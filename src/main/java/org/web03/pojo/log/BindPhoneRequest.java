package org.web03.pojo.log;


import lombok.Data;

/**
 * 绑定 / 换绑手机号请求体
 */
@Data
public class BindPhoneRequest {

    private String phone;   // 11 位新手机号
    private String smsCode; // 6 位短信验证码
    private String password; // 当前登录密码
}
