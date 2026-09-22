package org.web03.service.impl;

import org.web03.exception.BusinessException;
import org.web03.mapper.EmpMapper;
import org.web03.mapper.ShopMapper;
import org.web03.pojo.LoginInfo;
import org.web03.pojo.PhoneRegisterRequest;
import org.web03.pojo.User;
import org.web03.service.LongService;
import org.web03.service.SmsVerificationCodeService;
import org.web03.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;

/**
 * 长连接服务实现类
 */

@Service
public class LongServiceImpl implements LongService {

    @Autowired
    private EmpMapper empMapper;

    @Autowired
    private SmsVerificationCodeService smsVerificationCodeService;

    @Autowired
    private ShopMapper shopMapper;

    //    手机号登录
    @Override
    public LoginInfo longinPhone(PhoneRegisterRequest prr) {
        if (prr.getPhone() == null || !prr.getPhone().matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException("手机号格式不正确");
        }
        if (prr.getSmsCode() == null || !prr.getSmsCode().matches("^\\d{6}$")) {
            throw new BusinessException("验证码格式不正确");
        }
        // 校验验证码
        if (!smsVerificationCodeService.verifyCode(prr.getPhone(), "login", prr.getSmsCode())) {
            throw new BusinessException("验证码错误或已过期");
        }
        // 验证码校验通过后立即清除，保证一次性使用
        smsVerificationCodeService.clearCode(prr.getPhone(), "login");
        User phone = empMapper.longinPhone(prr);
        return getLoginInfo(phone);
    }

    //    用户名密码登录
    @Override
    public LoginInfo login(User user) {
        User login = empMapper.login(user);
        return getLoginInfo(login);
    }

    private LoginInfo getLoginInfo(User login) {
        if (login != null) {
            //    创建JWT令牌
            HashMap<String, Object> claims = new HashMap<>();
            claims.put("id", login.getId());
            claims.put("userId", login.getUserId());
            String jwt = JwtUtils.generateToken(claims);

            String shopId = shopMapper.findShopIdByOwner(login.getUserId());
            return new LoginInfo(login.getId(), login.getUserId(), login.getNickname(), jwt, shopId);
        }
        return null;
    }

}



