package org.web03.pojo.Coupon;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 优惠券实体
 */

@Data
public class Coupon {
    private Integer id;             // 优惠券ID
    private String title;           // 优惠券标题
    private BigDecimal threshold;   // 优惠券门槛
    private BigDecimal amount;      // 优惠券金额
    private String expire;          // 优惠券有效期
    private Integer enabled;        // 优惠券是否启用
    private String createdAt;       // 优惠券创建时间
}
