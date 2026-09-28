package org.web03.mapper;


import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.web03.pojo.Coupon.Coupon;
import org.web03.pojo.Coupon.UserCoupon;

import java.util.List;


@Mapper
public interface CouponMapper {

    // 我的优惠券（持有记录联券模板）
    @Select("select uc.id, uc.user_id, uc.coupon_id, uc.status, uc.used_order_id, uc.used_at, uc.received_at, " +
            "c.title, c.threshold, c.amount, c.expire " +
            "from user_coupons uc join coupons c on c.id = uc.coupon_id " +
            "where uc.user_id = #{userId} and c.enabled = 1 " +
            "order by (uc.status = 'unused') desc, uc.id desc")
    List<UserCoupon> listByUser(String userId);

    // 可领取的券（平台启用中且未过期的券）
    @Select("select id, title, threshold, amount, expire from coupons where enabled = 1 " +
            "and (expire is null or expire = '' or expire >= DATE_FORMAT(CURDATE(), '%Y-%m-%d')) order by id")
    List<Coupon> listEnabled();

    // 券模板
    @Select("select id, title, threshold, amount, expire, enabled from coupons where id = #{id}")
    Coupon findById(Integer couponId);

    // 我已领过的券模板ID
    @Select("select coupon_id from user_coupons where user_id = #{userId}")
    List<Integer> claimedCouponIds(String userId);

    // 我是否已领过该券
    @Select("select count(*) from user_coupons where user_id = #{userId} and coupon_id = #{couponId}")
    int countMine(@Param("userId") String userId, @Param("couponId") Integer couponId);

    // 领取（唯一键 uk_user_coupon 兜底一人一张）
    @Insert("insert into user_coupons (user_id, coupon_id, status, received_at) " +
            "values (#{userId}, #{couponId}, 'unused', now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertUserCoupon(UserCoupon uc);

    // 按持有记录取券（下单校验）
    @Select("select uc.id, uc.user_id, uc.coupon_id, uc.status, c.title, c.threshold, c.amount, c.expire " +
            "from user_coupons uc join coupons c on c.id = uc.coupon_id " +
            "where uc.id = #{id} and uc.user_id = #{userId} and c.enabled = 1")
    UserCoupon findUserCoupon(@Param("id") Integer id, @Param("userId") String userId);

    // 标记已用
    @Update("update user_coupons set status = 'used', used_order_id = #{orderId}, used_at = now() " +
            "where id = #{id} and user_id = #{userId} and status = 'unused'")
    int markUsed(@Param("id") Integer id, @Param("userId") String userId, @Param("orderId") Integer orderId);

    // 取消订单退回优惠券（幂等：已退回时影响 0 行）
    @Update("update user_coupons set status = 'unused', used_order_id = null, used_at = null " +
            "where id = #{id} and user_id = #{userId} and status = 'used'")
    int restoreCoupon(@Param("id") Integer id, @Param("userId") String userId);

}
