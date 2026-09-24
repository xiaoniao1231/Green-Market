package org.web03.pojo.Order;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建订单结果。
 */
@Data
public class OrderCreateResult {
    private String payNo;            // 支付单号：本次下单拆出的子订单共享
    private List<OrderVO> orders;    // 拆出的子订单（一单一店），按店铺首次出现顺序
    private Integer orderCount;      // 子订单数量
    private BigDecimal goodsAmount;  // 商品总金额
    private BigDecimal discount;     // 优惠券总抵扣
    private BigDecimal freight;      // 运费合计（每店独立计算）
    private BigDecimal totalAmount;  // 应付总额
}
