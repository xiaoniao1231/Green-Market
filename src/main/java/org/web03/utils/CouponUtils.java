package org.web03.utils;

import java.time.LocalDate;

/**
 * 优惠券有效期判断
 */
public class CouponUtils {

    // expire 为 yyyy-MM-dd，含当天有效；为空表示长期有效，格式不规范时按长期有效处理
    public static boolean isExpired(String expire) {
        if (expire == null || expire.trim().isEmpty()) return false;
        try {
            return LocalDate.parse(expire.trim()).isBefore(LocalDate.now());
        } catch (Exception e) {
            return false;
        }
    }
}
