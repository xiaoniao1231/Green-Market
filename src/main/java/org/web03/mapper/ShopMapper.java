package org.web03.mapper;

import org.apache.ibatis.annotations.*;
import org.web03.pojo.Shop.Shop;

@Mapper
public interface ShopMapper {
    //根据店主ID查询店铺ID
    @Select("select shop_id from shops where owner_user_id = #{ownerUserId} limit 1")
    String findShopIdByOwner(String userId);

    //根据店铺名称和排除的店铺ID查询店铺数量
    @Select("select count(*) from shops where name = #{name} and shop_id != #{excludeShopId}")
    int countByName(@Param("name") String name, @Param("excludeShopId") String excludeShopId);

    //插入店铺
    @Insert("insert into shops (shop_id, name, owner_user_id, score, avatar, fans, intro, founded, created_at, updated_at) " +
            "values (#{shopId}, #{name}, #{ownerUserId}, #{score}, #{avatar}, #{fans}, #{intro}, #{founded}, now(), now())")
    void insert(Shop shop);

    //将用户绑定到店铺
    @Update("update users set shop_id = #{shopId} where user_id = #{ownerId}")
    void bindUserShop(@Param("shopId") String shopId, @Param("ownerId") String ownerId);

    //根据店主ID查询店铺(limit 1: 限制只查询一个)
    @Select("select * from shops where owner_user_id = #{ownerUserId} limit 1")
    Shop findByOwnerUserId(String ownerUserId);

    //更新店铺资料
    @Update("update shops set name = #{name}, avatar = #{avatar}, intro = #{intro}, updated_at = now() where  owner_user_id = #{ownerUserId}")
    void updateProfile(Shop shop);

    //根据店铺ID查询店铺
    @Select("select * from shops where shop_id = #{shopId}")
    Shop findById(String shopId);

    //新增关注关系
    @Insert("insert ignore into shop_follows (user_id, shop_id, created_at) values (#{userId}, #{shopId}, now())")
    int insertFollow(@Param("userId") String userId, @Param("shopId") String shopId);

    //删除关注关系
    @Delete("delete from shop_follows where user_id = #{userId} and shop_id = #{shopId}")
    int deleteFollow(@Param("userId") String userId, @Param("shopId") String shopId);

    //粉丝数 +1
    @Update("update shops set fans = fans + 1, updated_at = now() where shop_id = #{shopId}")
    void increaseFans(String shopId);

    //粉丝数 -1
    @Update("update shops set fans = case when fans > 0 then fans - 1 else 0 end, updated_at = now() where shop_id = #{shopId}")
    void decreaseFans(String shopId);
}
