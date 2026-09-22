package org.web03.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.web03.pojo.Messages.MessagesFile;
import org.web03.pojo.Messages.PrivateMessageRequest;
import org.web03.pojo.Result;
import org.web03.pojo.Messages.SendPrivateResult;
import org.web03.service.MessageService;
import org.web03.utils.CurrentHolder;

import java.util.Map;

/**
 * 消息控制器
 */

@Slf4j
@RestController
@RequestMapping("/messages")
public class  MessagesController {

    @Autowired
    private MessageService messageService;

    // 发送私聊消息
    @PostMapping("/private")
    public Result sendPrivate(@RequestBody PrivateMessageRequest req){
        SendPrivateResult date = messageService.sendPrivate(CurrentHolder.getCurrentUserId(),req);
        return Result.success(date);
    }

    //文件传输
    @PostMapping("/file")
    public Result sendFile(@RequestParam("file") MultipartFile file,
                           @RequestParam("receiverId") String receiverId)
    {
        MessagesFile data = messageService.sendFile(CurrentHolder.getCurrentUserId(), file, receiverId);
        return Result.success(data);
    }

    // 获取私聊消息历史
    @GetMapping("/history")
    public Result history(@RequestParam(required = false) String peerId,
                          @RequestParam(defaultValue = "1") Integer page,
                          @RequestParam(defaultValue = "10") Integer size){
        return Result.success(messageService.history(CurrentHolder.getCurrentUserId(), peerId, page, size));
    }

    //查询我的会话列表
    @GetMapping("/conversations")
    public Result conversations(){
        return Result.success(messageService.conversations(CurrentHolder.getCurrentUserId()));
    }

    // 标记会话已读：POST /messages/read，body { peerId }
    @PostMapping("/read")
    public Result markRead(@RequestBody Map<String, String> body) {
        String peerId = body == null ? null : body.get("peerId");
        if (!StringUtils.hasLength(peerId)) {
            return Result.error("对端账号不能为空");
        }
        messageService.markRead(CurrentHolder.getCurrentUserId(), peerId);
        return Result.success(null);
    }

}
