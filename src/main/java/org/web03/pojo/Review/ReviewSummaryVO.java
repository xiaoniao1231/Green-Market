package org.web03.pojo.Review;


import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评分汇总
 */
@Data
public class ReviewSummaryVO {
    private Integer productId;                       // 商品ID
    private BigDecimal rating;                       // 商品评分
    private Integer reviewCount;                     // 评价数
    private Integer goodRate;                        // 好评率
    private Map<String, Integer> distribution = new LinkedHashMap<>();  // 各评分段分布
    private BigDecimal shopScore;                    // 该商品所属店铺当前评分
    private List<Map<String, Object>> skus = new ArrayList<>();    //款式分布
}
