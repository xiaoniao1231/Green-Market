package org.web03.pojo.Review;


import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 待评价订单
 */
@Data
public class PendingReviewOrder {
    private Integer id;                 // 订单ID
    private String orderNo;             // 订单号
    private LocalDateTime finishTime;   // 完成时间
    private List<PendingReviewItem> items = new ArrayList<>();  // 该订单的商品条目
}
