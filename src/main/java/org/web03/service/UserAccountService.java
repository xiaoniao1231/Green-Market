package org.web03.service;

import org.springframework.web.multipart.MultipartFile;
import org.web03.pojo.User;
import org.web03.pojo.log.BindPhoneRequest;
import org.web03.pojo.log.ChangePasswordRequest;
import org.web03.pojo.log.ChangePasswordResult;

import java.util.Map;

public interface UserAccountService {
    User me();

    void bindPhone(BindPhoneRequest request);

    ChangePasswordResult changePassword(ChangePasswordRequest request);

    Map<String,Object> uploadAvatar(MultipartFile file);

    void updateProfile(User user);
}
