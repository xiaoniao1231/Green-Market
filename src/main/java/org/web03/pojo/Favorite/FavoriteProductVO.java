package org.web03.pojo.Favorite;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 收藏列表元素
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteProductVO {
    private Integer id;                       // 商品ID
    private String title;                     // 标题
    private BigDecimal price;                 // 售价
    private BigDecimal original;              // 原价/划线价
    private Map<String, Object> art;          // 主图：{img} 或占位 {e, g}
    private Integer sales;                    // 销量
    private Integer stock;                    // 库存
    private String tag;                       // 标签
    private Map<String, Object> shop;         // {name, score}
}
