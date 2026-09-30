package org.web03.pojo.Review;


import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品评价
 */
@Data
public class ProductReview {

    /* ---------- 表字段 ---------- */
    private Long id;                // 评价ID
    private Integer orderId;        // 来源订单
    private Integer orderItemId;    // 来源订单条目
    private Integer productId;      // 被评价商品
    private String shopId;          // 商品所属店铺快照
    private String userId;          // 评价人账号
    private Integer score;          // 评分 1-5
    private String content;         // 评价内容
    private String images;          // 晒单图地址 JSON 数组字符串
    private Integer anonymous;      // 是否匿名：1 是 / 0 否
    private String sku;             // 款式文本快照
    private String appendContent;   // 追评内容
    private LocalDateTime appendTime;  // 追评时间
    private String replyContent;    // 商家回复内容
    private LocalDateTime replyTime;   // 商家回复时间
    private LocalDateTime createdAt;// 评价时间
    private LocalDateTime updatedAt;// 更新时间

    /* ---------- JOIN 附加字段（非表列） ---------- */
    private String orderNo;             // orders.order_no
    private String productTitle;        // products.title
    private String productSkus;         // products.skus（用于推导商品缩略图，展示图由 SKU 推导）
    private String shopName;            // shops.name
    private String reviewerNickname;    // users.nickname
    private String reviewerAvatar;      // users.avatar
}
