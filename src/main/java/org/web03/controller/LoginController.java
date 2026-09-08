package org.web03.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.web03.pojo.LoginInfo;
import org.web03.pojo.PhoneRegisterRequest;
import org.web03.pojo.Result;
import org.web03.pojo.User;
import org.web03.service.LongService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录控制器
 */

@Slf4j
@RestController
@RequestMapping("/login")
public class LoginController {

    @Autowired
    private LongService longService;

    @PostMapping
    public Result login(@RequestBody User user){
        log.info("登入");
        LoginInfo loginInfo = longService.login(user);
        if (loginInfo != null){
            return Result.success(loginInfo);
        }
        return Result.error("用户名或密码错误");
    }

    @PostMapping("/phone")
    public Result longinPhone(@RequestBody PhoneRegisterRequest prr){
        log.info("手机号登入");
        LoginInfo loginInfo = longService.longinPhone(prr);
        if (loginInfo != null) {
            return Result.success(loginInfo);
        }
        return Result.error("该手机号未注册");
    }
}
