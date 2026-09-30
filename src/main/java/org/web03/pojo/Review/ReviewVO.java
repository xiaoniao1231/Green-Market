package org.web03.pojo.Review;


import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 返回给前端的评价对象
 */
@Data
public class ReviewVO {
    private Long id;                    // 评价ID
    private Integer orderId;            // 来源订单
    private String orderNo;             // 订单号
    private Integer orderItemId;        // 来源订单条目
    private Integer productId;          // 商品ID
    private String shopId;              // 店铺标识
    private Integer score;              // 评分 1-5
    private String content;             // 评价内容
    private List<String> images;        // 晒单图地址数组
    private Boolean anonymous;          // 是否匿名
    private String sku;                 // **该评价对应的款式文本**

    private Map<String, Object> user;   // 匿名时对外隐藏身份
    private Map<String, Object> product;// 商品信息
    private Map<String, Object> shop;   // 店铺信息
    private Map<String, Object> append; //  追加评价
    private Map<String, Object> reply;  // 回复
    private String createdAt;           // 评价时间
}
