package org.web03.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.web03.pojo.PasswordResetRequest;
import org.web03.pojo.Result;
import org.web03.service.PasswordResetService;

/**
 * 忘记密码（短信验证码重置）
 */
@Slf4j
@RestController
@RequestMapping("/password")
public class PasswordResetController {

    @Autowired
    private PasswordResetService passwordResetService;

    //重置密码
    @PostMapping("/reset")
    public Result reset(@RequestBody PasswordResetRequest request) {
        passwordResetService.resetPassword(request);
        return Result.success();
    }
}
