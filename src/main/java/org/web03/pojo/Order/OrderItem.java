package org.web03.pojo.Order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单条目实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
    private Integer id;               // 条目ID
    private Integer orderId;          // 订单ID
    private String shopId;            // 商品所属店铺快照
    private Integer productId;        // 商品ID
    private String title;             // 商品标题快照
    private String sku;               // 规格文本
    private Integer qty;              // 数量
    private BigDecimal price;         // 下单时售价快照
    private String artImg;            // 展示图 OSS 地址快照（下单时定格商品第一个图片）
    private LocalDateTime createdAt;  // 创建时间
}