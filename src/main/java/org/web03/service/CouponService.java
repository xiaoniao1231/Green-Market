package org.web03.service;

import org.web03.pojo.Coupon.UserCoupon;

import java.util.List;
import java.util.Map;

public interface CouponService {
    List<UserCoupon> list();

    List<Map<String, Object>> claimable();

    UserCoupon claim(Integer couponId);
}
