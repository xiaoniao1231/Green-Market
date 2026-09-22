package org.web03.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 收货地址
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Address {
    private Integer id;                 //地址id
    private String userId;             //用户id
    private String name;               //收货人
    private String phone;              //手机号
    private String region;             //区域
    private String detail;             //详细地址
    private String tag;                //标签
    private Boolean isDefault;         //是否默认地址
    private LocalDateTime createdAt;   //创建时间
    private LocalDateTime updatedAt;   //更新时间

}
