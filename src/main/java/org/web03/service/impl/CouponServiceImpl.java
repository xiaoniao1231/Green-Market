package org.web03.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web03.exception.BusinessException;
import org.web03.mapper.CouponMapper;
import org.web03.pojo.Coupon.Coupon;
import org.web03.pojo.Coupon.UserCoupon;
import org.web03.service.CouponService;
import org.web03.utils.CouponUtils;
import org.web03.utils.CurrentHolder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户优惠券服务实现类
 */

@Service
public class CouponServiceImpl implements CouponService {

    /** 与订单 / 消息模块统一的时间格式 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private CouponMapper couponMapper;

    // 我的优惠券
    @Override
    public List<UserCoupon> list() {
        return couponMapper.listByUser(CurrentHolder.getCurrentUserId());
    }

    // 可领取的券
    @Override
    public List<Map<String, Object>> claimable() {
        Set<Integer> mine = new HashSet<>(couponMapper.claimedCouponIds(CurrentHolder.getCurrentUserId()));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Coupon c : couponMapper.listEnabled()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("couponId", c.getId());
            item.put("title", c.getTitle());
            item.put("threshold", c.getThreshold());
            item.put("amount", c.getAmount());
            item.put("expire", c.getExpire());
            item.put("claimed", mine.contains(c.getId()));
            list.add(item);
        }
        return list;
    }

    // 领取：每张券每人只能领一张
    @Override
    @Transactional
    public UserCoupon claim(Integer couponId) {
        String userId = CurrentHolder.getCurrentUserId();
        Coupon c = couponMapper.findById(couponId);
        if (c == null || c.getEnabled() == null || c.getEnabled() != 1)
            throw new BusinessException("优惠券不存在或已停用");
        if (CouponUtils.isExpired(c.getExpire())) throw new BusinessException("优惠券已过期");
        if (couponMapper.countMine(userId, couponId) > 0)
            throw new BusinessException("您已领取过该优惠券");

        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        try {
            couponMapper.insertUserCoupon(uc);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("您已领取过该优惠券");
        }
        uc.setStatus("unused");
        uc.setTitle(c.getTitle());
        uc.setThreshold(c.getThreshold());
        uc.setAmount(c.getAmount());
        uc.setExpire(c.getExpire());
        /* 领取时间由库里的 now() 落库，这里回填同一时刻，保证接口响应字段完整（接口文档 4.3） */
        uc.setReceivedAt(LocalDateTime.now().format(TIME_FORMATTER));
        return uc;
    }
}
