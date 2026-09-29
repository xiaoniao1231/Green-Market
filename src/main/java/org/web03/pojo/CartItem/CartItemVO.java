package org.web03.pojo.CartItem;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 购物车条目
 */
@Data
public class CartItemVO {
    private String itemKey;        // 购物车条目唯一标识
    private Integer productId;     // 商品ID
    private String sku;            // 规格文本
    private Integer qty;           // 数量（1-999）
    private BigDecimal price;      // 条目单价（到手价 / 款式价）
    private BigDecimal flashPrice; // 当日秒杀价，非秒杀商品为 null
    private Integer flashQty;      // 本条目可享秒杀价的件数（0 或 1）
    private Boolean flashUsed;     // 当前账号今日是否已用完该商品的秒杀价
    private CartProductVO product; // 商品快照
}