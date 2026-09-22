package org.web03.pojo.Messages;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送私聊响应
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendPrivateResult {
    private String msgId;       //消息ID
    private String sendTime;     //发送时间
    private boolean isOnline;   //接收方是否在线
}
