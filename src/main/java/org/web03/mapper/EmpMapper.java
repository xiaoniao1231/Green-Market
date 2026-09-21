package org.web03.mapper;

import org.apache.ibatis.annotations.Update;
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

    /**
     * 修改用户信息（动态更新）。
     *
     * <p>users 表的 gender / avatar / signature 都是 NOT NULL 列，
     * 若前端只提交部分字段（例如只改昵称），整字段 UPDATE 会把其余列写成 NULL，
     * 触发 "Column 'xxx' cannot be null" 报 500。这里只更新非 null 字段，
     * 并固定写入 updated_at，保证 SET 子句永不为空。
     */
    @Update("<script>update users <set>" +
            "<if test='nickname != null'>nickname = #{nickname},</if>" +
            "<if test='gender != null'>gender = #{gender},</if>" +
            "<if test='avatar != null'>avatar = #{avatar},</if>" +
            "<if test='signature != null'>signature = #{signature},</if>" +
            "updated_at = now()" +
            "</set> where user_id = #{userId}</script>")
    void updateProfile(User user);

    //根据用户ID查询用户信息
    @Select("select * from users where user_id = #{userId}")
    User findByUserId(String myUserId);
}
