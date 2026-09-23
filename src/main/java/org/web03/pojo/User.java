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
    private Integer id;//自增主键
    private String userId;//用户账号（手机号注册时即手机号，最长 15 位）
    private String password;//密码（6-15 位）
    private String nickname;//昵称（最长 30 位）
    private String gender;//性别：male 男 / female 女 / secret 保密
    private String avatar;//头像（emoji 字符）
    private String signature;//个性签名（最长 40 字）
    private String shopId;//所属店铺标识
    private LocalDateTime createdAt;//创建时间
    private String phoneNumber;//手机号
    private LocalDateTime updatedAt;//资料更新时间
    private Integer pwdVersion;//密码版本号：每次改密/重置 +1，令此前签发的 JWT 立即失效（见 TokenFilter）
}
