package org.web03.pojo.Order;


import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 订单条目
 */
@Data
public class OrderItemVO {
    private Integer productId;        // 商品ID
    private String title;             // 商品标题快照
    private String sku;               // 规格文本
    private Integer qty;              // 数量
    private BigDecimal price;         // 下单时售价
    private Map<String, Object> art;  // 展示图：商品第一个图片 {img}，无图返回占位 {e, g}
}
