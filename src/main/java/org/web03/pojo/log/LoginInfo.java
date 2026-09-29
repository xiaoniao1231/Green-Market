package org.web03.pojo.log;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 登录信息
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginInfo {
    private Integer id;
    private String username; //用户名
    private String name; //姓名
    private String token; //令牌
    private String shopId; //店铺ID
    private String gender; //性别：male 男 / female 女 / secret 保密
    private String avatar; //头像（阿里云 OSS 地址；历史数据为 emoji 字符）
    private String signature; //个性签名
}
