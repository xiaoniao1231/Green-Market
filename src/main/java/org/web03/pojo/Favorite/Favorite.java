package org.web03.pojo.Favorite;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收藏记录
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Favorite {
    private Integer id;                 // 收藏记录ID
    private String userId;              // 归属用户账号
    private Integer productId;          // 商品ID
    private LocalDateTime createdAt;    // 收藏时间

    /* ---- 商品快照数据源，不落库 ---- */
    private String title;               // 商品标题
    private BigDecimal price;           // 售价
    private BigDecimal originalPrice;   // 原价/划线价
    private Integer sales;              // 销量
    private Integer stock;              // 库存
    private String tag;                 // 标签
    private String skus;                // 商品主图
    private String shopName;            // 店铺名
    private BigDecimal shopScore;       // 店铺评分
}
