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
    private Integer id;
    private String title;
    private BigDecimal price;
    private BigDecimal original;
    private Integer sales;
    private Integer stock;
    private String category;
    private String sub;
    private String tag;
    private Map<String, Object> art;          // 有主图 {img:url}；无图 {e,g}（前端渐变占位）
    private Map<String, Object> shop;         // {name, score}
    private List<Map<String, Object>> skus;   // 规格款式
    private List<List<String>> params;        // 参数 [[键,值]]
    private List<Map<String, Object>> detail; // 图文详情段落
    private String desc;                      // 简介
    private Boolean onSale;                   // 是否在售
}
