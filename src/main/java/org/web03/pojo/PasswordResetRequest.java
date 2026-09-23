package org.web03.pojo;

import lombok.Data;

/**
 * 忘记密码 · 短信验证码重置请求体
 */

@Data
public class PasswordResetRequest {
    private String phone;    // 11 位手机号（必须已绑定到某个账号的 users.phone_number）
    private String smsCode;  // 6 位短信验证码（scene = reset）
    private String password; // 新密码（6-15 位，入库前做 BCrypt 哈希）
}
