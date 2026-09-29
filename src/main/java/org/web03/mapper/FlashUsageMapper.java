package org.web03.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日秒杀资格
 */
@Mapper
public interface FlashUsageMapper {

    // 占用当日资格：插入成功=抢到
    int insertUse(@Param("userId") String userId, @Param("productId") Integer productId, @Param("day") LocalDate day);

    // 释放过再占用：仅 released 时成功
    int reuse(@Param("userId") String userId, @Param("productId") Integer productId, @Param("day") LocalDate day);

    // 绑定订单
    int bindOrderId(@Param("userId") String userId, @Param("productId") Integer productId,
                    @Param("day") LocalDate day, @Param("orderId") Integer orderId);

    // 取消订单释放资格
    int releaseByOrder(@Param("orderId") Integer orderId);

    // 当日已用商品 id
    List<Integer> usedProductIds(@Param("userId") String userId, @Param("day") LocalDate day);

    // 当日某商品是否已用
    int countUsed(@Param("userId") String userId, @Param("productId") Integer productId, @Param("day") LocalDate day);
}
