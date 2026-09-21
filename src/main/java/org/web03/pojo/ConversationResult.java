package org.web03.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话列表项（GET /messages/conversations 的 data 元素）：
 * 当前用户与某对端的会话入口 + 最后一条消息摘要，
 * 供前端登录后重建消息中心会话列表（对端 id / 昵称 / 最后消息预览）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResult {
    private String peerId;          // 对端账号
    private String peerName;        // 对端昵称
    private String lastMsgId;       // 最后一条消息 ID
    private String lastSenderId;    // 最后一条消息发送者账号
    private String lastType;        // 最后一条消息类型
    private String lastContent;     // 最后一条消息内容（文本或文件名）
    private LocalDateTime lastTime; // 最后一条消息时间
    private Boolean lastRecalled;   // 最后一条消息是否已撤回
    private Integer unreadCount;    // 未读消息计数
}
