package org.web03.service;

import org.web03.pojo.LoginInfo;
import org.web03.pojo.PhoneRegisterRequest;
import org.web03.pojo.User;


public interface LongService {
    LoginInfo login(User user);

    LoginInfo longinPhone(PhoneRegisterRequest prr);
}
