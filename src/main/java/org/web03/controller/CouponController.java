package org.web03.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.web03.exception.BusinessException;
import org.web03.pojo.Result;
import org.web03.service.CouponService;

import java.util.Map;

/**
 * 用户优惠券
 */

@RestController
@RequestMapping("/coupons")
public class CouponController {

    @Autowired
    private CouponService couponService;

    //我的优惠券
    @GetMapping
    public Result list() { return Result.success(couponService.list()); }

    //可领取的券
    @GetMapping("/claimable")
    public Result claimable() { return Result.success(couponService.claimable()); }

    //领取一张券
    @PostMapping("/claim")
    public Result claim(@RequestBody Map<String, Object> body) {
        Object raw = body == null ? null : body.get("couponId");
        String s = raw == null ? "" : String.valueOf(raw).trim();
        if (s.isEmpty()) throw new BusinessException("优惠券参数无效");
        try {
            return Result.success(couponService.claim(Integer.valueOf(s)));
        } catch (NumberFormatException e) {
            /* couponId 不是数字（前端传了脏值）：按参数无效处理，不能让它冒成 500 */
            throw new BusinessException("优惠券参数无效");
        }
    }
}
