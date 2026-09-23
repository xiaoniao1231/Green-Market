package org.web03.pojo.Shop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 店铺信息
 */


@Data
@NoArgsConstructor
@AllArgsConstructor
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
    private Boolean followed;   // 当前登录用户是否已关注该店铺（仅公开店铺档案接口下发；本地关注态只是镜像）

    public ShopInformation(String shopId, String name, String ownerUserId, String nicknameByUserId, String avatar, String intro, BigDecimal score, Integer fans, String founded) {
        this.shopId = shopId;
        this.name = name;
        this.ownerUserId = ownerUserId;
        this.owner = nicknameByUserId;
        this.avatar = avatar;
        this.intro = intro;
        this.score = score.doubleValue();
        this.fans = fans;
        this.founded = founded;
    }
}
