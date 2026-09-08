package org.web03.service;

import org.web03.pojo.PhoneRegisterRequest;

public interface SmsVerificationCodeService {
    String sendSmsCode(PhoneRegisterRequest phoneRegisterRequest);

    /** 校验验证码：正确且 5 分钟内有效 */
    boolean verifyCode(String phone, String scene, String code);

    /** 校验通过后清除验证码（一次性使用） */
    void clearCode(String phone, String scene);
}
