package org.web03.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web03.exception.BusinessException;
import org.web03.mapper.FootprintMapper;
import org.web03.mapper.ProductMapper;
import org.web03.pojo.Footprint.Footprint;
import org.web03.pojo.Footprint.FootprintListResult;
import org.web03.pojo.Footprint.FootprintProductVO;
import org.web03.service.FootprintService;
import org.web03.utils.CurrentHolder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 浏览足迹业务层实现类
 */

@Slf4j
@Service
public class FootprintServiceImpl implements FootprintService {

    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private FootprintMapper footprintMapper;

    private static final ObjectMapper OM = new ObjectMapper();
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    //我的足迹列表
    @Override
    public FootprintListResult list(Integer page, Integer size) {
        String userId = CurrentHolder.getCurrentUserId();
        int pageNo = (page == null || page < 1) ? 1 : page;
        int pageSize = (size == null || size < 1) ? 100 : Math.min(size, 200);
        int offset = (pageNo - 1) * pageSize;

        List<FootprintProductVO> list = new ArrayList<>();
        for (Footprint row : footprintMapper.listByUserId(userId, offset, pageSize)) {
            list.add(toVO(row));
        }
        return new FootprintListResult(footprintMapper.countByUserId(userId), pageNo, pageSize, list);
    }

    // 转换为前端契约对象（含商品图 art 与店铺 {id,name,score}，与收藏夹同口径）
    private FootprintProductVO toVO(Footprint row) {
        FootprintProductVO vo = new FootprintProductVO();
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
        vo.setBrowseTime(row.getUpdatedAt() == null ? null : row.getUpdatedAt().format(DTF));
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

    //记录足迹
    @Override
    public void record(Footprint request) {
        String userId = CurrentHolder.getCurrentUserId();
        if (request == null || request.getProductId() == null) throw new BusinessException("商品ID不能为空");
        Integer productId = request.getProductId();
        if (productMapper.getById(productId) == null) throw new BusinessException("商品不存在");
        int rows = footprintMapper.upsert(userId, productId);
        log.info("{} 记录浏览足迹 商品{} (影响 {} 行：1=新增, 0=已存在仅刷新时间)", userId, productId, rows);

        Integer total = footprintMapper.countByUserId(userId);
        if (total != null && total > 200) {
            int removed = footprintMapper.trim(userId, 200);
            log.info("{} 足迹超出上限 {}（当前 {} 条），清理最旧的 {} 条", userId, 200, total, removed);
        }

    }

}
