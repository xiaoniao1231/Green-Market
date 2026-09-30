package org.web03.pojo.AfterSale;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后单商品明细实体
 * 一条售后单（= 一个订单 = 一个店铺）对应多行明细，一行 = 订单里的一件商品
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleItem {

    private Integer id;                 // 自增主键
    private Integer afterSaleId;        // 售后单ID（after_sales.id）
    private Integer orderItemId;        // 订单条目ID（order_items.id）
    private Integer productId;          // 商品ID
    private String title;               // 商品标题快照
    private String sku;                 // 款式文本快照
    private String artImg;              // 款式图 OSS 地址快照
    private Integer qty;                // 本件售后数量
    private BigDecimal price;           // 下单单价快照
    private BigDecimal refundAmount;    // 本件退款金额 = price × qty（换货为 0）
    private LocalDateTime createdAt;    // 创建时间
}
