package org.web03.pojo.Footprint;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 浏览足迹记录
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Footprint {
    private Integer id;                 // 足迹记录ID
    private String userId;              // 归属账号
    private Integer productId;          // 商品ID
    private LocalDateTime createdAt;    // 首次浏览时间
    private LocalDateTime updatedAt;    // 最近浏览时间
    private String title;               // 商品标题
    private BigDecimal price;           // 售价
    private BigDecimal originalPrice;   // 原价/划线价
    private Integer sales;              // 销量
    private Integer stock;              // 库存
    private String tag;                 // 标签
    private String skus;                // 规格款式
    private String shopId;              // 店铺标识
    private String shopName;            // 店铺名
    private BigDecimal shopScore;       // 店铺评分
    private Integer onSale;             // 是否在售：1 在售 / 0 已下架
    private Integer deleted;            // 软删除：1 已删除 / 0 正常
}
