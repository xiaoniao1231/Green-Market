package org.web03.pojo.AfterSale;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后单主表实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AfterSale {

    private Integer id;                 // 售后单ID
    private String afterNo;             // 售后单号
    private Integer orderId;            // 订单ID
    private String orderNo;             // 订单号快照
    private Integer orderItemId;        // 订单条目ID（售后粒度）
    private String userId;              // 买家账号
    private String shopId;              // 商品所属店铺快照
    private Integer productId;          // 商品ID
    private String title;               // 商品标题快照
    private String sku;                 // 规格文本快照
    private String artImg;              // 商品图 OSS 地址快照
    private Integer qty;                // 售后件数
    private BigDecimal price;           // 下单时单价快照
    private BigDecimal refundAmount;    // 退款金额 = price × qty（换货为 0）
    private String type;                // refund 仅退款 / return 退货退款 / exchange 换货
    private String reason;              // 售后原因
    private String description;         // 补充说明
    private String images;              // 凭证图 JSON 数组
    private String status;              // pending / agreed / returned / refunded / exchanged / refused / canceled
    private String returnAddress;       // 商家给出的寄回地址
    private String refuseReason;        // 商家拒绝原因
    private String sellerRemark;        // 商家处理备注
    private String buyerCompany;        // 买家寄回承运商
    private String buyerTrackingNo;     // 买家寄回运单号
    private LocalDateTime buyerShipTime;// 买家寄回时间
    private String reshipCompany;       // 换货重发承运商
    private String reshipNo;            // 换货重发运单号
    private LocalDateTime reshipTime;   // 换货重发时间
    private LocalDateTime finishTime;   // 售后结束时间
    private LocalDateTime createdAt;    // 申请时间
    private LocalDateTime updatedAt;    // 更新时间

    /* ------------------------------------------------------------------
       以下三个字段只用于接收请求体（不落库、不参与 SQL）：
       前端与《售后服务接口文档》用的字段名是 company / trackingNo / remark，
       而表列名派生的实体字段是 buyerCompany / buyerTrackingNo / sellerRemark。
       若只保留后者，`@RequestBody AfterSale` 反序列化时前端传来的值会被静默丢弃
       （表现为「买家填了寄回物流却总提示请填写快递公司」、「商家备注存不上」）。
       ------------------------------------------------------------------ */
    private String company;             // 入参：买家寄回承运商（POST /after-sales/{id}/ship）
    private String trackingNo;          // 入参：买家寄回运单号（同上）
    private String remark;              // 入参：商家处理备注（approve / receive）
}
