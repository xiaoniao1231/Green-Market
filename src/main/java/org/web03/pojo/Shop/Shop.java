package org.web03.pojo.Shop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 店铺
 */


@Data
@NoArgsConstructor
@AllArgsConstructor
public class Shop {
    private String shopId;              //店铺
    private String name;                //店铺名称
    private String ownerUserId;         //店主用户ID
    private BigDecimal score;           //店铺评分
    private String avatar;              //店铺头像
    private Integer fans;               //店铺粉丝数
    private String intro;               //店铺简介
    private LocalDate founded;          //开店时间
    private LocalDateTime createdAt;    //创建时间
    private LocalDateTime updatedAt;    //更新时间





}
