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
    private BigDecimal price;      // 条目级成交价快照
    private CartProductVO product; // 商品快照
}