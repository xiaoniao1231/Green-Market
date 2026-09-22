package org.web03.pojo.Messages;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 推送帧的 message 部分（统一帧结构 {type, message:{...}}，见接口文档 4.1.3）
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WsMessage {
    private String msgId;           // 消息 ID
    private String senderId;        // 发送方账号
    private String senderNickname;  // 发送方昵称
    private String receiverId;      // 接收方账号
    private String content;         // 消息内容
    private String sendTime;        // 发送时间
    private String fileName;        // 文件名
    private Long fileSize;          // 文件大小
    private String fileUrl;    // 文件在阿里云 OSS 的访问地址

    //消息构造函数
    public WsMessage(String msgId, String senderId, String senderNickname, String receiverId,
                     String content, String sendTime) {
        this.msgId = msgId;
        this.senderId = senderId;
        this.senderNickname = senderNickname;
        this.receiverId = receiverId;
        this.content = content;
        this.sendTime = sendTime;
    }

    //文件构造函数
    public WsMessage(String msgId, String senderId, String senderNickname, String receiverId,
                     String content, String sendTime, String fileName, Long fileSize) {
        this(msgId, senderId, senderNickname, receiverId, content, sendTime);
        this.fileName = fileName;
        this.fileSize = fileSize;
    }
}
