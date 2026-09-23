package org.web03.service.impl;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web03.exception.BusinessException;
import org.web03.mapper.FavoriteMapper;
import org.web03.mapper.ProductMapper;
import org.web03.pojo.Favorite.Favorite;
import org.web03.pojo.Favorite.FavoriteAddRequest;
import org.web03.pojo.Favorite.FavoriteFanHui;
import org.web03.pojo.Favorite.FavoriteProductVO;
import org.web03.service.FavoriteService;
import org.web03.utils.CurrentHolder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收藏夹业务层实现类
 */


@Slf4j
@Service
public class FavoriteServiceImpl implements FavoriteService {

    private static final ObjectMapper OM = new ObjectMapper();

    @Autowired
    private FavoriteMapper favoriteMapper;
    @Autowired
    private ProductMapper productMapper;

    //获取当前用户ID
    private String currentUser(){
        String userId = CurrentHolder.getCurrentUserId();
        if(userId == null) throw new BusinessException("用户未登录或登录已失效");
        return userId;
    }

    //获取收藏夹列表
    @Override
    public FavoriteFanHui list(FavoriteFanHui favoriteFanHui) {
        String userId = currentUser();
        /* 分页参数规整：
           · page<1（如 page=0 / -1）会让 offset 变成负数，MySQL 的 LIMIT 负偏移直接报错 → 500；
           · size<1 同理；
           · 查询串写成 ?page=&size= 时 Spring 会把空串绑成 null，下面取值会拆箱 NPE → 500。
           这里统一回落到默认值，并给 size 设上限，避免一次拉全表。 */
        Integer pageParam = favoriteFanHui.getPage();
        Integer sizeParam = favoriteFanHui.getSize();
        int page = (pageParam == null || pageParam < 1) ? 1 : pageParam;
        int size = (sizeParam == null || sizeParam < 1) ? 20 : Math.min(sizeParam, 100);
        favoriteFanHui.setPage(page);
        favoriteFanHui.setSize(size);
        int offset = (page - 1) * size;

        favoriteFanHui.setTotal(favoriteMapper.countByUserId(userId));
        List<FavoriteProductVO> list = new ArrayList<>();
        for (Favorite row : favoriteMapper.listByUserId(userId, offset, size)) {
            list.add(toVO(row));
        }
        favoriteFanHui.setList(list);
        return favoriteFanHui;
    }

    //添加收藏
    @Override
    @Transactional
    public void add(FavoriteAddRequest favoriteAddRequest) {
        String userId = currentUser();
        if (favoriteAddRequest == null || favoriteAddRequest.getProductId() == null) {
            throw new BusinessException("商品ID不能为空");
        }
        Integer productId = favoriteAddRequest.getProductId();
        if(productMapper.getById(productId) == null){
            throw new BusinessException("商品不存在");
        }

        if (favoriteMapper.exists(userId, productId) > 0) {
            log.info("{} 已收藏商品 {} ", userId, productId);
            return;
        }
        favoriteMapper.insert(userId, productId);
        log.info("{} 收藏商品 {}", userId, productId);
    }

    //取消收藏
    @Override
    public void remove(Integer productId) {
        String userId = currentUser();
        int rows = favoriteMapper.deleteOne(userId, productId);
        log.info("{} 取消收藏商品 {} (物理删除 {} 行)", userId, productId, rows);

    }

    //清空收藏
    @Override
    public void clear() {
        String userId = currentUser();
        int rows = favoriteMapper.clear(userId);
        log.info("{} 清空收藏夹 (物理删除 {} 行)", userId, rows);

    }

    // 转换为前端契约对象
    private FavoriteProductVO toVO(Favorite row) {
        FavoriteProductVO vo = new FavoriteProductVO();
        vo.setId(row.getProductId());
        vo.setTitle(row.getTitle());
        vo.setPrice(row.getPrice());
        vo.setOriginal(row.getOriginalPrice());
        vo.setSales(row.getSales());
        vo.setStock(row.getStock());
        vo.setTag(row.getTag());
        vo.setArt(buildArt(row.getSkus()));
        vo.setOnSale(row.getOnSale());
        vo.setDeleted(row.getDeleted());
        Map<String, Object> shop = new HashMap<>();
        shop.put("id", row.getShopId());
        shop.put("name", row.getShopName());
        shop.put("score", row.getShopScore());
        vo.setShop(shop);
        return vo;
    }

    // 构建展示图（商品第一个图片：第一个带图的 SKU 款式值；无图返回 {e, g} 占位）
    private Map<String, Object> buildArt(String skusJson) {
        Map<String, Object> art = new HashMap<>();
        String firstImg = firstSkuImg(skusJson);
        if (firstImg != null && !firstImg.isEmpty()) {
            art.put("img", firstImg);
        } else {
            art.put("e", "🛍️");
            art.put("g", Arrays.asList("#e8e8e8", "#f5f5f5"));
        }
        return art;
    }

    // 获取第一个带图片的sku图片
    private String firstSkuImg(String skusJson) {
        if (skusJson == null || skusJson.isEmpty()) return null;
        try {
            List<Map<String, Object>> skus = OM.readValue(skusJson, new TypeReference<List<Map<String, Object>>>() {});
            for (Map<String, Object> group : skus) {
                Object values = group.get("values");
                if (!(values instanceof List)) continue;
                for (Object item : (List<?>) values) {
                    if (!(item instanceof Map)) continue;
                    Object img = ((Map<?, ?>) item).get("img");
                    if (img != null && !String.valueOf(img).isEmpty()) {
                        return String.valueOf(img);
                    }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }
}
