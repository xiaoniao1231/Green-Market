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
import org.web03.exception.BusinessException;
import org.web03.mapper.EmpMapper;
import org.web03.mapper.ProductMapper;
import org.web03.pojo.Messages.MessagesFile;
import org.web03.pojo.Messages.PrivateMessageRequest;
import org.web03.pojo.Product.Product;
import org.web03.pojo.Result;
import org.web03.pojo.Messages.SendPrivateResult;
import org.web03.service.MessageService;
import org.web03.utils.CurrentHolder;
import org.web03.utils.JsonUtils;

import java.util.LinkedHashMap;
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
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private EmpMapper empMapper;

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

    // 标记会话已读
    @PostMapping("/read")
    public Result markRead(@RequestBody Map<String, String> body) {
        String peerId = body == null ? null : body.get("peerId");
        if (!StringUtils.hasLength(peerId)) {
            return Result.error("对端账号不能为空");
        }
        messageService.markRead(CurrentHolder.getCurrentUserId(), peerId);
        return Result.success(null);
    }

    //发送商品卡片
    @PostMapping("/goods")
    public Result sendGoods(@RequestBody Map<String, Object> body) {
        if (body == null) throw new BusinessException("消息参数无效");
        Integer productId = parseId(body.get("productId"));
        if (productId == null) throw new BusinessException("商品参数无效");
        Object receiverObj = body.get("receiverId");
        String receiverId = receiverObj == null ? null : String.valueOf(receiverObj).trim();
        if (!StringUtils.hasLength(receiverId)) throw new BusinessException("接收方账号不能为空");
        if (empMapper.countByUserId(receiverId) == 0) throw new BusinessException("接收方账号不存在");
        /* 取商品快照用 getById（未删除即可）而不是 getPublicById：
           已下架商品也允许分享 —— 卡片按快照展示，前端点开详情时再提示已下架（接口文档 4.4） */
        Product p = productMapper.getById(productId);
        if (p == null) throw new BusinessException("商品不存在");
        Map<String, Object> biz = new LinkedHashMap<>();
        biz.put("productId", p.getId());
        biz.put("title", p.getTitle());
        biz.put("price", p.getPrice());
        biz.put("artImg", JsonUtils.buildArt(p.getSkus()));
        return Result.success(messageService.sendBiz(receiverId, "GOODS_MES", p.getTitle(), biz));
    }

    //请求体里的整数参数：缺失 / 空白 / 非数字统一返回 null，由调用方给出业务提示（不抛 500）
    private Integer parseId(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        if (!StringUtils.hasLength(s)) return null;
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
