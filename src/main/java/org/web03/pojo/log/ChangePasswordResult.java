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
    /* 恒为 null：改密成功后服务端**不再签发新令牌**（用户需重新登录），
       前端 api.js 据此约定「token 为空 → 清除登录态并引导重新登录」。 */
    private String token;
    private Integer pwdVersion; // 密码版本号，自增后此前签发的令牌全部失效
}
