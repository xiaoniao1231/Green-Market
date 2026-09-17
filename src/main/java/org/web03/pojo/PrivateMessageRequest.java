package org.web03.pojo;

import lombok.Data;

/**
 * 发送私聊请求体
 */
@Data
public class PrivateMessageRequest {
    private String receiverId;//接收者ID
    private String content;//内容
    private String msgId;//消息ID
}
