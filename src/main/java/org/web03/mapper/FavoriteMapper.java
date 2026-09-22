package org.web03.mapper;


import org.apache.ibatis.annotations.*;
import org.web03.pojo.Favorite.Favorite;

import java.util.List;

/**
 * 收藏夹数据访问层
 */
@Mapper
public interface FavoriteMapper {

    // 根据用户ID统计收藏夹数量
    Integer countByUserId(String userId);

    // 根据用户ID分页查询收藏夹列表
    List<Favorite> listByUserId(@Param("userId") String userId, @Param("offset") int offset, @Param("size") Integer size);

    //判断用户是否已收藏该商品。
    @Select("select count(*) from favorites where user_id = #{userId} and product_id = #{productId}")
    int exists(@Param("userId") String userId, @Param("productId") Integer productId);

    // 添加收藏
    /* 用 INSERT IGNORE 兜住并发：两个请求同时通过 exists 检查时，
       第二个 insert 会撞 uk_user_product 唯一键。若用普通 INSERT，
       会抛 DuplicateKeyException，被全局处理器统一翻译成
       「账号或手机号已存在」——收藏接口弹出这种文案非常误导。
       收藏本身就该是幂等的，重复插入静默忽略即可（影响 0 行）。 */
    @Insert("insert ignore into favorites (user_id, product_id, created_at) values (#{userId}, #{productId}, now())")
    int insert(@Param("userId") String userId, @Param("productId") Integer productId);

    // 取消收藏
    @Delete("delete from favorites where user_id = #{userId} and product_id = #{productId}")
    int deleteOne(@Param("userId") String userId, @Param("productId") Integer productId);

    // 清空当前用户全部收藏
    @Delete("delete from favorites where user_id = #{userId}")
    int clear(String userId);
}
