package org.web03.pojo.Address;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 收货地址请求参数
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddressRequest {
    private String name;        // 收货人
    private String phone;       // 手机号
    private String region;      // 区域
    private String detail;      // 详细地址
    private String tag;         // 标签
    private Boolean isDefault; // 是否默认地址
}
