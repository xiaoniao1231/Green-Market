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
    private Integer id;                //主键
    private String msgId;              //消息唯一ID
    private String senderId;           //发送消息者
    private String receiverId;         //接收消息者
    private String msgType;            //消息类型
    private String content;            //文本内容或文件名
    private LocalDateTime sendTime;    //发送时间
    private Boolean recalled;          //是否撤回消息

    private String fileUrl;    // 文件在阿里云 OSS 的访问地址
    private Long fileSize;     // 文件大小（字节）
}
