package org.web03.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 好友
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Friends {
    private Integer id;
    private String user_id;
    private String friend_id;
    private Integer status;
    private LocalDateTime created_time;
    private LocalDateTime updated_time;
}
