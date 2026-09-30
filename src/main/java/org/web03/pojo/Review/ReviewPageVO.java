package org.web03.pojo.Review;


import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 评价分页结果
 */
@Data
public class ReviewPageVO {
    private Long total = 0L;                            // 总记录数
    private Integer page = 1;                           // 当前页
    private Integer size = 10;                          // 每页大小
    private List<ReviewVO> list = new ArrayList<>();    // 评价列表
    private ReviewSummaryVO summary;                    // 仅商品评价列表下发，评价汇总
    private List<PendingReviewOrder> orders;            // 仅待评价，待评价订单列表
}
