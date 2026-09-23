package org.web03.pojo.Order;


import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建订单请求参数
 */
@Data
public class OrderCreateRequest {
    private List<OrderCreateItem> items;   // 下单条目
    private Map<String, Object> address;   // 收货地址快照
    private Map<String, Object> coupon;    // 优惠券快照
    private String payMethod;              // 支付方式：支付宝 / 微信支付 / 银行卡
    private String remark;                 // 订单备注（可空，前端限制 50 字内）
}
