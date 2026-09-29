package org.web03.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.web03.exception.BusinessException;
import org.web03.mapper.EmpMapper;
import org.web03.pojo.Result;
import org.web03.pojo.User;
import org.web03.pojo.log.BindPhoneRequest;
import org.web03.pojo.log.ChangePasswordRequest;
import org.web03.service.UserAccountService;
import org.web03.websocket.ChatWebSocketHandler;

import java.util.*;

/**
 * 用户与在线状态
 */
@Slf4j
@RestController
@RequestMapping("/users")
public class UsersController {

    @Autowired
    private ChatWebSocketHandler chatWebSocketHandler;
    @Autowired
    private UserAccountService userAccountService;


    //获取在线用户列表
    @GetMapping("/online")
    public Result online() {
        List<String> users = new ArrayList<>(chatWebSocketHandler.onlineUsers());
        List<String> away = new ArrayList<>(chatWebSocketHandler.awayUsers());
        HashMap<String, Object> data = new HashMap<>();
        data.put("onlineCount", users.size());
        data.put("onlineUsers", users);
        data.put("awayCount", away.size());
        data.put("awayUsers", away);
        // 账号 → ONLINE / AWAY，前端一次拿全状态，不必自己拼两个集合
        data.put("userStatus", chatWebSocketHandler.userStatusMap());
        return Result.success(data);
    }

    //上传头像
    @PostMapping("/avatar")
    public Result uploadAvatar(@RequestParam(value = "file", required = false) MultipartFile file){
        return Result.success(userAccountService.uploadAvatar(file));
    }


    //当前登录账号的完整资料
    @GetMapping("/me")
    public Result me() {
        return Result.success(userAccountService.me());
    }

    //修改用户信息
    @PutMapping("/profile")
    public Result updateProfile(@RequestBody User user) {
        userAccountService.updateProfile(user);
        return Result.success();
    }

    // 绑定 / 换绑手机号
    @PostMapping("/phone")
    public Result bindPhone(@RequestBody BindPhoneRequest request) {
        userAccountService.bindPhone(request);
        return Result.success();
    }

    // 修改密码
    @PutMapping("/password")
    public Result changePassword(@RequestBody ChangePasswordRequest request) {
        return Result.success(userAccountService.changePassword(request));
    }


}
