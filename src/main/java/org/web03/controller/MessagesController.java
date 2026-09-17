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
import org.web03.pojo.MessagesFile;
import org.web03.pojo.PrivateMessageRequest;
import org.web03.pojo.Result;
import org.web03.pojo.SendPrivateResult;
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
/*        if (req == null || !StringUtils.hasLength(req.getReceiverId())) {
            return Result.error("接收方账号不能为空");
        }
        if (!StringUtils.hasLength(req.getContent())) {//判断前端传过来的消息内容是否为空
            return Result.error("消息内容为空");
        }*/
        SendPrivateResult date = messageService.sendPrivate(CurrentHolder.getCurrentUserId(),req);
        return Result.success(date);
    }

    //文件传输
    @PostMapping("/file")
    public Result sendFile(@RequestParam("file") MultipartFile file,
                           @RequestParam("receiverId") String receiverId)
    {
/*        if (file == null || file.isEmpty()) {
            return Result.error("上传文件不能为空");
        }*/
        MessagesFile data = messageService.sendFile(CurrentHolder.getCurrentUserId(), file, receiverId);
        return Result.success(data);
    }

    // 获取私聊消息历史
    @GetMapping("/history")
    public Result history(@RequestParam(required = false) String peerId,
                          @RequestParam(defaultValue = "1") Integer page,
                          @RequestParam(defaultValue = "10") Integer size){
/*        if (!StringUtils.hasLength(peeerId)) {//判断前端传过来的 peerId 是否为空
            return Result.error("接收方账号不能为空");
        }*/
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
