package org.web03.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    private Integer id;             // 自增主键
    private String shopId;          // 所属店铺标识（shops.shop_id）
    private String title;           // 商品标题
    private BigDecimal price;       // 售价
    private BigDecimal originalPrice;// 原价 / 划线价
    private Integer sales;          // 销量
    private Integer stock;          // 库存
    private String category;        // 一级分类
    private String sub;             // 二级分类
    private String tag;             // 标签（如 包邮）
    private String artImg;          // 商品主图 OSS 地址（无图时前端回退渐变占位）
    private String skus;            // 规格款式 JSON：[{name, values:[{v,img}|字符串]}]
    private String params;          // 参数 JSON：[[键,值],...]
    private String detail;          // 图文详情 JSON：[{type:"text"|"img",...}]
    private String description;     // 商品简介（可由 detail 首段截取）
    private Integer onSale;         // 是否在售：1 是 / 0 否
    private Integer deleted;        // 软删除：1 已删 / 0 正常
    private LocalDateTime createdAt;// 创建时间
    private LocalDateTime updatedAt;// 更新时间

    /** 关联字段：店铺名 / 店铺评分（JOIN shops 填充，不落库） */
    private String shopName;
    private BigDecimal shopScore;
}
