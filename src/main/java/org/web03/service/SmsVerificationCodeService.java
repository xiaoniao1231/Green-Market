package org.web03.service;

import org.web03.pojo.PhoneRegisterRequest;

public interface SmsVerificationCodeService {
    String sendSmsCode(PhoneRegisterRequest phoneRegisterRequest);

    boolean verifyCode(String phone, String scene, String code);

    void clearCode(String phone, String scene);
}
