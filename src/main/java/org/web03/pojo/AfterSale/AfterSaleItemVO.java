package org.web03.pojo.AfterSale;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 售后单商品明细（接口对象）
 * 申请售后时前端只需要传 productId + sku + qty，
 * 标题 / 图片 / 价格一律由服务端按订单条目快照回填，不信任前端传值
 */
@Data
public class AfterSaleItemVO {

    private Integer id;                 // 明细ID
    private Integer itemId;             // 订单条目ID（order_items.id）
    private Integer productId;          // 商品ID
    private String title;               // 商品标题快照
    private String sku;                 // 款式文本快照
    private Map<String, Object> art;    // 展示图 {img} 或占位 {e,g}
    private Integer qty;                // 本件售后数量
    private BigDecimal price;           // 下单单价快照
    private BigDecimal refundAmount;    // 本件退款金额
}
