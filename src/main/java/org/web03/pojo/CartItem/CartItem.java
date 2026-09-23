package org.web03.pojo.CartItem;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;


/**
 * 购物车条目
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CartItem {
    private Integer id;                 // ID
    private String userId;              // 归属用户账号
    private Integer productId;          // 商品ID
    private String sku;                 // 规格文本
    private Integer quantity;           // 数量（1-999）
    private BigDecimal price;           // 款式成交价
    private LocalDateTime createdAt;    // 加入时间
    private LocalDateTime updatedAt;    // 更新时间

    /* ---- 商品数据源，不落库 ---- */
    private String title;               // 商品标题
    private BigDecimal productPrice;    // 商品默认价
    private BigDecimal originalPrice;   // 原价/划线价
    private Integer sales;              // 销量
    private Integer stock;              // 库存
    private String tag;                 // 标签
    private String skus;                // 规格
    private Integer onSale;             // 是否在售：1 在售 / 0 已下架
    private Integer deleted;            // 软删除：1 已删除 / 0 正常
    private String shopName;            // 店铺名
    private String shopId;              // 店铺标识
    private BigDecimal shopScore;       // 店铺评分
}
