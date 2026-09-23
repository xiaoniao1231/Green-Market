package org.web03.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.web03.exception.BusinessException;
import org.web03.mapper.CartMapper;
import org.web03.mapper.ProductMapper;
import org.web03.pojo.CartItem.*;
import org.web03.service.CartService;
import org.web03.utils.CurrentHolder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@Slf4j
@Service
public class CartServiceImpl implements CartService {

    private static final int MAX_QTY = 999;
    private static final ObjectMapper OM = new ObjectMapper();

    @Autowired
    private CartMapper cartMapper;
    @Autowired
    private ProductMapper productMapper;

    //当前登录账号
    private String currentUser() {
        String userId = CurrentHolder.getCurrentUserId();
        if (!StringUtils.hasLength(userId)) throw new BusinessException("未登录或登录已失效");
        return userId;
    }

    //购物车条目键
    private record ItemKey(int productId, String sku) { }

    //解析购物车条目键
    private ItemKey parseItemKey(String itemKey) {
        if (!StringUtils.hasLength(itemKey)) throw new BusinessException("购物车条目参数无效");
        int idx = itemKey.indexOf('|');
        if (idx <= 0 || idx >= itemKey.length() - 1) throw new BusinessException("购物车条目参数无效");
        int productId;
        try {
            productId = Integer.parseInt(itemKey.substring(0, idx));
        } catch (NumberFormatException e) {
            throw new BusinessException("购物车条目参数无效");
        }
        return new ItemKey(productId, itemKey.substring(idx + 1));
    }

    //验证数量
    private int requireQuantity(Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > MAX_QTY) {
            throw new BusinessException("数量必须在1-" + MAX_QTY + "之间");
        }
        return quantity;
    }

    //获取当前用户购物车列表
    @Override
    public List<CartItemVO> list() {
        List<CartItemVO> list = new ArrayList<>();
        for(CartItem row : cartMapper.listByUserId(currentUser())){
            list.add(toVo(row));
        }
        log.info("获取购物车列表成功: {}", list.size());
        return list;
    }

    //加入购物车
    @Override
    public void add(CartAddRequest cartAddRequest) {
        if (cartAddRequest == null || cartAddRequest.getProductId() == null) {
            throw new BusinessException("商品参数无效");
        }
        String userId = currentUser();
        if(productMapper.getPublicById(cartAddRequest.getProductId()) == null) throw new BusinessException("商品不存在或已下架");
        String skuText = StringUtils.hasLength(cartAddRequest.getSkuText()) ? cartAddRequest.getSkuText().trim() : "默认";
        if (skuText.length() > 100) throw new BusinessException("规格参数过长");
        CartItem cartItem = new CartItem();
        cartItem.setUserId(userId);
        cartItem.setProductId(cartAddRequest.getProductId());
        cartItem.setSku(skuText);
        cartItem.setQuantity(requireQuantity(cartAddRequest.getQuantity()));
        cartItem.setPrice(cartAddRequest.getPrice());
        cartMapper.upsert(cartItem);

        log.info("加入购物车成功: {}", cartItem.getId());

    }

    //修改数量
    @Override
    public void updateQty(CartUpdateRequest cartUpdateRequest) {
        String userId = currentUser();
        ItemKey key = parseItemKey(cartUpdateRequest.getItemKey());
        int quantity = requireQuantity(cartUpdateRequest.getQuantity());
        int rows = cartMapper.updateQty(userId, key.productId(), key.sku(), quantity);
        if (rows == 0) throw new BusinessException("购物车中不存在该商品");
        log.info("修改购物车数量: {} (itemKey={}, 数量={})", userId, cartUpdateRequest.getItemKey(), quantity);

    }

    //删除购物车
    @Override
    @Transactional
    public void delete(CartDeleteRequest cartDeleteRequest) {
        String userId = currentUser();
        List<String> keys = (cartDeleteRequest == null || cartDeleteRequest.getItemKeys() == null)
                ? Collections.emptyList() : cartDeleteRequest.getItemKeys();
        int removed = 0;
        for (String key : keys) {
            if (!StringUtils.hasLength(key)) continue;
            try {
                ItemKey parsed = parseItemKey(key);
                removed += cartMapper.deleteOne(userId, parsed.productId(), parsed.sku());
            } catch (BusinessException e) {
                log.warn("跳过无效购物车条目: {}", key);
            }
        }
        log.info("删除购物车条目: {} (共 {} 条，实际删除 {})", userId, keys.size(), removed);
    }

    //修改款式
    @Override
    @Transactional
    public void updateSku(CartSkuUpdateRequest cartSkuUpdateRequest) {
        String userId = currentUser();
        if (cartSkuUpdateRequest == null || !StringUtils.hasLength(cartSkuUpdateRequest.getSkuText())) {
            throw new BusinessException("请选择要更换的款式");
        }
        ItemKey old = parseItemKey(cartSkuUpdateRequest.getItemKey());
        String newSku = cartSkuUpdateRequest.getSkuText().trim();
        if (newSku.length() > 100) throw new BusinessException("规格参数过长");
        if(newSku.equals(old.sku())) return;

        if(productMapper.getPublicById(old.productId()) == null) throw new BusinessException("商品不存在或已下架");

        // 换款式保持原数量；新款式已存在则数量合并
        CartItem oldRow = cartMapper.findByKey(userId, old.productId, old.sku);
        if (oldRow == null) throw new BusinessException("购物车中不存在该商品");

        CartItem item = new CartItem();
        item.setUserId(userId);
        item.setProductId(old.productId);
        item.setSku(newSku);
        item.setQuantity(oldRow.getQuantity());
        item.setPrice(cartSkuUpdateRequest.getPrice());
        cartMapper.upsert(item);

        //删除旧条目
        cartMapper.deleteOne(userId, old.productId, old.sku);
        log.info("修改购物车款式: {} ({} -> {})", userId, cartSkuUpdateRequest.getItemKey(), newSku);
        
    }

    //清空购物车
    @Override
    public void clear() {
        String userId = currentUser();
        int rows = cartMapper.clear(userId);
        log.info("清空购物车: {} (删除 {} 条)", userId, rows);
    }

    //将数据库对象转换为VO对象
    private CartItemVO toVo(CartItem row) {
        CartItemVO vo = new CartItemVO();
        vo.setProductId(row.getProductId());
        vo.setSku(row.getSku());
        vo.setQty(row.getQuantity());
        vo.setPrice(row.getPrice());
        vo.setItemKey(row.getProductId() + "|" + row.getSku());

        CartProductVO p = new CartProductVO();
        p.setId(row.getProductId());
        p.setTitle(row.getTitle());
        p.setPrice(row.getProductPrice());
        p.setOriginal(row.getOriginalPrice());
        p.setSales(row.getSales());
        p.setStock(row.getStock());
        p.setTag(row.getTag());
        p.setOnSale(row.getOnSale());
        p.setDeleted(row.getDeleted());
        p.setSkus(parseSkus(row.getSkus()));
        p.setArt(buildArt(row.getSkus()));
        p.setShopScore(row.getShopScore());
        Map<String, Object> shop = new HashMap<>();
        shop.put("id", row.getShopId());
        shop.put("name", row.getShopName());
        shop.put("score", row.getShopScore());
        p.setShop(shop);
        vo.setProduct(p);
        return vo;
    }

    //构建商品图片(显示图片取第一个图片)
    private Map<String, Object> buildArt(String art) {
        Map<String, Object> artMap = new HashMap<>();
        String firstImg = firstSkuImg(art);
        if (StringUtils.hasLength(firstImg)) {
            artMap.put("img", firstImg);
        } else {
            artMap.put("e", "🛍️");
            artMap.put("g", Arrays.asList("#e8e8e8", "#f5f5f5"));
        }
        return artMap;
    }

    //获取第一个SKU的图片
    private String firstSkuImg(String art) {
        if (!StringUtils.hasLength(art)) return null;
        try {
            List<Map<String, Object>> skus = OM.readValue(art, new TypeReference<>() {
            });
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
        } catch (Exception ignored) { }
        return null;
    }


    //将数据库的JSON字符串转换为List<Map<String, Object>>
    private List<Map<String, Object>> parseSkus(String skus) {
        if (!StringUtils.hasLength(skus)) return null;
        try {
            return OM.readValue(skus, new TypeReference<>() {
            });
        } catch (Exception ignored) { }
        return null;
    }
}
