package org.web03.service.impl;


import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.web03.exception.BusinessException;
import org.web03.mapper.ProductMapper;
import org.web03.mapper.ShopMapper;
import org.web03.pojo.Product.ProductsCheck;
import org.web03.pojo.Product.Product;
import org.web03.pojo.Product.ProductRequest;
import org.web03.pojo.Product.ProductVO;
import org.web03.service.ProductService;
import org.web03.utils.AliyunOSSOperator;
import org.web03.utils.CurrentHolder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;


/**
 * 商品服务实现
 */
@Slf4j
@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ShopMapper shopMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    private static final ObjectMapper OM = new ObjectMapper();

    /** 款式价上限（与商品售价一致：1-99999 元） */
    private static final BigDecimal SKU_PRICE_MAX = new BigDecimal("99999");

    //商品列表
    @Override
    public ProductsCheck sellerList(ProductsCheck productsCheck) {
        productsCheck.setShopId(requireShopId());
        Integer page = productsCheck.getPage();
        Integer size = productsCheck.getSize();
        productsCheck.setOffset((page - 1) * size);

        List<ProductVO> list = productMapper.sellerList(productsCheck)
                .stream().map(this::toVO).collect(Collectors.toList());

        long total = productMapper.sellerCount(productsCheck);

        return new ProductsCheck(total, page, size, list);

    }

    //公开商品列表（买家端：不要求登录/开店，只返回在售商品）
    @Override
    public ProductsCheck publicList(ProductsCheck productsCheck) {
        Integer page = productsCheck.getPage();
        Integer size = productsCheck.getSize();
        productsCheck.setOffset((page - 1) * size);

        List<ProductVO> list = productMapper.publicList(productsCheck)
                .stream().map(this::toVO).collect(Collectors.toList());

        long total = productMapper.publicCount(productsCheck);

        return new ProductsCheck(total, page, size, list);
    }

    //创建商品
    @Override
    public ProductVO create(ProductRequest productRequest) {
        if(productRequest == null) throw new BusinessException("请填写商品信息");
        if(!StringUtils.hasLength(productRequest.getTitle())) throw new BusinessException("请填写商品标题");
        if (productRequest.getPrice() == null || productRequest.getPrice().compareTo(BigDecimal.ONE) < 0) {
            throw new BusinessException("请输入有效售价");
        }
        String shopId = requireShopId();

        Product p = new Product();
        p.setShopId(shopId);
        p.setTitle(productRequest.getTitle().trim());
        p.setPrice(productRequest.getPrice());
        p.setOriginalPrice(normalizeOriginal(productRequest.getOriginal()));
        p.setSales(0);
        p.setStock(productRequest.getStock() == null ? 0 : Math.max(0, productRequest.getStock()));
        p.setCategory(productRequest.getCategory());
        p.setSub(productRequest.getSub());
        p.setTag(productRequest.getTag());
        p.setSkus(toJson(normalizeSkus(productRequest.getSkus())));
        p.setParams(toJson(productRequest.getParams()));
        p.setDetail(toJson(productRequest.getDetail()));
        p.setDescription(resolveDesc(productRequest.getDesc(), productRequest.getDetail()));
        p.setOnSale(1);
        p.setDeleted(0);

        productMapper.insert(p);
        return toVO(productMapper.getById(p.getId()));
    }


    //删除商品
    @Override
    public Map<String, Object> delete(Integer id) {
        String shopId = requireShopId();
        int rows = productMapper.softDelete(id, shopId);
        if (rows == 0) throw new BusinessException("商品不存在");
        return Collections.singletonMap("deleted", true);
    }

    //更新商品
    @Override
    public ProductVO update(Integer id, ProductRequest req) {
        if (req == null) throw new BusinessException("请填写要修改的内容");
        String shopId = requireShopId();
        Product exist = productMapper.getByIdAndShop(id, shopId);
        if (exist == null) throw new BusinessException("商品不存在");

        if (req.getTitle() != null) {
            if (!StringUtils.hasLength(req.getTitle())) throw new BusinessException("商品标题不能为空");
            exist.setTitle(req.getTitle().trim());
        }
        if (req.getPrice() != null) {
            if (req.getPrice().compareTo(BigDecimal.ONE) < 0) throw new BusinessException("请输入有效售价");
            exist.setPrice(req.getPrice());
        }
        if (req.getOriginal() != null) exist.setOriginalPrice(normalizeOriginal(req.getOriginal()));
        if (req.getStock() != null) exist.setStock(Math.max(0, req.getStock()));
        if (req.getCategory() != null) exist.setCategory(req.getCategory());
        if (req.getSub() != null) exist.setSub(req.getSub());
        if (req.getTag() != null) exist.setTag(req.getTag());
        if (req.getSkus() != null) exist.setSkus(toJson(normalizeSkus(req.getSkus())));
        if (req.getParams() != null) exist.setParams(toJson(req.getParams()));
        if (req.getDetail() != null) exist.setDetail(toJson(req.getDetail()));
        if (req.getDesc() != null) exist.setDescription(req.getDesc());

        productMapper.update(exist);
        return toVO(productMapper.getByIdAndShop(id, shopId));
    }


    //更新商品上架/下架状态
    @Override
    public Map<String, Object> setStatus(Integer id, Boolean onSale) {
        String shopId = requireShopId();
        int rows = productMapper.updateStatus(id, shopId, Boolean.TRUE.equals(onSale) ? 1 : 0);
        if (rows == 0) throw new BusinessException("商品不存在");
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("onSale", onSale);
        return data;
    }

    //获取商品详情（用于编辑商品时回填表单）
    @Override
    public ProductVO getForEdit(Integer id) {
        String shopId = requireShopId();
        Product p = productMapper.getByIdAndShop(id, shopId);
        if (p == null) throw new BusinessException("商品不存在");
        return toVO(p);
    }

    //获取商品详情（用于商品详情页面；下架 / 已删除商品对买家不可见）
    @Override
    public ProductVO getDetail(Integer id) {
        Product p = productMapper.getPublicById(id);
        if(p == null)throw new BusinessException("商品不存在");
        return toVO(p);
    }

    //相关推荐：同分类优先，再用其他在售商品补足；排除当前商品
    @Override
    public List<ProductVO> related(Integer id, Integer size) {
        Product self = productMapper.getPublicById(id);
        if (self == null) throw new BusinessException("商品不存在");
        int limit = (size == null || size < 1) ? 5 : Math.min(size, 20);
        return productMapper.related(id, self.getCategory(), limit)
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    //限时秒杀
    @Override
    public Map<String, Object> flash() {
        Map<String, Object> data = new HashMap<>();
        data.put("endTime", System.currentTimeMillis() + 2 * 60 * 60 * 1000L); // 当前 + 2 小时
        data.put("list", productMapper.flashList(6).stream().map(this::toVO).collect(Collectors.toList()));
        return data;
    }

    //推荐商品
    @Override
    public ProductsCheck recommend(ProductsCheck productsCheck) {
        productsCheck.setOffset((productsCheck.getPage() - 1) * productsCheck.getSize());

        List<ProductVO> list = productMapper.recommendList(productsCheck)
                .stream().map(this::toVO).collect(Collectors.toList());
        long total = productMapper.recommendCount();

        return new ProductsCheck(total, productsCheck.getPage(), productsCheck.getSize(), list);
    }

    //搜索商品
    @Override
    public ProductsCheck search(ProductsCheck productsCheck) {
        if (!StringUtils.hasLength(productsCheck.getQ())) {
            return emptyPage(productsCheck.getPage(), productsCheck.getSize());
        }
        productsCheck.setOffset((productsCheck.getPage() - 1) * productsCheck.getSize());

        List<ProductVO> list = productMapper.search(productsCheck)
                .stream().map(this::toVO).collect(Collectors.toList());
        long total = productMapper.searchCount(productsCheck.getQ().trim());
        return new ProductsCheck(total, productsCheck.getPage(), productsCheck.getSize(), list);
    }

    //上传图片
    @Override
    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("请选择要上传的图片");
        if (file.getSize() > 10 * 1024 * 1024) throw new BusinessException("文件大小不能超过 10MB");
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

    //从登录态账号取本店 id；未登录抛“未登录”，已登录但未开店才抛“当前账号未开店”
    private String requireShopId() {
        String userId = CurrentHolder.getCurrentUserId();
        if (!StringUtils.hasLength(userId)) {
            throw new BusinessException("未登录或登录已失效");
        }
        String shopId = shopMapper.findShopIdByOwner(userId);
        if (!StringUtils.hasLength(shopId)) {
            throw new BusinessException("当前账号未开店");
        }
        return shopId;
    }
    //Product（DB 行）→ ProductVO（前端契约对象）
    private ProductVO toVO(Product p) {
        ProductVO vo = new ProductVO();
        vo.setId(p.getId());
        vo.setTitle(p.getTitle());
        vo.setPrice(p.getPrice());
        vo.setOriginal(p.getOriginalPrice());
        vo.setSales(p.getSales());
        vo.setStock(p.getStock());
        vo.setCategory(p.getCategory());
        vo.setSub(p.getSub());
        vo.setTag(p.getTag());

        // skus：主图由「第一个带图的款式值」推导（前端已删除独立主图上传，口径见 SellerProductsView.collect）
        List<Map<String, Object>> skus = parseList(p.getSkus());
        vo.setSkus(skus);

        // art：取第一个带图的 SKU 款式图；无图返回渐变占位
        Map<String, Object> art = new HashMap<>();
        String firstImg = firstSkuImg(skus);
        if (StringUtils.hasLength(firstImg)) {
            art.put("img", firstImg);
        } else {
            art.put("e", "🛍️");
            art.put("g", Arrays.asList("#ffe4d3", "#ffb88c"));
        }
        vo.setArt(art);

        // shop：JOIN 结果
        Map<String, Object> shop = new HashMap<>();
        shop.put("name", p.getShopName());
        shop.put("score", p.getShopScore());
        vo.setShop(shop);
        /* 款式价 → 价格区间：默认价与所有款式价一起取 min / max。
           卡片列表用区间（如「¥299 - ¥399」），详情页按选中款式取具体价。 */
        BigDecimal min = p.getPrice();
        BigDecimal max = p.getPrice();
        for (Map<String, Object> g : skus) {
            Object valuesObj = g.get("values");
            if (!(valuesObj instanceof List)) continue;
            for (Object item : (List<?>) valuesObj) {
                if (!(item instanceof Map)) continue;
                Object priceObj = ((Map<?, ?>) item).get("price");
                if (priceObj == null) continue;
                BigDecimal sp;
                try {
                    sp = new BigDecimal(String.valueOf(priceObj).trim());
                } catch (NumberFormatException e) {
                    continue;   // 脏数据（非数字）忽略，不影响列表展示
                }
                if (min == null || sp.compareTo(min) < 0) min = sp;
                if (max == null || sp.compareTo(max) > 0) max = sp;
            }
        }
        vo.setPriceMin(min);
        vo.setPriceMax(max);
        vo.setParams(parseParams(p.getParams()));
        vo.setDetail(parseList(p.getDetail()));
        vo.setDesc(p.getDescription());
        vo.setOnSale(p.getOnSale() != null && p.getOnSale() == 1);
        return vo;
    }

    //JSON 字符串 → List<Map>，异常则返回空列表
    private List<Map<String, Object>> parseList(String json) {
        if (!StringUtils.hasLength(json)) return new ArrayList<>();
        try {
            return OM.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("json解析失败，json:{}", json, e);
            return new ArrayList<>();
        }
    }

    //主图：取第一个带 img 的 SKU 款式值（与前端 collect() 推导一致）；无图返回 null
    private String firstSkuImg(List<Map<String, Object>> skus) {
        if (skus == null) return null;
        for (Map<String, Object> g : skus) {
            Object valuesObj = g.get("values");
            if (!(valuesObj instanceof List)) continue;
            for (Object item : (List<?>) valuesObj) {
                if (!(item instanceof Map)) continue;
                Object img = ((Map<?, ?>) item).get("img");
                if (img != null && StringUtils.hasLength(String.valueOf(img))) {
                    return String.valueOf(img);
                }
            }
        }
        return null;
    }

    //JSON 字符串 → List<List<String>>，异常则返回空列表
    private List<List<String>> parseParams(String json) {
        if (!StringUtils.hasLength(json)) return new ArrayList<>();
        try {
            return OM.readValue(json, new TypeReference<List<List<String>>>() {});
        } catch (Exception e) {
            log.error("json解析失败，json:{}", json, e);
            return new ArrayList<>();
        }
    }

    //original 传 0 / 负数按空处理
    private BigDecimal normalizeOriginal(BigDecimal original) {
        if (original == null || original.compareTo(BigDecimal.ZERO) <= 0) return null;
        return original;
    }


    //规范化规格款式（SKU）：入口统一清洗，保证入库 JSON 结构稳定。
    private List<Map<String, Object>> normalizeSkus(List<Map<String, Object>> skus) {
        if (skus == null) return null;
        List<Map<String, Object>> groups = new ArrayList<>();
        for (Map<String, Object> g : skus) {
            if (g == null) continue;
            String name = g.get("name") == null ? "" : String.valueOf(g.get("name")).trim();
            Object rawValues = g.get("values");
            if (!(rawValues instanceof List)) continue;

            List<Map<String, Object>> values = new ArrayList<>();
            for (Object item : (List<?>) rawValues) {
                String text;
                Object img = null;
                Object price = null;
                if (item instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) item;
                    text = m.get("v") == null ? "" : String.valueOf(m.get("v")).trim();
                    img = m.get("img");
                    price = m.get("price");
                } else {
                    text = item == null ? "" : String.valueOf(item).trim();
                }
                if (text.isEmpty()) continue;

                Map<String, Object> v = new LinkedHashMap<>();
                v.put("v", text);
                if (img != null && StringUtils.hasLength(String.valueOf(img))) {
                    v.put("img", String.valueOf(img));
                }
                BigDecimal p = parseSkuPrice(price, text);
                if (p != null) v.put("price", p);
                values.add(v);
            }
            if (values.isEmpty()) continue;

            Map<String, Object> group = new LinkedHashMap<>();
            group.put("name", name.isEmpty() ? "规格" : name);
            group.put("values", values);
            groups.add(group);
        }
        return groups.isEmpty() ? null : groups;
    }

    //款式价解析：未填（null / 空串）→ null（沿用商品默认价）；非法 → 抛业务异常
    private BigDecimal parseSkuPrice(Object price, String valueText) {
        if (price == null) return null;
        String raw = String.valueOf(price).trim();
        if (raw.isEmpty()) return null;
        BigDecimal p;
        try {
            p = new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw new BusinessException("款式「" + valueText + "」的价格不合法");
        }
        if (p.compareTo(BigDecimal.ONE) < 0 || p.compareTo(SKU_PRICE_MAX) > 0) {
            throw new BusinessException("款式「" + valueText + "」的价格需在 1-99999 元之间");
        }
        return p.setScale(2, RoundingMode.HALF_UP);
    }

    //obj → JSON 字符串，异常则抛业务异常
    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return OM.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BusinessException("数据格式错误");
        }
    }

    //desc 为空时从 detail 首段文字截取 120 字
    private String resolveDesc(String desc, List<Map<String, Object>> detail) {
        if (StringUtils.hasLength(desc)) return desc;
        if (detail != null && !detail.isEmpty()) {
            Object first = detail.get(0).get("text");
            if (first != null) {
                String t = String.valueOf(first).trim();
                return t.length() > 120 ? t.substring(0, 120) : t;
            }
        }
        return null;
    }

    //构造空分页结果
    private ProductsCheck emptyPage(Integer page, Integer size) {

        return new ProductsCheck(0L, page == null || page < 1 ? 1 : page, size == null || size < 1 ? 10 : size, new ArrayList<>());
    }

}
