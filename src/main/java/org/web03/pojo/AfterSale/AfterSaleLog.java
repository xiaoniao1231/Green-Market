package org.web03.pojo.AfterSale;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 售后处理记录实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleLog {

    private Integer id;             // 自增主键
    private Integer afterSaleId;    // 售后单ID
    private String role;            // 操作者:buyer 买家 / seller 卖家 / system 系统

    //操作:apply(买家发起售后申请) approve(卖家同意处理) refuse(卖家拒绝处理) ship(卖家发货) receive(买家确认收货) cancel(买家取消售后)
    private String action;

    private String actorId;         // 操作账号
    private String content;         // 文字（拒绝原因 / 同意说明 / 处理备注；可为空串）
    private LocalDateTime createdAt;// 记录时间
}
