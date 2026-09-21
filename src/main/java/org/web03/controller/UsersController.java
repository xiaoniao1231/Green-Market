package org.web03.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.web03.exception.BusinessException;
import org.web03.mapper.EmpMapper;
import org.web03.pojo.Result;
import org.web03.pojo.User;
import org.web03.utils.AliyunOSSOperator;
import org.web03.utils.CurrentHolder;
import org.web03.websocket.ChatWebSocketHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private AliyunOSSOperator aliyunOSSOperator;
    @Autowired
    private EmpMapper empMapper;


    //获取在线用户列表
    @GetMapping("/online")
    public Result online() {
        List<String> users = new ArrayList<>(chatWebSocketHandler.onlineUsers());
        HashMap<String, Object> data = new HashMap<>();
        data.put("onlineCount", users.size());
        data.put("onlineUsers", users);
        return Result.success(data);
    }

    //上传头像
    @PostMapping("/avatar")
    public Result uploadAvatar(@RequestParam("file") MultipartFile file){
        String myUserId = CurrentHolder.getCurrentUserId();
        if (myUserId == null)return Result.error("用户未登录");
        if (file == null)return Result.error("请上传文件");
        if(file.getSize() > 10*1024*1024)return Result.error("文件大小不能超过 10MB");

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/"))return Result.error("请上传图片文件");
        try {
            String url = aliyunOSSOperator.upload(file.getBytes(),file.getOriginalFilename());
            Map<String,Object> data = new HashMap<>();
            data.put("url", url);
            return Result.success(data);
        } catch (BusinessException e) {
            /* 凭证缺失等可读原因直接透出，便于前端提示与排查 */
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("上传头像失败: {}", e.getMessage(), e);
            return Result.error("上传失败：" + e.getMessage());
        }
    }


    //修改用户信息
    @PutMapping("/profile")
    public Result updateProfile(@RequestBody User user) {
        String myUserId = CurrentHolder.getCurrentUserId();
        if (myUserId == null)return Result.error("用户未登录");
        if (user.getNickname() != null && user.getNickname().trim().isEmpty())return Result.error("用户昵称不能为空");
        user.setUserId(myUserId);
        empMapper.updateProfile(user);
        User updated = empMapper.findByUserId(myUserId);
        if (updated == null) return Result.error("用户不存在");
        updated.setPassword(null);   // 返回体不携带密码
        return Result.success(updated);
    }

}
