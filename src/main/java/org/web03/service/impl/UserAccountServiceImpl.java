package org.web03.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.web03.exception.BusinessException;
import org.web03.mapper.EmpMapper;
import org.web03.pojo.Result;
import org.web03.pojo.User;
import org.web03.pojo.log.AccountLog;
import org.web03.pojo.log.BindPhoneRequest;
import org.web03.pojo.log.ChangePasswordRequest;
import org.web03.pojo.log.ChangePasswordResult;
import org.web03.service.SmsVerificationCodeService;
import org.web03.service.UserAccountService;
import org.web03.utils.AliyunOSSOperator;
import org.web03.utils.CurrentHolder;
import org.web03.utils.JwtUtils;
import org.web03.utils.PasswordUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 账户设置业务实现
 */

@Slf4j
@Service
public class UserAccountServiceImpl implements UserAccountService {

    @Autowired
    private SmsVerificationCodeService smsVerificationCodeService;
    @Autowired
    private EmpMapper empMapper;
    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    //性别枚举
    private static final List<String> GENDERS = List.of("male", "female", "secret");

    // 上传头像
    @Override
    public Map<String, Object> uploadAvatar(MultipartFile file) {
        currentUser();   // 未登录直接抛「未登录或登录已失效」，账号由令牌决定
        if (file == null || file.isEmpty()) throw new BusinessException("请上传文件");
        if(file.getSize() > 100*1024*1024) throw new BusinessException("文件大小不能超过 100MB");

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) throw new BusinessException("请上传图片文件");
        try {
            String filename = file.getOriginalFilename();
            if (filename == null || filename.isBlank()) filename = "avatar-" + System.currentTimeMillis() + ".png";
            String url = aliyunOSSOperator.upload(file.getBytes(), filename);
            Map<String,Object> data = new HashMap<>();
            data.put("url", url);
            return data;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("上传头像失败: {}", e.getMessage(), e);
            throw new BusinessException("上传失败：" + e.getMessage());
        }
    }

    // 更新用户资料
    @Override
    public void updateProfile(User user) {
        String myUserId = currentUser();
        if (myUserId == null) throw new BusinessException ("用户未登录");
        if (user == null) throw new BusinessException("参数不能为空");
        if (user.getNickname() != null) {
            String nickname = user.getNickname().trim();
            if (nickname.isEmpty()) throw new BusinessException("用户昵称不能为空");
            if (nickname.length() > 30) throw new BusinessException("昵称最长 30 字");
            user.setNickname(nickname);
        }
        if (user.getGender() != null && !GENDERS.contains(user.getGender()))
            throw new BusinessException("性别参数不合法");
        if (user.getSignature() != null && user.getSignature().length() > 40)
            throw new BusinessException("个性签名最长 40 字");
        if (user.getAvatar() != null && user.getAvatar().length() > 500)
            throw new BusinessException("头像地址过长");

        user.setUserId(myUserId);
        empMapper.updateProfile(user);
    }

    // 获取当前用户信息
    @Override
    public User me() {
        String myUserId = currentUser();
        if (myUserId == null) throw new BusinessException("用户未登录");
        User me = empMapper.findByUserId(myUserId);
        if (me == null) throw new BusinessException("用户不存在");
        me.setPassword(null);
        me.setPwdVersion(null);
        me.setPhoneBound(me.getPhoneNumber() != null && !me.getPhoneNumber().trim().isEmpty());
        return me;
    }

    // 绑定 / 换绑手机号
    @Override
    @Transactional
    public void bindPhone(BindPhoneRequest request) {
        if (request == null) throw new BusinessException("参数不能为空");
        String phone = request.getPhone() == null ? "" : request.getPhone().trim();
        String smsCode = request.getSmsCode() == null ? "" : request.getSmsCode().trim();
        if (!phone.matches("^1[3-9]\\d{9}$")) throw new BusinessException("手机号格式不正确");
        if (!smsCode.matches("^\\d{6}$")) throw new BusinessException("验证码格式不正确");

        String userId = currentUser();
        User user = empMapper.findByUserId(userId);
        if (user == null) throw new BusinessException("用户不存在");
        String currentPhone = user.getPhoneNumber() == null ? "" : user.getPhoneNumber().trim();
        boolean alreadyBound = !currentPhone.isEmpty();
        if (alreadyBound && phone.equals(currentPhone)) throw new BusinessException("新手机号与当前绑定手机号相同");
        if (!smsVerificationCodeService.verifyCode(phone, "bind", smsCode))
            throw new BusinessException("验证码错误或已过期");
        if(empMapper.countPhoneBoundByOthers(phone, userId) > 0) throw new BusinessException("该手机号已被其他账号绑定");
        if(alreadyBound && !passwordMatches(request.getPassword(), user.getPassword()))
            throw new BusinessException("当前密码不正确");
        if (empMapper.updatePhone(userId, phone) == 0) throw new BusinessException("手机号绑定失败，请稍后重试");

        // 记录操作日志
        String action = alreadyBound ? "change_phone" : "bind_phone";
        empMapper.insertLog(buildLog(userId, action,
                (alreadyBound ? "换绑手机号 " + mask(currentPhone) + " → " : "绑定手机号 ") + mask(phone)));
        // 清除验证码
        smsVerificationCodeService.clearCode(phone, "bind");

        log.info("账号 {} {} 成功：{}", userId, alreadyBound ? "换绑手机号" : "绑定手机号", mask(phone));

    }

    // 修改登录密码
    @Override
    @Transactional
    public ChangePasswordResult changePassword(ChangePasswordRequest request) {
        if (request == null) throw new BusinessException("参数不能为空");
        String userId = currentUser();
        String oldPassword = request.getOldPassword();
        String newPassword = request.getNewPassword();
        if (oldPassword == null || oldPassword.isEmpty()) throw new BusinessException("请输入当前密码");
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 15) {
            throw new BusinessException("新密码长度需为 6-15 位");
        }

        User user = empMapper.findByUserId(userId);
        if (user == null) throw new BusinessException("用户不存在");
        if (!passwordMatches(oldPassword, user.getPassword())) throw new BusinessException("当前密码不正确");
        if (passwordMatches(newPassword, user.getPassword())) throw new BusinessException("新密码不能与当前密码相同");

        // 更新密码
        if (empMapper.updatePassword(userId, PasswordUtils.encode(newPassword)) == 0) {
            throw new BusinessException("密码修改失败，请稍后重试");
        }
        empMapper.insertLog(buildLog(userId, "change_password", "修改登录密码"));
// 重新签发令牌
/*
        Integer pwdVersion = empMapper.findPwdVersion(userId);
        if (pwdVersion == null) {
            pwdVersion = 0;
        }
        HashMap<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("userId", user.getUserId());
        claims.put("pv", pwdVersion);
        String token = JwtUtils.generateToken(claims);

        log.info("账号 {} 修改密码成功（pwd_version={}），已重签发令牌", userId, pwdVersion);
        return new ChangePasswordResult(token, pwdVersion);*/

        // 不重新签发令牌,用户改完需重新登入
        Integer pwdVersion = empMapper.findPwdVersion(userId);
        if (pwdVersion == null) {
            pwdVersion = 0;
        }
        log.info("账号 {} 修改密码成功", userId);
        return new ChangePasswordResult(null, pwdVersion);
    }

    //获取当前用户
    private String currentUser() {
        String userId = CurrentHolder.getCurrentUserId();
        if (!StringUtils.hasLength(userId)) throw new BusinessException("未登录或登录已失效");
        return userId;
    }

    // 密码匹配
    private boolean passwordMatches(String raw, String stored) {
        if (raw == null || raw.isEmpty() || stored == null || stored.isEmpty()) {
            return false;
        }
        return PasswordUtils.isEncoded(stored)
                ? PasswordUtils.matches(raw, stored)
                : PasswordUtils.matchesPlaintext(raw, stored);
    }

    //手机号脱敏：13800138000 → 138****8000
    private String mask(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    //组装一条操作日志
    private AccountLog buildLog(String userId, String action, String detail) {
        AccountLog accountLog = new AccountLog();
        accountLog.setUserId(userId);
        accountLog.setAction(action);
        accountLog.setDetail(detail == null ? "" : (detail.length() > 200 ? detail.substring(0, 200) : detail));
        accountLog.setIp(clientIp());
        return accountLog;
    }


    /**
     * 客户端 IP：优先 X-Forwarded-For 的第一段（经 nginx 反代时的真实来源），
     * 其次 RemoteAddr；取不到返回 null
     */
    private String clientIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
