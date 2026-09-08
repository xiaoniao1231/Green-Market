package org.web03.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 手机号注册请求体
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PhoneRegisterRequest {
    private String phone;    // 11 位数字手机号
    private String smsCode;  // 6 位短信验证码
    private String password; // 登录密码，最长 15 位
    private String nickname; // 用户昵称，最长 30 位
    private String scene;  // 注册场景，最长 10 位
}
