package org.web03.service.impl;


import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.web03.exception.BusinessException;
import org.web03.mapper.ProductMapper;
import org.web03.mapper.ShopMapper;
import org.web03.pojo.CheckProducts;
import org.web03.pojo.Product;
import org.web03.pojo.ProductRequest;
import org.web03.pojo.ProductVO;
import org.web03.service.ProductService;
import org.web03.utils.AliyunOSSOperator;
import org.web03.utils.AliyunOSSProperties;
import org.web03.utils.CurrentHolder;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

    /**商品列表*/
    @Override
    public CheckProducts sellerList(CheckProducts checkProducts) {
        checkProducts.setShopId(requireShopId());
        Integer page = checkProducts.getPage();
        Integer size = checkProducts.getSize();
        checkProducts.setOffset((page - 1) * size);

        List<ProductVO> list = productMapper.sellerList(checkProducts)
                .stream().map(this::toVO).collect(Collectors.toList());

        long total = productMapper.sellerCount(checkProducts);

        return new CheckProducts(total, page, size, list);

    }

    /**公开商品列表（买家端：不要求登录/开店，只返回在售商品）*/
    @Override
    public CheckProducts publicList(CheckProducts checkProducts) {
        Integer page = checkProducts.getPage();
        Integer size = checkProducts.getSize();
        checkProducts.setOffset((page - 1) * size);

        List<ProductVO> list = productMapper.publicList(checkProducts)
                .stream().map(this::toVO).collect(Collectors.toList());

        long total = productMapper.publicCount(checkProducts);

        return new CheckProducts(total, page, size, list);
    }

    /**创建商品*/
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
        if (productRequest.getArt() != null && productRequest.getArt().get("img") != null) {
            p.setArtImg(String.valueOf(productRequest.getArt().get("img")));
        }
        p.setSkus(toJson(productRequest.getSkus()));
        p.setParams(toJson(productRequest.getParams()));
        p.setDetail(toJson(productRequest.getDetail()));
        p.setDescription(resolveDesc(productRequest.getDesc(), productRequest.getDetail()));
        p.setOnSale(1);
        p.setDeleted(0);

        productMapper.insert(p);
        return toVO(productMapper.getById(p.getId()));
    }


    /**删除商品*/
    @Override
    public Map<String, Object> delete(Integer id) {
        String shopId = requireShopId();
        int rows = productMapper.softDelete(id, shopId);
        if (rows == 0) throw new BusinessException("商品不存在");
        return Collections.singletonMap("deleted", true);
    }

    /**更新商品*/
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
        if (req.getArt() != null) {
            Object img = req.getArt().get("img");
            exist.setArtImg(img == null ? null : String.valueOf(img));
        }
        if (req.getSkus() != null) exist.setSkus(toJson(req.getSkus()));
        if (req.getParams() != null) exist.setParams(toJson(req.getParams()));
        if (req.getDetail() != null) exist.setDetail(toJson(req.getDetail()));
        if (req.getDesc() != null) exist.setDescription(req.getDesc());

        productMapper.update(exist);
        return toVO(productMapper.getByIdAndShop(id, shopId));
    }


    /**更新商品上架/下架状态*/
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

    /**获取商品详情（用于编辑商品时回填表单）*/
    @Override
    public ProductVO getForEdit(Integer id) {
        String shopId = requireShopId();
        Product p = productMapper.getByIdAndShop(id, shopId);
        if (p == null) throw new BusinessException("商品不存在");
        return toVO(p);
    }

    /**获取商品详情（用于商品详情页面；下架 / 已删除商品对买家不可见）*/
    @Override
    public ProductVO getDetail(Integer id) {
        Product p = productMapper.getPublicById(id);
        if(p == null)throw new BusinessException("商品不存在");
        return toVO(p);
    }

    /**相关推荐：同分类优先，再用其他在售商品补足；排除当前商品*/
    @Override
    public List<ProductVO> related(Integer id, Integer size) {
        Product self = productMapper.getPublicById(id);
        if (self == null) throw new BusinessException("商品不存在");
        int limit = (size == null || size < 1) ? 5 : Math.min(size, 20);
        return productMapper.related(id, self.getCategory(), limit)
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    /**限时秒杀*/
    @Override
    public Map<String, Object> flash() {
        Map<String, Object> data = new HashMap<>();
        data.put("endTime", System.currentTimeMillis() + 2 * 60 * 60 * 1000L); // 当前 + 2 小时
        data.put("list", productMapper.flashList(6).stream().map(this::toVO).collect(Collectors.toList()));
        return data;
    }

    /**推荐商品*/
    @Override
    public CheckProducts recommend(CheckProducts checkProducts) {
        checkProducts.setOffset((checkProducts.getPage() - 1) * checkProducts.getSize());

        List<ProductVO> list = productMapper.recommendList(checkProducts)
                .stream().map(this::toVO).collect(Collectors.toList());
        long total = productMapper.recommendCount();

        return new CheckProducts(total, checkProducts.getPage(), checkProducts.getSize(), list);
    }

    /**搜索商品*/
    @Override
    public CheckProducts search(CheckProducts checkProducts) {
        if (!StringUtils.hasLength(checkProducts.getQ())) {
            return emptyPage(checkProducts.getPage(), checkProducts.getSize());
        }
        checkProducts.setOffset((checkProducts.getPage() - 1) * checkProducts.getSize());

        List<ProductVO> list = productMapper.search(checkProducts)
                .stream().map(this::toVO).collect(Collectors.toList());
        long total = productMapper.searchCount(checkProducts.getQ().trim());
        return new CheckProducts(total, checkProducts.getPage(), checkProducts.getSize(), list);
    }

    /**上传图片*/
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

    /**从登录态账号取本店 id；未登录抛“未登录”，已登录但未开店才抛“当前账号未开店”*/
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
    /**Product（DB 行）→ ProductVO（前端契约对象）*/
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

        // art：有图返回 {img}；无图返回渐变占位
        Map<String, Object> art = new HashMap<>();
        if (StringUtils.hasLength(p.getArtImg())) {
            art.put("img", p.getArtImg());
        } else {
            art.put("e", "🛍️");
            art.put("g", Arrays.asList("#e8e8e8", "#f5f5f5"));
        }
        vo.setArt(art);

        // shop：JOIN 结果
        Map<String, Object> shop = new HashMap<>();
        shop.put("name", p.getShopName());
        shop.put("score", p.getShopScore());
        vo.setShop(shop);

        vo.setSkus(parseList(p.getSkus()));
        vo.setParams(parseParams(p.getParams()));
        vo.setDetail(parseList(p.getDetail()));
        vo.setDesc(p.getDescription());
        vo.setOnSale(p.getOnSale() != null && p.getOnSale() == 1);
        return vo;
    }

    /**JSON 字符串 → List<Map>，异常则返回空列表*/
    private List<Map<String, Object>> parseList(String json) {
        if (!StringUtils.hasLength(json)) return new ArrayList<>();
        try {
            return OM.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("json解析失败，json:{}", json, e);
            return new ArrayList<>();
        }
    }

    /**JSON 字符串 → List<List<String>>，异常则返回空列表*/
    private List<List<String>> parseParams(String json) {
        if (!StringUtils.hasLength(json)) return new ArrayList<>();
        try {
            return OM.readValue(json, new TypeReference<List<List<String>>>() {});
        } catch (Exception e) {
            log.error("json解析失败，json:{}", json, e);
            return new ArrayList<>();
        }
    }

    /** original 传 0 / 负数按空处理 */
    private BigDecimal normalizeOriginal(BigDecimal original) {
        if (original == null || original.compareTo(BigDecimal.ZERO) <= 0) return null;
        return original;
    }


    /** obj → JSON 字符串，异常则抛业务异常 */
    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return OM.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BusinessException("数据格式错误");
        }
    }

    /** desc 为空时从 detail 首段文字截取 120 字 */
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

    /** 构造空分页结果 */
    private CheckProducts emptyPage(Integer page, Integer size) {

        return new CheckProducts(0L, page == null || page < 1 ? 1 : page, size == null || size < 1 ? 10 : size, new ArrayList<>());
    }

}
