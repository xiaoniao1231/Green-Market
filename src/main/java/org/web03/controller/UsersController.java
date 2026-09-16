package org.web03.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.web03.pojo.Result;
import org.web03.websocket.ChatWebSocketHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 用户与在线状态
 */
@Slf4j
@RestController
@RequestMapping("/users")
public class UsersController {

    @Autowired
    private ChatWebSocketHandler chatWebSocketHandler;


    //获取在线用户列表
    @GetMapping("/online")
    public Result online() {
        List<String> users = new ArrayList<>(chatWebSocketHandler.onlineUsers());
        HashMap<String, Object> data = new HashMap<>();
        data.put("onlineCount", users.size());
        data.put("onlineUsers", users);
        return Result.success(data);
    }
}
