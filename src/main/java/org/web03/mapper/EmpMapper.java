package org.web03.mapper;

import org.apache.ibatis.annotations.Update;
import org.web03.pojo.PhoneRegisterRequest;
import org.web03.pojo.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmpMapper {
    //登录
    @Select("select * from users where user_id = #{userId}")
    User login(User user);

    //更新密码并递增密码版本号
    @Update("update users set password = #{password}, pwd_version = pwd_version + 1, updated_at = now() where user_id = #{userId}")
    int updatePassword(@Param("userId") String userId, @Param("password") String password);

    //新密码
    @Update("update users set password = #{password}, updated_at = now() where user_id = #{userId}")
    void updatePasswordOnly(@Param("userId") String userId, @Param("password") String password);

    // 查询账号当前的密码版本号（TokenFilter 每次带令牌请求都会调用；账号不存在返回 null → 拒绝放行）
    @Select("select pwd_version from users where user_id = #{userId}")
    Integer findPwdVersion(String userId);

    // 手机号登录
    @Select("select * from users where phone_number = #{phone}")
    User longinPhone(PhoneRegisterRequest prr);

    // 根据手机号查询账号（忘记密码重置时定位用户；未绑定手机号的账号查不到，返回 null）
    @Select("select * from users where phone_number = #{phone} limit 1")
    User findByPhone(String phone);

    //按照账户查询昵称（推送消息时展示对方昵称）
    @Select("select nickname from users where user_id = #{userId}")
    String findNicknameByUserId(String userId);

    //账号是否存在
    @Select("select count(*) from users where user_id = #{userId}")
    int countByUserId(String userId);

    //修改用户信息（动态更新）
    @Update("update users set nickname = ifnull(#{nickname}, nickname), " +
            "gender = ifnull(#{gender}, gender), " +
            "avatar = ifnull(#{avatar}, avatar), " +
            "signature = ifnull(#{signature}, signature), " +
            "updated_at = now() where user_id = #{userId}")
    void updateProfile(User user);

    //根据用户ID查询用户信息
    @Select("select * from users where user_id = #{userId}")
    User findByUserId(String myUserId);
}
