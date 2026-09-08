package org.web03.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
private Integer id;//
private String userId;//用户ID
private String password;//密码
private String nickname;//昵称（数据库列名 nickname）
private LocalDateTime created_at;//创建时间
private String phone_number;//电话号码
}
