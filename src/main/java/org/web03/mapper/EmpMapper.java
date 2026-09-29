package org.web03.mapper;

import org.apache.ibatis.annotations.*;
import org.web03.pojo.log.AccountLog;
import org.web03.pojo.log.PhoneRegisterRequest;
import org.web03.pojo.User;

@Mapper
public interface EmpMapper {
    //登录
    @Select("select * from users where user_id = #{userId}")
    User login(User user);

    //更新密码并递增密码版本号
    @Update("update users set password = #{password}, pwd_version = pwd_version + 1, last_pwd_change_at = now(), updated_at = now() where user_id = #{userId}")
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

    // 根据手机号查询账号
    @Select("select * from users where phone_number = #{phone} limit 1")
    User findByPhone(String phone);

    //按照账户查询昵称
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

    //判断手机号是否被其他用户绑定
    @Select("select count(*) from users where phone_number = #{phone} and user_id <> #{userId}")
    int countPhoneBoundByOthers(@Param("phone") String phone, @Param("userId") String userId);

    //更新手机号
    @Update("update users set phone_number = #{phone}, phone_bound_at = now(), updated_at = now() " +
            "where user_id = #{userId}")
    int updatePhone(@Param("userId") String userId, @Param("phone") String phone);

    //插入日志
    @Insert("insert into account_logs (user_id, action, detail, ip, created_at) "
            + "values (#{userId}, #{action}, #{detail}, #{ip}, now())")
    void insertLog(AccountLog accountLog);
}
