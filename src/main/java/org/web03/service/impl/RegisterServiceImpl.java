package org.web03.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web03.exception.BusinessException;
import org.web03.mapper.RegisterMapper;
import org.web03.pojo.PhoneRegisterRequest;
import org.web03.pojo.User;
import org.web03.service.RegisterService;
import org.web03.service.SmsVerificationCodeService;

import java.time.LocalDateTime;

@Service
public class RegisterServiceImpl implements RegisterService {

    @Autowired
    private RegisterMapper registerMapper;
    @Autowired
    private SmsVerificationCodeService smsVerificationCodeService;

    @Override
    public void phoneRegister(PhoneRegisterRequest prr) {
        // 参数校验
        if (prr.getPhone() == null || !prr.getPhone().matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException("手机号格式不正确");
        }
        if (prr.getSmsCode() == null || !prr.getSmsCode().matches("^\\d{6}$")) {
            throw new BusinessException("验证码格式不正确");
        }
        if (prr.getPassword() == null || prr.getPassword().length() < 6 || prr.getPassword().length() > 15) {
            throw new BusinessException("密码长度需为 6-15 位");
        }
        if (prr.getNickname() == null || prr.getNickname().trim().isEmpty() || prr.getNickname().length() > 30) {
            throw new BusinessException("昵称不能为空且最长 30 位");
        }
        /* 校验验证码在前：验证码在成功注册后会被清除，因此"已注册号码 + 旧验证码"的重放
           会先被判为验证码错误，不会暴露该号码是否已注册（避免手机号枚举） */
        if (!smsVerificationCodeService.verifyCode(prr.getPhone(), "register", prr.getSmsCode())) {
            throw new BusinessException("验证码错误或已过期");
        }
        // 手机号查重
        if (registerMapper.existsByPhone(prr.getPhone()) > 0) {
            throw new BusinessException("手机号已被注册");
        }
        User user = new User();
        user.setUserId(prr.getPhone());
        user.setPhoneNumber(prr.getPhone());
        user.setPassword(prr.getPassword());
        user.setNickname(prr.getNickname());
        user.setCreatedAt(LocalDateTime.now());
        registerMapper.registerByPhone(user);

        // 5. 验证码一次性使用
        smsVerificationCodeService.clearCode(prr.getPhone(), "register");
    }

    @Override
    public void register(User user) {
        // 参数校验
        if (user.getUserId() == null || user.getUserId().trim().isEmpty() || user.getUserId().length() > 15) {
            throw new BusinessException("账号不能为空且最长 15 位");
        }
        if (user.getPassword() == null || user.getPassword().length() < 6 || user.getPassword().length() > 15) {
            throw new BusinessException("密码长度需为 6-15 位");
        }
        if (user.getNickname() == null || user.getNickname().trim().isEmpty() || user.getNickname().length() > 30) {
            throw new BusinessException("昵称不能为空且最长 30 位");
        }
        // 账号查重
        if (registerMapper.existsByUserId(user.getUserId()) > 0) {
            throw new BusinessException("账号已存在");
        }
        user.setCreatedAt(LocalDateTime.now());
        registerMapper.register(user);
    }
}
