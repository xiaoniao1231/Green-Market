package org.web03.pojo.log;

/**
 * 修改密码返回体
 */


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordResult {
    private String token;       //  重新签发的 JWT
    private Integer pwdVersion; // 密码版本号
}
