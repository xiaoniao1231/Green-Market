package org.web03.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.web03.exception.BusinessException;
import org.web03.mapper.EmpMapper;
import org.web03.mapper.MessageMapper;
import org.web03.pojo.*;
//import org.web03.pojo.HistoryResult;
import org.web03.service.MessageService;
import org.web03.utils.AliyunOSSOperator;
import org.web03.websocket.ChatWebSocketHandler;
import org.web03.websocket.WsHandshakeInterceptor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Slf4j
@Service
public class MessageServiceImpl implements MessageService {

    @Autowired
    private EmpMapper empMapper;
    @Autowired
    private MessageMapper messageMapper;
    @Autowired
    private ChatWebSocketHandler chatWebSocketHandler;
    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    /** 文本消息长度上限：最长 10000 字 */
    private static final int MAX_CONTENT_LENGTH = 10000;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 文件消息大小上限：1 GB */
    private static final long MAX_FILE_SIZE = 1024L * 1024 * 1024;

    /** 发送私聊消息 */
    @Override
    public SendPrivateResult sendPrivate(String myUserId, PrivateMessageRequest req) {
        requireSender(myUserId);
        String receiverId = req.getReceiverId() == null ? "" : req.getReceiverId().trim();
        String content = req.getContent() == null ? "" : req.getContent();
        /* 空内容拦截：原先只校验长度，空串会被当成一条空消息入库（前端虽已拦截，但接口层要兜住） */
        if (!StringUtils.hasLength(content.trim())) {
            throw new BusinessException("消息内容不能为空");
        }
        if(content.length() > MAX_CONTENT_LENGTH){
            throw new BusinessException("消息内容最长 " + MAX_CONTENT_LENGTH + " 字");
        }
        checkReceiver(myUserId, receiverId);
        //将消息保存到数据库
        Messages m = new Messages();
        m.setMsgId(StringUtils.hasLength(req.getMsgId()) ? req.getMsgId().trim() : UUID.randomUUID().toString());
        m.setSenderId(myUserId);
        m.setReceiverId(receiverId);
        m.setMsgType("COMM_MES");
        m.setContent(content);
        m.setSendTime(LocalDateTime.now());
        m.setRecalled(false);

        //检查消息是否已存在（客户端重试 / 双击发送会带同一个 msgId，命中即返回既有记录，
        //否则会撞 msg_id 唯一键，被全局异常处理器翻译成「账号或手机号已存在」这种误导提示）
        Messages exist = messageMapper.selectByMsgId(m.getMsgId());
        if(exist != null){
            log.info("消息已存在，返回既有记录");
            return new SendPrivateResult(exist.getMsgId(),
                    TIME_FORMATTER.format(exist.getSendTime()),
                    chatWebSocketHandler.isOnline(exist.getReceiverId()));
        }

        //保存消息到数据库
        messageMapper.insert(m);
        String sendTime = TIME_FORMATTER.format(m.getSendTime());

        // 尝试实时送达，如果失败则记录日志并提示用户
        boolean delivered = pushTo(receiverId,"COMM_MES",
                new WsMessage(m.getMsgId(), myUserId, empMapper.findNicknameByUserId(myUserId),
                        receiverId, content, sendTime));
        if(!delivered){
            log.warn("用户 [{}] 未实时送达，消息 [{}] 已入库待其上线拉取", receiverId, m.getMsgId());
            chatWebSocketHandler.pushTo(myUserId,"SYSTEM",new WsMessage("","系统","系统",myUserId,"用户 " + receiverId + " 不在线，消息未送达", sendTime));
        }
        return new SendPrivateResult(m.getMsgId(), sendTime, delivered);
    }

    /** 发送文件消息 */
    @Override
    public MessagesFile sendFile(String myUserId, MultipartFile file, String receiverId) {
        requireSender(myUserId);
        if (file == null || file.isEmpty()){
            throw new BusinessException("文件不能为空");
        }
        if(file.getSize() > MAX_FILE_SIZE){
            throw new BusinessException("文件大小不能超过 " + (MAX_FILE_SIZE / 1024 / 1024 / 1024) + " GB");
        }
        checkReceiver(myUserId, receiverId);
        // 获取文件名
        String originalName = file.getOriginalFilename();
        if (!StringUtils.hasLength(originalName)){//判断字符串是否存在内容（空串不行，空格可以)
            originalName = "未命名文件";
        }
        originalName = StringUtils.cleanPath(originalName);//文件上传防路径穿越漏洞
        // 去掉文件路径，只保留文件名
        if (originalName.contains("/")) {
            originalName = originalName.substring(originalName.lastIndexOf('/') + 1);
        }

        String fileUrl;
        try {
            fileUrl = aliyunOSSOperator.upload(file.getBytes(),originalName);
        }catch (Exception e){
            log.error("文件上传 OSS 失败：{}", e.getMessage(), e);
            throw new BusinessException("文件上传失败，请稍后重试");
        }
        if (!StringUtils.hasLength(fileUrl)) {
            throw new BusinessException("文件上传失败：未取得 OSS 地址");

        }

        // 保存消息到数据库
        Messages m = new Messages();
        m.setMsgId(UUID.randomUUID().toString());
        m.setSenderId(myUserId);
        m.setReceiverId(receiverId);
        m.setMsgType("FILE_MES");
        m.setContent(originalName);
        m.setFileUrl(fileUrl);
        m.setFileSize(file.getSize());
        m.setSendTime(LocalDateTime.now());
        m.setRecalled(false);

        messageMapper.insert(m);

        // 转化时间格式
        String sendTime = TIME_FORMATTER.format(m.getSendTime());

        // 实时推送
        WsMessage ws = new WsMessage(m.getMsgId(),myUserId,empMapper.findNicknameByUserId(myUserId),
                receiverId,originalName,sendTime,originalName,file.getSize());
        ws.setFileUrl(fileUrl);
        boolean delivered = pushTo(receiverId, "FILE_MES", ws);
        if (!delivered) {
            log.warn("用户 [{}] 未实时送达文件消息，已入库待其上线拉取", receiverId);
            chatWebSocketHandler.pushTo(myUserId, "SYSTEM", new WsMessage("", "系统", "系统", myUserId,
                    "用户 " + receiverId + " 不在线，文件消息未送达", sendTime));
        }

        return new MessagesFile(m.getMsgId(), originalName, fileUrl, file.getSize(), sendTime, delivered);
    }

    /** 历史消息 */
    @Override
    public HistoryResult history(String myUserId, String peerId, Integer page, Integer size) {
        if (!StringUtils.hasLength(peerId)){
            throw new BusinessException("接收方账号不能为空");
        }
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 20 : Math.min(size, 100);//上限100
        long total = messageMapper.countByPeer(myUserId, peerId);
        List<Messages> list = messageMapper.selectPageByPeer(myUserId, peerId, (p - 1) * s, s);
        return new HistoryResult(total, p, s, list);

    }


    /** 个人会话列表 */
    @Override
    public List<ConversationResult> conversations(String myUserId) {
        requireSender(myUserId);
        /* selectConversations 已按对端分组取最新一条（时间倒序），
           这里补上对端昵称（聊天身份 = 用户账号，昵称查不到时回退为账号本身） */
        List<ConversationResult> result = new ArrayList<>();
        Map<String, Object> unreadMap = new HashMap<>(); // 组装：对端账号 → 未读数
        for (Map<String, Object> row : messageMapper.countUnreadByPeer(myUserId)) {
            unreadMap.put(String.valueOf(row.get("peerId")), row.get("unreadCount"));
        }
        for (Messages m : messageMapper.selectConversations(myUserId)) {
            String peerId = m.getSenderId().equals(myUserId) ? m.getReceiverId() : m.getSenderId();
            String peerName = empMapper.findNicknameByUserId(peerId);
            int unread = ((Number) unreadMap.getOrDefault(peerId, 0L)).intValue();
            result.add(new ConversationResult(peerId,
                    StringUtils.hasLength(peerName) ? peerName : peerId,
                    m.getMsgId(), m.getSenderId(), m.getMsgType(), m.getContent(),
                    m.getSendTime(), m.getRecalled(), unread));
        }
        return result;
    }

    @Override
    public void markRead(String myUserId, String peerId) {
        if (!StringUtils.hasLength(myUserId) || !StringUtils.hasLength(peerId)) {
            return; // 参数缺失直接忽略（前端容错）
        }
        messageMapper.upsertReadState(myUserId, peerId);
    }


    /** 发送者上下文校验 */
    public void requireSender(String myUserId) {
        //调用Spring工具类：字符串不为 null，并且去掉首尾空白后长度 > 0 → 返回 true 否者返回 false
        if (!StringUtils.hasLength(myUserId)) {
            throw new BusinessException("登录状态无效，请重新登录");
        }
    }

    /** 接收方校验：接收方账号不能为空+不能发给自己 + 账号必须存在 */
    public void checkReceiver(String myUserId, String receiverId){
        if (!StringUtils.hasLength(receiverId)) {
            throw new BusinessException("接收方账号不能为空");
        }
        if (receiverId.equals(myUserId)) {
            throw new BusinessException("不能给自己发送消息");
        }
        if (empMapper.countByUserId(receiverId) == 0) {
            throw new BusinessException("接收方用户不存在");
        }
    }

    /** 推送消息给接收方 */
    public boolean pushTo(String receiverId, String type, WsMessage message){
        if(!chatWebSocketHandler.isOnline(receiverId)){
            return false;
        }
        return chatWebSocketHandler.pushTo(receiverId, type, message);
    }

}
