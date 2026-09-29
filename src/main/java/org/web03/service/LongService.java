package org.web03.service;

import org.web03.pojo.log.LoginInfo;
import org.web03.pojo.log.PhoneRegisterRequest;
import org.web03.pojo.User;


public interface LongService {
    LoginInfo login(User user);

    LoginInfo longinPhone(PhoneRegisterRequest prr);
}
