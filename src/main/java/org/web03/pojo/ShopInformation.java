package org.web03.pojo;

import lombok.Data;

/**
 * 店铺信息
 */


@Data
public class ShopInformation {

    private String shopId;      // 店铺ID
    private String name;        // 店铺名称
    private String ownerUserId; // 店主用户ID
    private String owner;       // 店主
    private String avatar;      // 店铺头像
    private String intro;       // 店铺简介
    private double score;       // 店铺评分
    private Integer fans;          // 店铺粉丝数
    private String founded;     // 店铺成立时间

}
