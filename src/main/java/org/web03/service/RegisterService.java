package org.web03.service;

import org.web03.pojo.PhoneRegisterRequest;
import org.web03.pojo.User;

public interface RegisterService {
    void register(User user);

    void phoneRegister(PhoneRegisterRequest prr);
}
