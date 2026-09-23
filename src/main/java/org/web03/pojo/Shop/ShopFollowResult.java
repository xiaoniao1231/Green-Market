package org.web03.pojo.Shop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 店铺关注结果
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopFollowResult {

    private String shopId;      // 店铺标识
    private Integer fans;       // 操作后的最新粉丝数
    private Boolean followed;   // 当前登录用户操作后是否关注了该店铺
}
