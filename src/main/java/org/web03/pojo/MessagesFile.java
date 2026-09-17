package org.web03.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件消息
 */


@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessagesFile {
    private String msgId;
    private String fileName;    // 文件名
    private long fileSize;      // 文件大小
    private String fileUrl;     // 文件Url
    private String sendTime;    // 发送时间
    private boolean delivered;  // 是否已送达


    public MessagesFile(String msgId, String originalName, String fileUrl, long size, String sendTime, boolean delivered) {
        this.msgId = msgId;
        this.fileName = originalName;
        this.fileUrl = fileUrl;
        this.fileSize = size;
        this.sendTime = sendTime;
        this.delivered = delivered;
    }
}
