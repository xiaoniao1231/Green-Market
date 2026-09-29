package org.web03.pojo.log;

import lombok.Data;

/**
 * 忘记密码 · 短信验证码重置请求体
 */

@Data
public class PasswordResetRequest {
    private String phone;    // 11 位手机号
    private String smsCode;  // 6 位短信验证码
    private String password; // 新密码
}
