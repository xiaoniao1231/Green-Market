package org.web03.pojo.Order;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建订单请求里的条目
 */
@Data
public class OrderCreateItem {
    private Integer productId;   // 商品ID
    private String sku;          // 规格文本
    private Integer qty;         // 数量
    private BigDecimal price;    // 前端购物车快照价
    private String title;        // 前端快照标题
}
