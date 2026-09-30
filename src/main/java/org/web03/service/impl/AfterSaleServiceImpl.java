package org.web03.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.web03.exception.BusinessException;
import org.web03.mapper.AfterSaleMapper;
import org.web03.mapper.EmpMapper;
import org.web03.mapper.OrderMapper;
import org.web03.mapper.ShopMapper;
import org.web03.pojo.AfterSale.AfterSale;
import org.web03.pojo.AfterSale.AfterSaleItem;
import org.web03.pojo.AfterSale.AfterSaleItemVO;
import org.web03.pojo.AfterSale.AfterSaleLog;
import org.web03.pojo.AfterSale.AfterSaleLogVO;
import org.web03.pojo.AfterSale.AfterSaleVO;
import org.web03.pojo.Order.Order;
import org.web03.pojo.Order.OrderItem;
import org.web03.pojo.Shop.Shop;
import org.web03.service.AfterSaleService;
import org.web03.utils.AliyunOSSOperator;
import org.web03.utils.CurrentHolder;
import org.web03.utils.JsonUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;


/**
 * 售后服务实现类
 */

@Slf4j
@Service
public class AfterSaleServiceImpl implements AfterSaleService {

    @Autowired
    private AfterSaleMapper afterSaleMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private ShopMapper shopMapper;
    @Autowired
    private EmpMapper empMapper;
    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    // 售后类型
    private static final String T_REFUND = "refund";       // 仅退款
    private static final String T_RETURN = "return";       // 退货退款
    private static final String T_EXCHANGE = "exchange";   // 换货
    private static final List<String> TYPES = Arrays.asList(T_REFUND, T_RETURN, T_EXCHANGE);

    // 售后状态
    private static final String S_PENDING = "pending";     // 待商家处理
    private static final String S_AGREED = "agreed";       // 待买家寄回
    private static final String S_RETURNED = "returned";   // 待商家收货
    private static final String S_REFUNDED = "refunded";   // 已退款
    private static final String S_EXCHANGED = "exchanged"; // 换货完成
    private static final String S_REFUSED = "refused";     // 商家已拒绝
    private static final String S_CANCELED = "canceled";   // 买家已撤销

    // 可申请售后的订单状态：待发货 / 待收货 / 已完成
    private static final List<String> APPLICABLE_ORDER_STATUS = Arrays.asList("paid", "shipped", "done");

    // 操作方与动作
    private static final String ROLE_BUYER = "buyer";     // 买家
    private static final String ROLE_SELLER = "seller";   // 商家
    private static final String A_APPLY = "apply";       // 申请
    private static final String A_APPROVE = "approve";   // 审批
    private static final String A_REFUSE = "refuse";     // 拒绝
    private static final String A_SHIP = "ship";         // 配送
    private static final String A_RECEIVE = "receive";   // 收到
    private static final String A_CANCEL = "cancel";     // 撤销

    // 时间格式化
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    // 查询所有售后单
    @Override
    public List<AfterSaleVO> listAll() {
        String userId = currentUser();
        return afterSaleMapper.listAllByUser(userId).stream().map(this::toVO).collect(Collectors.toList());
    }

    // 根据id查询售后单
    @Override
    public AfterSaleVO get(Integer id) {
        return toVO(requireOwnAfterSale(id));
    }

    // 创建售后单
    @Override
    @Transactional
    public AfterSaleVO create(AfterSaleVO request) {
        String userId = currentUser();
        if (request == null || request.getOrderId() == null) throw new BusinessException("订单不存在");

        Order order = orderMapper.findByIdAndUser(request.getOrderId(), userId);
        if (order == null) throw new BusinessException("订单不存在");
        if (!APPLICABLE_ORDER_STATUS.contains(order.getStatus())) throw new BusinessException("当前订单状态不支持申请售后");

        // 参数校验
        String type = normalizeType(request.getType());
        String reason = JsonUtils.trimToNull(request.getReason());
        if (reason == null) throw new BusinessException("请选择售后原因");
        if (reason.length() > 100) throw new BusinessException("售后原因最多 100 字");
        String description = request.getDescription() == null ? "" : request.getDescription().trim();
        if (description.length() > 2000) throw new BusinessException("补充说明最多 2000 字");
        List<String> images = JsonUtils.normalizeImages(request.getImages());

        // 商品明细
        List<OrderItem> orderItems = orderMapper.listItems(order.getId());
        if (orderItems == null || orderItems.isEmpty()) throw new BusinessException("订单里没有可申请售后的商品");
        List<AfterSaleItemVO> picked = request.getItems();
        boolean all = picked == null || picked.isEmpty();
        List<AfterSaleItem> details = new ArrayList<>();
        String shopId = null;
        BigDecimal refundAmount = BigDecimal.ZERO;
        int totalQty = 0;
        for (OrderItem oi : orderItems) {
            int maxQty = oi.getQty() == null || oi.getQty() < 1 ? 1 : oi.getQty();
            int qty = maxQty;
            if (!all) {
                AfterSaleItemVO hit = pickItem(picked, oi);
                if (hit == null) continue;
                qty = hit.getQty() == null ? maxQty : hit.getQty();
                if (qty < 1 || qty > maxQty) throw new BusinessException("售后件数必须在 1-" + maxQty + " 之间");
            }
            BigDecimal price = oi.getPrice() == null ? BigDecimal.ZERO : oi.getPrice();
            BigDecimal itemRefund = T_EXCHANGE.equals(type) ? BigDecimal.ZERO : price.multiply(BigDecimal.valueOf(qty));
            AfterSaleItem row = new AfterSaleItem();
            row.setOrderItemId(oi.getId());
            row.setProductId(oi.getProductId());
            row.setTitle(oi.getTitle());
            row.setSku(StringUtils.hasLength(oi.getSku()) ? oi.getSku() : "默认");
            row.setArtImg(oi.getArtImg());
            row.setQty(qty);
            row.setPrice(price);
            row.setRefundAmount(itemRefund);
            details.add(row);
            totalQty += qty;
            refundAmount = refundAmount.add(itemRefund);
            if (shopId == null && StringUtils.hasLength(oi.getShopId())) shopId = oi.getShopId();
        }
        if (details.isEmpty()) throw new BusinessException("请至少选择一件要售后的商品");
        if (!StringUtils.hasLength(shopId)) throw new BusinessException("订单缺少店铺信息，无法申请售后");

        // 首件商品写入主表快照
        AfterSaleItem first = details.get(0);

        // 一个订单只能有一条售后单：进行中 / 已完成不能重复申请；撤销或被拒可复用原行重申请
        AfterSale exist = afterSaleMapper.findByOrderId(order.getId());
        if (exist != null) {
            String st = exist.getStatus();
            if (S_PENDING.equals(st) || S_AGREED.equals(st) || S_RETURNED.equals(st)) {
                throw new BusinessException("该订单已有进行中的售后，请先撤销后再申请");
            }
            if (S_REFUNDED.equals(st) || S_EXCHANGED.equals(st)) {
                throw new BusinessException("该订单售后已完成，不能重复申请");
            }
            // canceled / refused：复用原行重新申请（唯一键决定了不能插第二条）
            AfterSale reset = new AfterSale();
            reset.setId(exist.getId());
            reset.setOrderItemId(first.getOrderItemId());
            reset.setProductId(first.getProductId());
            reset.setTitle(first.getTitle());
            reset.setSku(first.getSku());
            reset.setArtImg(first.getArtImg());
            reset.setQty(totalQty);
            reset.setPrice(first.getPrice());
            reset.setRefundAmount(refundAmount);
            reset.setType(type);
            reset.setReason(reason);
            reset.setDescription(description);
            reset.setImages(JsonUtils.toJson(images));
            if (afterSaleMapper.resetForReapply(reset) == 0) {
                throw new BusinessException("该订单已有进行中的售后，请先撤销后再申请");
            }
            afterSaleMapper.deleteItems(exist.getId());
            writeItems(exist.getId(), details);
            appendLog(exist.getId(), ROLE_BUYER, A_APPLY, userId, description.isEmpty() ? reason : description);
            log.info("售后重新申请: 买家 {} 订单 {} 商品 {} 件（复用售后单 {}）",
                    userId, order.getOrderNo(), details.size(), exist.getAfterNo());
            return toVO(afterSaleMapper.findById(exist.getId()));
        }

        // 新建售后单：店铺 / 商品 / 金额全部取订单与条目快照，件数与金额按明细汇总
        AfterSale as = new AfterSale();
        as.setAfterNo(genAfterNo());
        as.setOrderId(order.getId());
        as.setOrderNo(order.getOrderNo());
        as.setOrderItemId(first.getOrderItemId());
        as.setUserId(userId);
        as.setShopId(shopId);
        as.setProductId(first.getProductId());
        as.setTitle(first.getTitle());
        as.setSku(first.getSku());
        as.setArtImg(first.getArtImg());
        as.setQty(totalQty);
        as.setPrice(first.getPrice());
        as.setRefundAmount(refundAmount);
        as.setType(type);
        as.setReason(reason);
        as.setDescription(description);
        as.setImages(JsonUtils.toJson(images));
        as.setStatus(S_PENDING);
        if (afterSaleMapper.insert(as) == 0 || as.getId() == null) {
            throw new BusinessException("该订单已有进行中的售后，请先撤销后再申请");
        }
        writeItems(as.getId(), details);
        appendLog(as.getId(), ROLE_BUYER, A_APPLY, userId, description.isEmpty() ? reason : description);
        log.info("售后申请: 买家 {} 订单 {} 商品 {} 件 类型 {} 金额 {}",
                userId, order.getOrderNo(), details.size(), type, refundAmount);
        return toVO(afterSaleMapper.findById(as.getId()));
    }

    // 上传图片
    @Override
    public String uploadImage(MultipartFile file) {
        currentUser();   // 未登录直接抛「未登录或登录已失效」
        if (file == null || file.isEmpty()) throw new BusinessException("请选择要上传的图片");
        if (file.getSize() > 100 * 1024 * 1024) throw new BusinessException("文件大小不能超过 100MB");
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException("请上传图片文件");
        }
        try {
            return aliyunOSSOperator.upload(file.getBytes(), Objects.requireNonNull(file.getOriginalFilename()));
        } catch (Exception e) {
            throw new BusinessException("图片上传失败：" + e.getMessage());
        }
    }

    // 撤销售后单
    @Override
    public void cancel(Integer id) {
        String userId = currentUser();

        AfterSale as = requireOwnAfterSale(id);
        if (changeStatus(as.getId(), userId, S_PENDING, S_CANCELED, true) == 0) {
            throw new BusinessException("当前状态不可撤销");
        }
        appendLog(id, ROLE_BUYER, A_CANCEL, userId, "买家撤销了售后申请");
        log.info("售后撤销: 买家 {} 售后单 {}", userId, as.getAfterNo());
    }

    // 配送售后单
    @Override
    @Transactional
    public void ship(Integer id, AfterSale request) {
        String userId = currentUser();
        AfterSale as = requireOwnAfterSale(id);
        if (T_REFUND.equals(as.getType())) throw new BusinessException("仅退款无需寄回商品");
        String company = pick(JsonUtils.trimToNull(request == null ? null : request.getCompany()),
                JsonUtils.trimToNull(request == null ? null : request.getBuyerCompany()));
        String trackingNo = pick(JsonUtils.trimToNull(request == null ? null : request.getTrackingNo()),
                JsonUtils.trimToNull(request == null ? null : request.getBuyerTrackingNo()));
        if (company == null) throw new BusinessException("请填写快递公司");
        if (company.length() > 50) throw new BusinessException("快递公司最多 50 字");
        if (trackingNo == null) throw new BusinessException("请填写正确的运单号");
        if (trackingNo.length() > 50 || !trackingNo.matches("[A-Za-z0-9-]+")) {
            throw new BusinessException("请填写正确的运单号");
        }
        if (changeStatus(as.getId(), userId, S_AGREED, S_RETURNED, false) == 0) {
            throw new BusinessException("当前状态不可填写寄回物流");
        }
        afterSaleMapper.updateShipInfo(as.getId(), company, trackingNo);
        appendLog(id, ROLE_BUYER, A_SHIP, userId, company + " " + trackingNo);
        log.info("买家寄回: 买家 {} 售后单 {} 运单 {}", userId, as.getAfterNo(), trackingNo);
    }

    // 商家查询售后单
    @Override
    public Map<String, Object> sellerList(String status, String afterNo, Integer page, Integer size) {
        String shopId = requireShopId();
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null ? 20 : Math.min(Math.max(size, 1), 100);
        String st = normalizeStatus(status);
        String no = StringUtils.hasLength(afterNo) ? afterNo.trim() : null;
        List<AfterSaleVO> list = afterSaleMapper.listByShop(shopId, st, no, (p - 1) * s, s)
                .stream().map(this::toVO).collect(Collectors.toList());
        long total = afterSaleMapper.countByShop(shopId, st, no);
        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("page", p);
        data.put("size", s);
        data.put("list", list);
        return data;
    }

    // 商家审批售后单
    @Override
    @Transactional
    public void approve(Integer id, AfterSale request) {
        String shopId = requireShopId();
        String actorId = currentUser();
        AfterSale as = requireShopAfterSale(id, shopId);

        String remark = pick(JsonUtils.trimToNull(request == null ? null : request.getRemark()),
                JsonUtils.trimToNull(request == null ? null : request.getSellerRemark()));
        if (remark != null && remark.length() > 1000) {
            throw new BusinessException("处理备注最多 1000 字");
        }
        String returnAddress = JsonUtils.trimToNull(request == null ? null : request.getReturnAddress());
        boolean onlyRefund = T_REFUND.equals(as.getType());
        if (!onlyRefund) {
            if (returnAddress == null) throw new BusinessException("请填写退货寄回地址");
            if (returnAddress.length() > 200) {
                throw new BusinessException("寄回地址最多 200 字");
            }
        }
        String to = onlyRefund ? S_REFUNDED : S_AGREED;
        if (changeStatus(as.getId(), null, S_PENDING, to, onlyRefund) == 0) {
            throw new BusinessException("当前状态不可同意");
        }
        //寄回地址与备注落库
        afterSaleMapper.updateApproveInfo(as.getId(), returnAddress, remark);
        appendLog(id, ROLE_SELLER, A_APPROVE, actorId,
                remark != null ? remark : (onlyRefund ? "商家同意退款" : "商家同意退货，请按地址寄回"));
        log.info("售后同意: 店铺 {} 售后单 {} → {}", shopId, as.getAfterNo(), to);
    }

    // 商家拒绝售后单
    @Override
    @Transactional
    public void refuse(Integer id, AfterSale request) {
        String shopId = requireShopId();
        String actorId = currentUser();
        AfterSale as = requireShopAfterSale(id, shopId);

        String reason = JsonUtils.trimToNull(request == null ? null : request.getReason());
        if (reason == null) throw new BusinessException("请填写拒绝原因");
        if (reason.length() > 2000) {
            throw new BusinessException("拒绝原因最多 2000 字");
        }
        if (changeStatus(as.getId(), null, S_PENDING, S_REFUSED, true) == 0) {
            throw new BusinessException("当前状态不可拒绝");
        }
        /* 拒绝原因必须落库：after_sales.refuse_reason 是买家与店家页面「拒绝原因」一栏的数据源，
           只写进日志会导致两边页面都显示不出原因。 */
        afterSaleMapper.updateRefuseReason(as.getId(), reason);
        appendLog(id, ROLE_SELLER, A_REFUSE, actorId, reason);
        log.info("售后拒绝: 店铺 {} 售后单 {} 原因 {}", shopId, as.getAfterNo(), reason);

    }

    // 商家收到售后单
    @Override
    @Transactional
    public void receive(Integer id, AfterSale request) {
        String shopId = requireShopId();
        String actorId = currentUser();
        AfterSale as = requireShopAfterSale(id, shopId);

        String remark = pick(JsonUtils.trimToNull(request == null ? null : request.getRemark()),
                JsonUtils.trimToNull(request == null ? null : request.getSellerRemark()));
        if (remark != null && remark.length() > 1000) {
            throw new BusinessException("处理备注最多 1000 字");
        }
        boolean exchange = T_EXCHANGE.equals(as.getType());
        String reshipCompany = null;
        String reshipNo = null;
        if (exchange) {
            reshipCompany = JsonUtils.trimToNull(request == null ? null : request.getReshipCompany());
            reshipNo = JsonUtils.trimToNull(request == null ? null : request.getReshipNo());
            if (reshipCompany == null) throw new BusinessException("请填写换货重发的快递公司");
            if (reshipCompany.length() > 50) {
                throw new BusinessException("快递公司最多 50 字");
            }
            if (reshipNo == null || reshipNo.length() > 50 || !reshipNo.matches("[A-Za-z0-9-]+")) {
                throw new BusinessException("请填写换货重发的运单号");
            }
        }
        String to = exchange ? S_EXCHANGED : S_REFUNDED;
        if (changeStatus(as.getId(), null, S_RETURNED, to, true) == 0) {
            throw new BusinessException("当前状态不可确认收货");
        }
        //换货重发单号与备注落库
        afterSaleMapper.updateReceiveInfo(as.getId(), exchange, reshipCompany, reshipNo, remark);
        appendLog(id, ROLE_SELLER, A_RECEIVE, actorId,
                exchange ? ("已收到退货，换货已发出：" + reshipCompany + " " + reshipNo)
                        : (remark != null ? remark : "已收到退货，退款已办结"));
        log.info("售后确认收货: 店铺 {} 售后单 {} → {}", shopId, as.getAfterNo(), to);
    }


    //请求体字段取值：优先前端 / 接口文档口径的字段名，实体列名的历史字段名兜底
    private String pick(String primary, String fallback) {
        return primary != null ? primary : fallback;
    }

    //当前登录账号
    private String currentUser() {        String userId = CurrentHolder.getCurrentUserId();
        if (!StringUtils.hasLength(userId)) throw new BusinessException("未登录或登录已失效");
        return userId;
    }

    //当前账号的店铺ID
    private String requireShopId() {
        String shopId = shopMapper.findShopIdByOwner(currentUser());
        if (!StringUtils.hasLength(shopId)) throw new BusinessException("当前账号未开店");
        return shopId;
    }

    //买家侧：售后单必须存在且属于当前账号
    private AfterSale requireOwnAfterSale(Integer id) {
        if (id == null) throw new BusinessException("售后单不存在");
        AfterSale as = afterSaleMapper.findByIdAndUser(id, currentUser());
        if (as == null) throw new BusinessException("售后单不存在");
        return as;
    }

    //卖家侧：售后单必须存在且属于当前账号的店铺
    private AfterSale requireShopAfterSale(Integer id, String shopId) {
        if (id == null) throw new BusinessException("售后单不存在或不属于本店铺");
        AfterSale as = afterSaleMapper.findByIdAndShop(id, shopId);
        if (as == null) throw new BusinessException("售后单不存在或不属于本店铺");
        return as;
    }

    //数据库行 → 接口对象
    private AfterSaleVO toVO(AfterSale as) {
        if (as == null) return null;
        AfterSaleVO vo = new AfterSaleVO();
        vo.setId(as.getId());
        vo.setAfterNo(as.getAfterNo());
        vo.setOrderId(as.getOrderId());
        vo.setOrderNo(as.getOrderNo());
        vo.setItemId(as.getOrderItemId());
        vo.setProductId(as.getProductId());
        vo.setTitle(as.getTitle());
        vo.setSku(as.getSku());
        vo.setArt(JsonUtils.buildArtFromImg(as.getArtImg()));
        vo.setQty(as.getQty());
        vo.setPrice(as.getPrice());
        vo.setRefundAmount(as.getRefundAmount());
        vo.setType(as.getType());
        vo.setReason(as.getReason());
        vo.setDescription(as.getDescription());
        vo.setImages(JsonUtils.parseImages(as.getImages()));
        vo.setStatus(as.getStatus());
        vo.setReturnAddress(as.getReturnAddress());
        vo.setRefuseReason(as.getRefuseReason());
        vo.setSellerRemark(as.getSellerRemark());

        //商品明细：一条售后单可含订单里的多件商品（主表字段只是首件快照 + 汇总）
        List<AfterSaleItemVO> items = afterSaleMapper.listItems(as.getId()).stream().map(r -> {
            AfterSaleItemVO iv = new AfterSaleItemVO();
            iv.setId(r.getId());
            iv.setItemId(r.getOrderItemId());
            iv.setProductId(r.getProductId());
            iv.setTitle(r.getTitle());
            iv.setSku(r.getSku());
            iv.setArt(JsonUtils.buildArtFromImg(r.getArtImg()));
            iv.setQty(r.getQty());
            iv.setPrice(r.getPrice());
            iv.setRefundAmount(r.getRefundAmount());
            return iv;
        }).collect(Collectors.toList());
        vo.setItems(items);
        if (!items.isEmpty()) {
            AfterSaleItemVO f = items.get(0);
            vo.setItemId(f.getItemId());
            vo.setProductId(f.getProductId());
            vo.setTitle(f.getTitle());
            vo.setSku(f.getSku());
            vo.setArt(f.getArt());
            vo.setPrice(f.getPrice());
        }

        //店铺摘要：店铺被删时也要能展示
        Map<String, Object> shop = new HashMap<>();
        shop.put("shopId", as.getShopId());
        Shop s = StringUtils.hasLength(as.getShopId()) ? shopMapper.findById(as.getShopId()) : null;
        shop.put("name", s == null || !StringUtils.hasLength(s.getName()) ? "" : s.getName());
        vo.setShop(shop);

        //买家摘要：卖家端要看到「谁申请的」
        Map<String, Object> buyer = new HashMap<>();
        buyer.put("userId", as.getUserId());
        String nickname = StringUtils.hasLength(as.getUserId()) ? empMapper.findNicknameByUserId(as.getUserId()) : null;
        buyer.put("nickname", StringUtils.hasLength(nickname) ? nickname : as.getUserId());
        vo.setBuyer(buyer);

        if (StringUtils.hasLength(as.getBuyerTrackingNo())) {
            Map<String, Object> express = new HashMap<>();
            express.put("company", as.getBuyerCompany());
            express.put("trackingNo", as.getBuyerTrackingNo());
            express.put("time", as.getBuyerShipTime() != null ? TIME_FORMATTER.format(as.getBuyerShipTime()) : "");
            vo.setExpress(express);
        }
        if (StringUtils.hasLength(as.getReshipNo())) {
            Map<String, Object> reship = new HashMap<>();
            reship.put("company", as.getReshipCompany());
            reship.put("trackingNo", as.getReshipNo());
            reship.put("time", as.getReshipTime() != null ? TIME_FORMATTER.format(as.getReshipTime()) : "");
            vo.setReship(reship);
        }

        //时间线：最早在前（读起来像对话记录）
        vo.setLogs(afterSaleMapper.listLogs(as.getId()).stream().map(l -> {
            AfterSaleLogVO lv = new AfterSaleLogVO();
            lv.setRole(l.getRole());
            lv.setAction(l.getAction());
            lv.setContent(l.getContent());
            lv.setTime(l.getCreatedAt() != null ? TIME_FORMATTER.format(l.getCreatedAt()) : "");
            return lv;
        }).collect(Collectors.toList()));

        vo.setCreatedAt(as.getCreatedAt() != null ? TIME_FORMATTER.format(as.getCreatedAt()) : "");
        vo.setUpdatedAt(as.getUpdatedAt() != null ? TIME_FORMATTER.format(as.getUpdatedAt()) : "");
        vo.setFinishTime(as.getFinishTime() != null ? TIME_FORMATTER.format(as.getFinishTime()) : "");
        return vo;
    }

    //明细勾选命中：按「商品 + 款式」匹配订单条目（前端不依赖订单条目 id）
    private AfterSaleItemVO pickItem(List<AfterSaleItemVO> picked, OrderItem oi) {
        if (oi == null || oi.getProductId() == null) return null;
        String sku = StringUtils.hasLength(oi.getSku()) ? oi.getSku().trim() : "默认";
        for (AfterSaleItemVO p : picked) {
            if (p == null || p.getProductId() == null) continue;
            if (!Objects.equals(p.getProductId(), oi.getProductId())) continue;
            String pSku = StringUtils.hasLength(p.getSku()) ? p.getSku().trim() : "默认";
            if (pSku.equals(sku)) return p;
        }
        return null;
    }

    //批量写入商品明细
    private void writeItems(Integer afterSaleId, List<AfterSaleItem> details) {
        details.forEach(d -> d.setAfterSaleId(afterSaleId));
        if (afterSaleMapper.insertItems(details) != details.size()) {
            throw new BusinessException("售后商品明细写入失败，请稍后重试");
        }
    }

    //状态参数归一化
    private String normalizeStatus(String status) {
        if (!StringUtils.hasLength(status)) return null;
        String st = status.trim().toLowerCase();
        List<String> all = Arrays.asList(S_PENDING, S_AGREED, S_RETURNED, S_REFUNDED, S_EXCHANGED, S_REFUSED, S_CANCELED);
        return all.contains(st) ? st : null;
    }

    //售后类型枚举校验
    private String normalizeType(String type) {
        String t = type == null ? null : type.trim().toLowerCase();
        if (t == null || !TYPES.contains(t)) throw new BusinessException("售后类型不合法");
        return t;
    }

    //写一条售后单修改状态记录
    private void appendLog(Integer afterSaleId, String role, String action, String actorId, String content) {
        AfterSaleLog logRow = new AfterSaleLog();
        logRow.setAfterSaleId(afterSaleId);
        logRow.setRole(role);
        logRow.setAction(action);
        logRow.setActorId(actorId == null ? "" : actorId);
        logRow.setContent(content == null ? "" : content);
        afterSaleMapper.insertLog(logRow);
    }
    //售后单号：AS + yyyyMMdd + 6 位随机大写字母数字
    public static String genAfterNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String rand = new Random().ints(0, 36).limit(6)
                .mapToObj(i -> Character.toString("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt(i)))
                .collect(Collectors.joining());
        return "AS" + date + rand;
    }

    //修改售后单状态（from 传当前状态，匹配不到说明状态已变，返回 0）
    private int changeStatus(Integer id, String userId, String from, String to, boolean finish) {
        return afterSaleMapper.changeStatus(id, userId, from, to, finish);
    }
}
