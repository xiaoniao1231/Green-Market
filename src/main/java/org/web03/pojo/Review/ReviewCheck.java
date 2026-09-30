package org.web03.pojo.Review;

import lombok.Data;

/**
 * 评价查询条件
 */
@Data
public class ReviewCheck {
    private Integer productId;   // 商品ID
    private String shopId;       // 店铺ID（店家视角）
    private Integer score;       // 只看某档评分
    private String replyStatus;  // 店家视角：all / unreplied / replied / appended
    private Boolean hasImage;    // 只看有晒单图
    private String sku;          // 只看某个款式
    private String sort;         // new 最新（默认）/ score 评分从高到低
    private Integer page = 1;    // 页码
    private Integer size = 10;   // 每页条数
    private Integer offset;      // 计算属性：由 Service 设置
    private Long total;          // 总数
    private String userId;       // 当前账号
}