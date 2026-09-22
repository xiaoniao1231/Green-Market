package org.web03.mapper;

import org.apache.ibatis.annotations.*;
import org.web03.pojo.Address.Address;

import java.util.List;


@Mapper
public interface AddressMapper {

    //查询用户地址列表，按默认地址和添加时间排序
    @Select("select * from addresses where user_id = #{userId} order by is_default desc, id desc")
    List<Address> listByUserId(String userId);

    //查询用户地址数量
    @Select("select count(*) from addresses where user_id = #{userId}")
    int countByUserId(String userId);

    //清空用户默认地址
    @Update("update addresses set is_default = 0 where user_id = #{userId} and is_default = 1")
    void clearDefault(String userId);

    //设置用户默认地址
    @Select("update addresses set is_default = 1, updated_at = now() where id = #{id} and user_id = #{userId}")
    Integer setDefault(@Param("id") Integer id,@Param("userId") String userId);

    // 新增地址
    @Insert("insert into addresses (user_id, name, phone, region, detail,tag,is_default, created_at, updated_at) " +
            "values (#{userId}, #{name}, #{phone}, #{region}, #{detail}, #{tag},#{isDefault}, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Address address);

    // 更新地址
    @Update("update addresses set name = #{name}, phone = #{phone}, region = #{region}, detail = #{detail}, " +
            "tag = #{tag}, is_default = #{isDefault}, updated_at = now() where id = #{id} and user_id = #{userId}")
    Integer update(Address address);

    // 删除地址
    @Delete("delete from addresses where id = #{id} and user_id = #{userId} ")
    void delete(@Param("id") Integer id, @Param("userId")    String userId);

    // 按 ID + 归属用户查询（防止越权操作他人地址；找不到返回 null）
    @Select("select * from addresses where id = #{id} and user_id = #{userId} limit 1")
    Address findByIdAndUser(@Param("id") Integer id, @Param("userId") String userId);
}
