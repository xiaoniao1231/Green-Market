package org.web03.mapper;

import org.web03.pojo.PhoneRegisterRequest;
import org.web03.pojo.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmpMapper {
    @Select("select * from users where user_id = #{userId} and password = #{password}")
    User login(User user);

    @Select("select * from users where phone_number = #{phone}")
    User longinPhone(PhoneRegisterRequest prr);
}
