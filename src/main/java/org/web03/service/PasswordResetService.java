package org.web03.service;

import org.web03.pojo.PasswordResetRequest;

/**
 * 忘记密码（短信验证码重置）业务接口
 */
public interface PasswordResetService {

    void resetPassword(PasswordResetRequest request);
}
