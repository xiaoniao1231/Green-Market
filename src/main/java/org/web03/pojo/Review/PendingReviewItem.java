package org.web03.pojo.Review;


import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 待评价订单里的商品条目
 */
@Data
public class PendingReviewItem {
    private Integer orderItemId;    // 条目ID
    private Integer orderId;       // 所属订单ID
    private Integer productId;      // 商品ID
    private String title;           // 商品标题快照
    private String sku;             // **该条目对应的款式文本**
    private Integer qty;            // 数量
    private BigDecimal price;       // 下单时售价快照
    private String artImg;          // 展示图 OSS 地址快照
    private Map<String, Object> art; // 展示图
    private Integer reviewed;       // **该款式**是否已评价：1 已评价 / 0 未评价
}