package org.web03.pojo.AfterSale;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 售后对象
 */
@Data
public class AfterSaleVO {

    private Integer id;                      // 售后单ID
    private String afterNo;                  // 售后单号
    private Integer orderId;                 // 订单ID（售后粒度：一个订单 = 一个店铺）
    private String orderNo;                  // 订单号
    private Integer itemId;                  // 首件商品的订单条目ID（完整清单见 items）
    private Integer productId;               // 首件商品ID（完整清单见 items）
    private String title;                    // 首件商品标题快照
    private String sku;                      // 首件商品款式文本快照
    private Map<String, Object> art;         // 首件商品展示图 {img} 或占位 {e,g}
    private Integer qty;                     // 售后件数（全部明细合计）
    private BigDecimal price;                // 首件商品下单单价快照
    private BigDecimal refundAmount;         // 退款金额（全部明细合计）
    private List<AfterSaleItemVO> items;     // 商品明细（一条售后单可含该订单的多件商品）
    private String type;                     // 售后类型：refund（退款） / return（退货） / exchange（换货）
    private String reason;                   // 售后原因
    private String description;              // 补充说明
    private List<String> images;             // 凭证图地址数组
    private String status;                   // 见状态机
    private Map<String, Object> shop;        // 店铺摘要
    private Map<String, Object> buyer;       // 买家摘要
    private String returnAddress;            // 寄回地址
    private String refuseReason;             // 拒绝原因
    private String sellerRemark;             // 商家处理备注
    private Map<String, Object> express;     // 买家寄回
    private Map<String, Object> reship;      // 换货重发
    private List<AfterSaleLogVO> logs;       // 时间线（升序）
    private String createdAt;                // 申请时间
    private String updatedAt;                // 更新时间
    private String finishTime;               // 结束时间
}
