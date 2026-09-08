package org.web03.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.web03.exception.BusinessException;
import org.web03.pojo.PhoneRegisterRequest;
import org.web03.pojo.Result;
import org.web03.service.SmsVerificationCodeService;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
public class SmsVerificationCode {
    @Autowired
    private SmsVerificationCodeService smsVerificationCodeService;

    @RequestMapping("/sms-code")
    public Result sendSmsCode(@RequestBody PhoneRegisterRequest phoneRegisterRequest) {
        log.info("生成验证码");
        if (phoneRegisterRequest.getPhone() == null || !phoneRegisterRequest.getPhone().matches("^1[3-9]\\d{9}$")) {
            return Result.error("手机号格式不正确");
        }
        if (phoneRegisterRequest.getScene() == null || phoneRegisterRequest.getScene().isEmpty()) {
            phoneRegisterRequest.setScene("register");
        }
        try {
            String code = smsVerificationCodeService.sendSmsCode(phoneRegisterRequest);
            // 演示环境返回验证码便于前端自动填入；接入真实短信通道后 data 留空即可
            Map<String, Object> data = new HashMap<>();
            data.put("smsCode", code);
            data.put("expireSeconds", 300);
            return Result.success(data);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }

    }

}
