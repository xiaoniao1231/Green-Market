package org.web03.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.web03.exception.BusinessException;
import org.web03.pojo.PhoneRegisterRequest;
import org.web03.service.SmsVerificationCodeService;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.TimeUnit;
@Slf4j
@Service
public class SmsVerificationCodeImpl implements SmsVerificationCodeService {
    private static final long EXPIRE_SECONDS = 300;  // 验证码5分钟
    private static final long RESEND_INTERVAL_SECONDS = 60; // 1分钟不能从新发送验证码


    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    /** 验证码 key：sms:code:{scene}:{phone} */
    private String codeKey(String phone, String scene) {
        return "sms:code:" + scene + ":" + phone;
    }

    /** 防重发 key：sms:limit:{scene}:{phone} */
    private String limitKey(String phone, String scene) {
        return "sms:limit:" + scene + ":" + phone;
    }

    @Override
    public String sendSmsCode(PhoneRegisterRequest phoneRegisterRequest) {
        String phone = phoneRegisterRequest.getPhone();
        String scene = phoneRegisterRequest.getScene();
        // 防重发：SETNX，key 不存在才设置成功；60 秒内重复发送会被拦截
        Boolean canSend =
                stringRedisTemplate.opsForValue().setIfAbsent(limitKey(phone, scene), "1", Duration.ofSeconds(RESEND_INTERVAL_SECONDS));
        if (Boolean.FALSE.equals(canSend)){
            throw new BusinessException("验证码发送太频繁，请稍后再试");
        }
        // 生成验证码
        String code = String.format("%06d", new Random().nextInt(1000000));
        log.info("发送验证码 {} {}", phone, code);
        // 写入Redis，5分钟后自动过期
        stringRedisTemplate.opsForValue().set(codeKey(phone, scene), code, Duration.ofSeconds(EXPIRE_SECONDS));
        return code;
    }

    @Override
    public boolean verifyCode(String phone, String scene, String code) {
        // key 过期后 GET 返回 null，自动判定为"验证码错误或已过期"
        String cached = stringRedisTemplate.opsForValue().get(codeKey(phone, scene));
        return code != null && code.equals(cached);
    }

    @Override
    public void clearCode(String phone, String scene) {
        // 注册成功后删除验证码，保证一次性使用
        stringRedisTemplate.delete(codeKey(phone, scene));
    }
}
