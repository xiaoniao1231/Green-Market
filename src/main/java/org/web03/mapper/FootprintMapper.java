package org.web03.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.web03.pojo.Footprint.Footprint;

import java.util.List;

@Mapper
public interface FootprintMapper {
    //记录一次浏览足迹
    @Insert("insert into footprints (user_id, product_id, created_at, updated_at) " +
            "values (#{userId}, #{productId}, now(), now()) " +
            "on duplicate key update updated_at = now()")
    int upsert(@Param("userId") String userId, @Param("productId") Integer productId);

    //根据用户ID统计足迹数量
    Integer countByUserId(String userId);

    //容量裁剪
    int trim(@Param("userId") String userId, @Param("keep") int keep);

    //根据用户ID分页列表
    List<Footprint> listByUserId(@Param("userId") String userId, @Param("offset") int offset, @Param("pageSize") int pageSize);
}
