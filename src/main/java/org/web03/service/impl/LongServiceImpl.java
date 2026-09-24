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
import org.web03.utils.PasswordUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;

/**
 * 长连接服务实现类
 */

@Slf4j
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
        if (user == null || !StringUtils.hasLength(user.getUserId()) || !StringUtils.hasLength(user.getPassword())) {
            return null;
        }
        /* 密码不参与 SQL 比对：先按账号取出用户，再在 Service 层校验。
           账号不存在与密码错误一律返回 null，由控制器统一回「用户名或密码错误」，
           避免错误提示泄露「该账号是否已注册」。 */
        User login = empMapper.login(user);
        if (login == null) {
            return null;
        }
        String stored = login.getPassword();
        if (PasswordUtils.isEncoded(stored)) {
            // 正常路径：库中是 BCrypt 哈希，用自带随机盐的恒定时间比对校验
            if (!PasswordUtils.matches(user.getPassword(), stored)) {
                return null;
            }
        } else {
            /* 历史数据兼容：老库里是明文（不是 BCrypt 串），按明文比对一次；
               通过后立刻升级为哈希，明文随即消失，下次登录就走上面的 BCrypt 路径。
               这里用 updatePasswordOnly：这是存储格式升级、不是用户改密，
               不该顺带递增密码版本号（否则会把该账号在其它设备上的登录态一起踢掉）。 */
            if (!PasswordUtils.matchesPlaintext(user.getPassword(), stored)) {
                return null;
            }
            empMapper.updatePasswordOnly(login.getUserId(), PasswordUtils.encode(user.getPassword()));
            log.info("账号 {} 的历史明文密码已升级为 BCrypt 哈希", login.getUserId());
        }
        return getLoginInfo(login);
    }

    //    获取登录信息
    private LoginInfo getLoginInfo(User login) {
        if (login != null) {
            //    创建JWT令牌
            HashMap<String, Object> claims = new HashMap<>();
            claims.put("id", login.getId());
            claims.put("userId", login.getUserId());
            /* pv = 密码版本号：TokenFilter 每次带令牌请求都比对 token.pv 与库中当前值，
               密码一旦被改 / 被重置（版本号 +1），此前签发的 token 立刻失效。
               旧格式 token 没有 pv 声明，TokenFilter 按 0 处理 ——
               从未改过密码的账号（pwd_version = 0）不会因此被要求重新登录。 */
            claims.put("pv", login.getPwdVersion() == null ? 0 : login.getPwdVersion());
            String jwt = JwtUtils.generateToken(claims);

            String shopId = shopMapper.findShopIdByOwner(login.getUserId());
            return new LoginInfo(login.getId(), login.getUserId(), login.getNickname(), jwt, shopId);
        }
        return null;
    }

}



