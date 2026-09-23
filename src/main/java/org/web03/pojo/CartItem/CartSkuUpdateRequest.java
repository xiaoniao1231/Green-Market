package org.web03.pojo.CartItem;

import lombok.Data;

import java.math.BigDecimal;

//修改购物车条目款式请求参数
@Data
public class CartSkuUpdateRequest {
    private String itemKey;      // 当前条目唯一标识
    private String skuText;      // 新规格文本
    private BigDecimal price;    // 新款式成交价
}
