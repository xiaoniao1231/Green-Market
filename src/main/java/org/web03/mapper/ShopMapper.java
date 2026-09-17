package org.web03.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ShopMapper {
    //根据店主ID查询店铺ID
    @Select("select shop_id from shops where owner_user_id = #{ownerUserId} limit 1")
    String findShopIdByOwner(String userId);
}
