package org.web03.pojo.Order;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private Integer id;               // 订单ID
    private String orderNo;           // 订单号
    private String userId;            // 买家账号
    private String status;            // pending 待付款 / paid 待发货 / shipped 待收货 / done 已完成 / canceled 已取消
    private BigDecimal goodsAmount;   // 商品金额（下单时定格）
    private BigDecimal discount;      // 优惠券抵扣金额
    private BigDecimal freight;       // 运费
    private BigDecimal total;         // 应付总额
    private String payMethod;         // 支付方式
    private String remark;            // 订单备注
    private String addressJson;       // 收货地址快照
    private String couponJson;        // 优惠券快照
    private String logisticsJson;     // 物流轨迹
    private LocalDateTime payTime;    // 支付时间
    private LocalDateTime shipTime;   // 发货时间
    private LocalDateTime finishTime; // 完成时间（确认收货）
    private LocalDateTime createdAt;  // 下单时间
}
