package org.web03.pojo.log;


import lombok.Data;

/**
 * 修改密码请求体
 */

@Data
public class ChangePasswordRequest {

    private String oldPassword;// 旧密码
    private String newPassword;// 新密码
}
