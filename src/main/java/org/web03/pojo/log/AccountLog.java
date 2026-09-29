package org.web03.pojo.log;

import lombok.Data;

import java.time.LocalDateTime;


/**
 * 账户敏感操作日志
 */
@Data
public class AccountLog {

    private Long id;                    // 自增主键
    private String userId;              // 操作账号
    private String action;              // 动作：bind_phone / change_phone / change_password / update_profile
    private String detail;              // 摘要
    private String ip;                  // 客户端 IP
    private LocalDateTime createdAt;   // 操作时间
}
