package org.web03.pojo.CartItem;


import lombok.Data;

import java.math.BigDecimal;

/**
 * 加入购物车请求参数
 */
@Data
public class CartAddRequest {
    private Integer productId;   // 商品ID
    private String skuText;      // 规格文本
    private Integer quantity;    // 加入数量
    private BigDecimal price;    // 选中款式的成交价
}
