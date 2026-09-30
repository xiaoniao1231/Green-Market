package org.web03.pojo.AfterSale;


import lombok.Data;

/**
 * 售后时间线条目
 */
@Data
public class AfterSaleLogVO {
    private String role;        // 操作者:buyer 买家 / seller 卖家 / system 系统
    //操作:apply(买家发起售后申请) approve(卖家同意处理) refuse(卖家拒绝处理) ship(卖家发货) receive(买家确认收货) cancel(买家取消售后)
    private String action;

    private String content;     // 文字内容
    private String time;        // 操作时间
}