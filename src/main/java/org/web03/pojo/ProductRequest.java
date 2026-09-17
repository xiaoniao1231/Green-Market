package org.web03.pojo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 店家新增
 */
@Data
public class ProductRequest {
    private String title;                     // 标题
    private BigDecimal price;                 // 售价
    private BigDecimal original;              // 原价 / 划线价（可为空/0）
    private Integer stock;                    // 库存
    private String category;                  // 一级分类
    private String sub;                       // 二级分类
    private String tag;                       // 标签
    private Map<String, Object> art;          // 主图 {img:url}；无图时传渐变 {e,g}（后端只取 img 落库）
    private List<Map<String, Object>> skus;   // 规格款式 [{name, values:[{v,img}|字符串]}]
    private List<List<String>> params;        // 参数 [[键,值],...]
    private List<Map<String, Object>> detail; // 图文详情 [{type:"text",text}|{type:"img",url}]
    private String desc;                      // 简介（可空，由 detail 首段截取）
}
