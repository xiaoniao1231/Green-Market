package org.web03.pojo.Favorite;

import lombok.Data;

/**
 * 添加收藏请求参数
 */

@Data
public class FavoriteAddRequest {
    private Integer productId;   // 商品ID
}
