package org.web03.pojo.CartItem;

import lombok.Data;

import java.util.List;

/**
 * 批量删除购物车条目请求参数
 */
@Data
public class CartDeleteRequest {
    private List<String> itemKeys; // 待删除条目
}