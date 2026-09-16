package org.web03.mapper;

import org.web03.pojo.PhoneRegisterRequest;
import org.web03.pojo.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmpMapper {
    // 登录
    @Select("select * from users where user_id = #{userId} and password = #{password}")
    User login(User user);

    // 手机号登录
    @Select("select * from users where phone_number = #{phone}")
    User longinPhone(PhoneRegisterRequest prr);

    //按照账户查询昵称（推送消息时展示对方昵称）
    @Select("select nickname from users where user_id = #{userId}")
    String findNicknameByUserId(String userId);

    //账号是否存在
    @Select("select count(*) from users where user_id = #{userId}")
    int countByUserId(String userId);
}
