package org.web03.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Messages {
    private Integer id;
    private String msg_id;//消息ID
    private String sender_id;//发送者ID
    private String receiver_id;//接收者ID
    private String msg_type;//消息类型
    private String content;//消息内容
    private LocalDateTime send_time;//发送时间
    private Integer is_recalled;//是否撤回
}
