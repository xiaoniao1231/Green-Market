package org.web03.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.web03.pojo.User;

@Mapper
public interface RegisterMapper {
    void register(User user);

    int existsByPhone(String phone);

    int existsByUserId(String userId);

    void registerByPhone(User user);
}
