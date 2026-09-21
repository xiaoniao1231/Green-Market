package org.web03.pojo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 返回给前端的商品对象
 */
@Data
public class ProductVO {
    private Integer id;                       // 商品ID
    private String title;                     // 标题
    private BigDecimal price;                 // 价格
    private BigDecimal original;              // 原价
    private Integer sales;                    // 销量
    private Integer stock;                    // 库存
    private String category;                  //一级分类
    private String sub;                       //二级分类
    private String tag;                       //标签
    private Map<String, Object> art;          // 有主图
    private Map<String, Object> shop;         // {name, score}
    private List<Map<String, Object>> skus;   // 规格款式
    private List<List<String>> params;        // 参数 [[键,值]]
    private List<Map<String, Object>> detail; // 图文详情段落
    private String desc;                      // 简介
    private Boolean onSale;                   // 是否在售
}
