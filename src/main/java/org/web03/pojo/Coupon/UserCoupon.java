package org.web03.pojo.Coupon;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 用户持有的优惠券
 */

@Data
public class UserCoupon {
    private Integer id;                      // 持有记录ID
    private String userId;                   // 用户ID
    private Integer couponId;                // 券模板ID
    private String status;                   // unused / used
    private Integer usedOrderId;             // 使用订单ID
    private String usedAt;                   // 使用时间
    private String receivedAt;               // 领取时间

    private String title;                    // 以下来自 coupons 联表
    private BigDecimal threshold;
    private BigDecimal amount;
    private String expire;
}
