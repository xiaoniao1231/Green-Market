package org.web03.pojo.Review;

import lombok.Data;

import java.util.List;

/**
 * 发表评价请求体
 */
@Data
public class ReviewCreateRequest {
    private Integer orderItemId;    // 订单条目ID：必须属于当前账号的已完成订单
    private Integer score;          // 评分 1-5
    private String content;         // 评价内容（≤500 字）
    private List<String> images;    // 晒单图 OSS 地址（≤6 张）
    private Boolean anonymous;      // 是否匿名
}