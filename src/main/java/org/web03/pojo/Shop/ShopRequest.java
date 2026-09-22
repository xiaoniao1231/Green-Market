package org.web03.pojo.Shop;

import lombok.Data;

/**
 * 店铺请求参数
 */


@Data
public class ShopRequest {
    private String name;        // 店铺名称
    private String avatar;      // 店铺头像
    private String intro;       // 店铺简介
}
