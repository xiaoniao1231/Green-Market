package org.web03.pojo.CartItem;


import lombok.Data;

/**
 * 修改购物车数量请求参数
 */
@Data
public class CartUpdateRequest {
    private String itemKey;   // 条目唯一标识
    private Integer quantity; // 新数量
}