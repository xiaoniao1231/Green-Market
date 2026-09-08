package org.web03.controller;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.web03.pojo.PhoneRegisterRequest;
import org.web03.pojo.Result;
import org.web03.pojo.User;
import org.web03.service.RegisterService;

/**
 * 注册控制器
 */


@Slf4j
@RestController
@RequestMapping("/register")
public class RegisterController {
    @Autowired
    private RegisterService registerService;

    @RequestMapping
    public Result register(@RequestBody User user) {
        log.info("注册用户");
        registerService.register(user);
        return Result.success();
    }

    @RequestMapping("/phone")
    public Result phoneRegister(@RequestBody PhoneRegisterRequest prr){
        log.info("手机号注册");
        registerService.phoneRegister(prr);
        return Result.success();
    }


}
