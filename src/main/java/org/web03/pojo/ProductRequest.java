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
    private BigDecimal original;              // 原价 / 划线价
    private Integer stock;                    // 库存
    private String category;                  // 一级分类
    private String sub;                       // 二级分类
    private String tag;                       // 标签
    private List<Map<String, Object>> skus;   // 规格款式（主图由「第一个带图的款式值」推导，不再单独上传/落库 art）
    private List<List<String>> params;        // 参数
    private List<Map<String, Object>> detail; // 图文详情
    private String desc;                      // 简介
}
