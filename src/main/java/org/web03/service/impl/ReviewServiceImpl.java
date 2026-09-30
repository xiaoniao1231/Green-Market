package org.web03.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.web03.exception.BusinessException;
import org.web03.mapper.ProductReviewMapper;
import org.web03.mapper.ShopMapper;
import org.web03.pojo.Order.OrderItem;
import org.web03.pojo.Review.*;
import org.web03.pojo.Shop.Shop;
import org.web03.service.ReviewService;
import org.web03.utils.AliyunOSSOperator;
import org.web03.utils.CurrentHolder;
import org.web03.utils.JsonUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 评价晒单
 */

@Slf4j
@Service
public class ReviewServiceImpl implements ReviewService {

    @Autowired
    private ProductReviewMapper reviewMapper;
    @Autowired
    private ShopMapper shopMapper;
    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    // 日期时间格式化器
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern ("yyyy-MM-dd HH:mm:ss");

    // 待处理的评价
    @Override
    public ReviewPageVO pending(ReviewCheck check) {
        String userId = currentUser();
        check.setUserId(userId);
        normalizePage(check);

        // 赋值
        ReviewPageVO result = new ReviewPageVO();
        result.setTotal(reviewMapper.countPendingOrders(userId));
        result.setPage(check.getPage());
        result.setSize(check.getSize());

        // 获取订单列表，但是不包括订单条目
        List<PendingReviewOrder> orders = reviewMapper.listPendingOrders(userId, check.getOffset(), check.getSize());
        if (orders.isEmpty()) {
            result.setOrders(new ArrayList<>());
            return result;
        }

        // 获取orders中的订单id
        List<Integer> orderIds = new ArrayList<>();
        for (PendingReviewOrder o : orders) orderIds.add(o.getId());

        // 根据订单id填充订单展示图和订单是否已评论
        Map<Integer, List<PendingReviewItem>> grouped = new HashMap<>();
        for (PendingReviewItem item : reviewMapper.listItemsByOrders(orderIds)) {
            item.setArt(JsonUtils.buildArtFromImg(item.getArtImg()));
            grouped.computeIfAbsent(item.getOrderId(), k -> new ArrayList<>()).add(item);
        }
        for (PendingReviewOrder order : orders) {
            order.setItems(grouped.getOrDefault(order.getId(), new ArrayList<>()));
        }
        result.setOrders(orders);
        return result;

    }

    // 我的评价
    @Override
    public List<ReviewVO> mine() {
        String userId = currentUser();
        List<ReviewVO> list = new ArrayList<>();
        for (ProductReview row : reviewMapper.listMineAll(userId)) {
            list.add(toVO(row, true));
        }
        return list;
    }

    // 发表评价
    @Override
    @Transactional
    public ReviewVO create(ReviewCreateRequest request) {
        String userId = currentUser();
        if (request == null) throw new BusinessException("请填写评价信息");
        if (request.getOrderItemId() == null) throw new BusinessException("缺少订单条目信息");
        if (request.getScore() == null || request.getScore() < 1 || request.getScore() > 5) {
            throw new BusinessException("请选择 1-5 星评分");
        }
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.length() > 500) throw new BusinessException("评价内容最多 500 字");

        List<String> images = normalizeImages(request.getImages());
        if (content.isEmpty() && images.isEmpty()) {
            throw new BusinessException("请填写评价内容或上传晒单图");
        }

        //订单信息校验：条目存在，属于当前账号，所属订单已完成
        OrderItem item = reviewMapper.findOrderItemById(request.getOrderItemId(), userId);
        if (item == null) {
            String status = reviewMapper.findOrderStatusByItem(request.getOrderItemId(), userId);
            if (status == null) throw new BusinessException("订单条目不存在");
            if (!"done".equals(status)) throw new BusinessException("该订单尚未完成，暂不能评价");
            throw new BusinessException("订单条目不存在");
        }

        //校验重复评价
        if (reviewMapper.countByOrderItem(request.getOrderItemId()) > 0) {
            throw new BusinessException("该款式已评价，请勿重复评价");
        }

        //校验店铺是否存在
        String shopId = item.getShopId();
        if (!StringUtils.hasLength(shopId)) shopId = reviewMapper.findShopIdByProduct(item.getProductId());
        if (!StringUtils.hasLength(shopId)) throw new BusinessException("商品不存在");

        // 构建评价对象，并上传数据库
        ProductReview review = new ProductReview();
        review.setOrderId(item.getOrderId());
        review.setOrderItemId(item.getId());
        review.setProductId(item.getProductId());
        review.setShopId(shopId);
        review.setUserId(userId);
        review.setScore(request.getScore());
        review.setContent(content);
        review.setImages(images.isEmpty() ? null : JsonUtils.toJson(images));
        review.setAnonymous(Boolean.TRUE.equals(request.getAnonymous()) ? 1 : 0);
        review.setSku(StringUtils.hasLength(item.getSku()) ? item.getSku() : "默认");
        reviewMapper.insert(review);

        //重算商品评分和店铺评分
        recalc(review.getProductId(), shopId);
        log.info("{} 评价条目 {}（订单 {} / 商品 {} / 款式 {}）：{} 星",
                userId, item.getId(), item.getOrderId(), item.getProductId(), review.getSku(), review.getScore());

        return toVO(reviewMapper.findDetailById(review.getId()), true);

    }

    // 晒单图上传
    @Override
    public Map<String, Object> uploadImage(MultipartFile file) {
        currentUser();
        if (file == null || file.isEmpty()) throw new BusinessException("请选择要上传的图片");
        if (file.getSize() > 100 * 1024 * 1024) throw new BusinessException("单张图片不能超过 100MB");
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException("请上传图片文件");
        }
        try {
            String url = aliyunOSSOperator.upload(file.getBytes(), Objects.requireNonNull(file.getOriginalFilename()));
            Map<String, Object> data = new HashMap<>();
            data.put("url", url);
            return data;
        } catch (Exception e) {
            log.error("晒单图上传失败", e);
            throw new BusinessException("图片上传失败：" + e.getMessage());
        }
    }

    // 追评
    @Override
    @Transactional
    public ReviewVO append(Long reviewId, ProductReview request) {
        String userId = currentUser();
        String content = (request == null || request.getContent() == null) ? "" : request.getContent().trim();
        if (content.isEmpty()) throw new BusinessException("请填写追评内容");
        if (content.length() > 500) throw new BusinessException("追评最多 500 字");

        ProductReview exist = reviewMapper.findById(reviewId);
        if (exist == null || !userId.equals(exist.getUserId())) throw new BusinessException("评价不存在");
        if (StringUtils.hasLength(exist.getAppendContent())) throw new BusinessException("该评价已追评");

        int rows = reviewMapper.updateAppend(reviewId, userId, content);
        if (rows == 0) throw new BusinessException("该评价已追评");
        return toVO(reviewMapper.findDetailById(reviewId), true);
    }

    // 商家回复
    @Override
    @Transactional
    public ReviewVO reply(Long reviewId, ProductReview request) {
        String userId = currentUser();
        String content = (request == null || request.getContent() == null) ? "" : request.getContent().trim();
        if (content.isEmpty()) throw new BusinessException("请填写回复内容");
        if (content.length() > 500) throw new BusinessException("回复最多 500 字");

        ProductReview exist = reviewMapper.findById(reviewId);
        if (exist == null) throw new BusinessException("评价不存在");

        Shop shop = shopMapper.findById(exist.getShopId());
        if (shop == null || !userId.equals(shop.getOwnerUserId())) {
            throw new BusinessException("只有店主可以回复评价");
        }
        if (StringUtils.hasLength(exist.getReplyContent())) throw new BusinessException("该评价已回复");

        int rows = reviewMapper.updateReply(reviewId, exist.getShopId(), content);
        if (rows == 0) throw new BusinessException("该评价已回复");
        return toVO(reviewMapper.findDetailById(reviewId), false);
    }

    // 商品评价列表
    @Override
    public ReviewPageVO productReviews(ReviewCheck check) {
        if (check == null || check.getProductId() == null) throw new BusinessException("商品不存在");
        normalizePage(check);

        ReviewPageVO result = new ReviewPageVO();
        result.setTotal(reviewMapper.countByProduct(check));
        result.setPage(check.getPage());
        result.setSize(check.getSize());

        // 获取评价列表
        List<ReviewVO> list = new ArrayList<>();
        for (ProductReview row : reviewMapper.listByProduct(check)) {
            list.add(toVO(row, false));
        }
        result.setList(list);
        result.setSummary(summaryOf(check.getProductId()));
        return result;
    }

    // 店家收到的评价列表
    @Override
    public ReviewPageVO shopReviews(ReviewCheck check) {
        String userId = currentUser();
        Shop shop = shopMapper.findByOwnerUserId(userId);
        if (shop == null) throw new BusinessException("你还没有店铺");
        if (check == null) check = new ReviewCheck();
        check.setShopId(shop.getShopId());
        normalizePage(check);

        ReviewPageVO result = new ReviewPageVO();
        result.setTotal(reviewMapper.countByShop(check));
        result.setPage(check.getPage());
        result.setSize(check.getSize());

        List<ReviewVO> list = new ArrayList<>();
        for (ProductReview row : reviewMapper.listByShop(check)) {
            /* 店家视角也尊重匿名：买家选择匿名评价时，店家同样看不到真实昵称 ——
               只显示「匿名用户」。回复操作不依赖身份识别，靠 orderNo / 商品 / 款式定位即可。 */
            list.add(toVO(row, false));
        }
        result.setList(list);
        return result;
    }

    // 店家各商品的评价分组统计
    @Override
    public List<Map<String, Object>> shopGroups() {
        String userId = currentUser();
        Shop shop = shopMapper.findByOwnerUserId(userId);
        if (shop == null) throw new BusinessException("你还没有店铺");

        List<Map<String, Object>> rows = reviewMapper.listShopGroupStats(shop.getShopId());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("productId", row.get("productId"));
            g.put("title", row.get("productTitle"));
            g.put("art", JsonUtils.buildArt(row.get("productSkus") == null ? null : String.valueOf(row.get("productSkus"))));
            g.put("total", toInt(row.get("total")));
            g.put("replied", toInt(row.get("replied")));
            g.put("unreplied", toInt(row.get("unreplied")));
            g.put("withAppend", toInt(row.get("withAppend")));
            out.add(g);
        }
        return out;
    }

    // 将对象转换为整数，失败时返回 0。
    private int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }

    // 商品评分汇总
    @Override
    public ReviewSummaryVO productRating(Integer productId) {
        if (productId == null) throw new BusinessException("商品不存在");
        return summaryOf(productId);
    }


    //获取当前用户
    private String currentUser() {
        String userId = CurrentHolder.getCurrentUserId();
        if (!StringUtils.hasLength(userId)) throw new BusinessException("未登录或登录已失效");
        return userId;
    }

    //分页参数规整
    private void normalizePage(ReviewCheck check) {
        Integer p = check.getPage();
        Integer s = check.getSize();
        int page = (p == null || p < 1) ? 1 : p;
        int size = (s == null || s < 1) ? 10 : Math.min(s, 50);
        check.setPage(page);
        check.setSize(size);
        check.setOffset((page - 1) * size);
    }

    //晒图地址校验：去空、去重、验证格式、限制6张
    private List<String> normalizeImages(List<String> raw) {
        List<String> images = new ArrayList<>();
        if (raw == null) return images;
        for (String url : raw) {
            if (!StringUtils.hasLength(url)) continue;
            String u = url.trim();
            if (!u.startsWith("http://") && !u.startsWith("https://")) {
                throw new BusinessException("晒单图地址不合法");
            }
            images.add(u);
            if (images.size() > 6) throw new BusinessException("晒单图最多 6 张");
        }
        return images;
    }

    // 重算商品评分和店铺评分
    private void recalc(Integer productId, String shopId) {
        reviewMapper.recalcProductRating(productId);
        if (StringUtils.hasLength(shopId)) reviewMapper.recalcShopScore(shopId);
    }

    // 将数据库对象转换为前端评价对象。
    private ReviewVO toVO(ProductReview row, boolean showRealUser) {
        ReviewVO vo = new ReviewVO();
        vo.setId(row.getId());
        vo.setOrderId(row.getOrderId());
        vo.setOrderNo(row.getOrderNo());
        vo.setOrderItemId(row.getOrderItemId());
        vo.setProductId(row.getProductId());
        vo.setShopId(row.getShopId());
        vo.setScore(row.getScore());
        vo.setContent(row.getContent() == null ? "" : row.getContent());
        vo.setImages(JsonUtils.parseImages(row.getImages()));
        vo.setAnonymous(row.getAnonymous() != null && row.getAnonymous() == 1);
        vo.setSku(StringUtils.hasLength(row.getSku()) ? row.getSku() : "默认");

        //匿名时对外隐藏身份
        Map<String, Object> user = new HashMap<>();
        if (vo.getAnonymous() && !showRealUser) {
            user.put("userId", "");
            user.put("nickname", "匿名用户");
            user.put("avatar", "");
        } else {
            user.put("userId", row.getUserId());
            user.put("nickname", StringUtils.hasLength(row.getReviewerNickname()) ? row.getReviewerNickname() : row.getUserId());
            user.put("avatar", row.getReviewerAvatar() == null ? "" : row.getReviewerAvatar());
        }
        vo.setUser(user);

        //商品摘要
        if (row.getProductId() != null) {
            Map<String, Object> product = new HashMap<>();
            product.put("id", row.getProductId());
            product.put("title", row.getProductTitle() == null ? "" : row.getProductTitle());
            product.put("art", JsonUtils.buildArt(row.getProductSkus()));
            vo.setProduct(product);
        }

        Map<String, Object> shop = new HashMap<>();
        shop.put("shopId", row.getShopId());
        shop.put("name", row.getShopName() == null ? "" : row.getShopName());
        vo.setShop(shop);

        vo.setAppend(mapOf(row.getAppendContent(), row.getAppendTime()));
        vo.setReply(mapOf(row.getReplyContent(), row.getReplyTime()));
        vo.setCreatedAt(row.getCreatedAt() == null ? null : TIME_FORMATTER.format(row.getCreatedAt()));
        return vo;
    }

    // 追评和店家回复的统一结构
    private Map<String, Object> mapOf(String content, LocalDateTime time) {
        if (!StringUtils.hasLength(content)) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("content", content);
        map.put("time", time == null ? null : TIME_FORMATTER.format(time));
        return map;
    }

    // 评分汇总
    private ReviewSummaryVO summaryOf(Integer productId) {
        ReviewSummaryVO vo = new ReviewSummaryVO();
        vo.setProductId(productId);

        // 获取商品评分，评价数，好评率
        ReviewSummaryVO stat = reviewMapper.selectSummary(productId);
        vo.setRating(stat == null ? null : stat.getRating());
        vo.setReviewCount(stat == null || stat.getReviewCount() == null ? 0 : stat.getReviewCount());
        vo.setGoodRate(stat == null || stat.getGoodRate() == null ? 0 : stat.getGoodRate());

        //各评分分布
        Map<String, Integer> dist = new LinkedHashMap<>();
        for (int i = 5; i >= 1; i--) dist.put(String.valueOf(i), 0);
        for (Map<String, Object> d : reviewMapper.selectDistribution(productId)) {
            Object score = d.get("score");
            Object cnt = d.get("cnt");
            if (score == null || cnt == null) continue;
            dist.put(String.valueOf(((Number) score).intValue()), ((Number) cnt).intValue());
        }
        vo.setDistribution(dist);

        // 款式分布
        List<Map<String, Object>> skus = new ArrayList<>();
        for (Map<String, Object> s : reviewMapper.selectSkuDistribution(productId)) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("sku", s.get("sku") == null ? "默认" : String.valueOf(s.get("sku")));
            g.put("count", s.get("cnt") == null ? 0 : ((Number) s.get("cnt")).intValue());
            g.put("rating", toBigDecimal(s.get("rating")));
            skus.add(g);
        }
        vo.setSkus(skus);

        String shopId = reviewMapper.findShopIdByProduct(productId);
        if (StringUtils.hasLength(shopId)) vo.setShopScore(reviewMapper.findShopScore(shopId));
        return vo;
    }

    // 将对象转换为 BigDecimal
    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        return new BigDecimal(String.valueOf(v));
    }


}
