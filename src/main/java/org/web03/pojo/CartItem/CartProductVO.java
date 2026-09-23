package org.web03.pojo.CartItem;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 购物车条目中的商品信息
 */
@Data
public class CartProductVO {
    private Integer id;                       // 商品ID
    private String title;                     // 标题
    private BigDecimal price;                 // 售价
    private BigDecimal original;              // 原价/划线价
    private Map<String, Object> art;          // 第一个带 img 的款式的图片
    private List<Map<String, Object>> skus;   // 规格数组
    private Integer sales;                    // 销量
    private Integer stock;                    // 库存
    private String tag;                       // 标签
    private Integer onSale;                   // 是否在售：1 在售 / 0 已下架
    private Integer deleted;                  // 软删除：1 已删除 / 0 正常
    private BigDecimal shopScore;             // 店铺评分
    private Map<String, Object> shop;         // 店铺信息 {name, score}（前端购物车按店铺分组展示用）
}