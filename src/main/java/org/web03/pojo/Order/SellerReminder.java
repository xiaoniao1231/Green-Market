package org.web03.pojo.Order;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 买家催发货记录（seller_reminders 表）。
 */
@Data
public class SellerReminder {
    private Integer id;
    private Integer orderId;              // 订单ID
    private String shopId;                // 被催的店铺
    private String buyerId;               // 提醒人（买家账号）
    private String ownerUserId;           // 店主账号（WebSocket 推送目标）
    private Integer remindCount;          // 累计提醒次数
    private LocalDateTime lastRemindTime; // 最近一次提醒时间
    private Integer handled;              // 店家是否已处理：1 已发货 / 0 未处理
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
