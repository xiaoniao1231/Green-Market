package org.web03.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web03.exception.BusinessException;
import org.web03.mapper.EmpMapper;
import org.web03.pojo.PasswordResetRequest;
import org.web03.pojo.User;
import org.web03.service.PasswordResetService;
import org.web03.service.SmsVerificationCodeService;
import org.web03.utils.PasswordUtils;

/**
 * 忘记密码（短信验证码重置）业务层实现
 */
@Slf4j
@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    //重置密码专用的短信验证码场景
    private static final String SCENE = "reset";

    @Autowired
    private EmpMapper empMapper;

    @Autowired
    private SmsVerificationCodeService smsVerificationCodeService;

    //重置密码
    @Override
    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        if (request == null || request.getPhone() == null || !request.getPhone().matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException("手机号格式不正确");
        }
        if (request.getSmsCode() == null || !request.getSmsCode().matches("^\\d{6}$")) {
            throw new BusinessException("验证码格式不正确");
        }
        String password = request.getPassword();
        if (password == null || password.length() < 6 || password.length() > 15) {
            throw new BusinessException("密码长度需为 6-15 位");
        }

        //校验验证码
        if (!smsVerificationCodeService.verifyCode(request.getPhone(), SCENE, request.getSmsCode())) {
            throw new BusinessException("验证码错误或已过期");
        }

        // 按手机号定位账号
        User user = empMapper.findByPhone(request.getPhone());
        if (user == null) {
            throw new BusinessException("该手机号未绑定账号，无法通过短信重置密码");
        }

        // 更新密码
        int rows = empMapper.updatePassword(user.getUserId(), PasswordUtils.encode(password));
        if (rows == 0) {
            throw new BusinessException("密码重置失败，请稍后重试");
        }

        // 验证码一次性使用
        smsVerificationCodeService.clearCode(request.getPhone(), SCENE);
        log.info("账号 {} 已通过短信验证码重置密码", user.getUserId());
    }
}
