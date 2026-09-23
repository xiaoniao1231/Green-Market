package org.web03.pojo.Order;


import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 订单对象
 */
@Data
public class OrderVO {
    private Integer id;                       // 订单ID
    private String orderNo;                   // 订单号
    private String status;                    // pending / paid / shipped / done / canceled
    private String createTime;                // 下单时间
    private String payTime;                   // 支付时间
    private String shipTime;                  // 发货时间
    private String finishTime;                // 完成时间
    private List<OrderItemVO> items;          // 订单条目
    private Map<String, Object> address;      // 收货地址快照
    private Map<String, Object> coupon;       // 优惠券快照
    private String payMethod;                 // 支付方式
    private String remark;                    // 订单备注
    private BigDecimal goodsAmount;           // 商品金额
    private BigDecimal discount;              // 优惠券抵扣
    private BigDecimal freight;               // 运费
    private BigDecimal total;                 // 应付总额
    private List<Map<String, Object>> logistics; // 物流轨迹
}