package org.web03.utils;


public class CurrentHolder {

    private static final ThreadLocal<Integer> CURRENT_LOCAL = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USERID_LOCAL = new ThreadLocal<>();


    // 当前登录账号（users.employee_id），由 TokenFilter 解析 JWT 后写入
    public static void setCurrentId(Integer employeeId) {
        CURRENT_LOCAL.set(employeeId);
    }

    // 获取当前登录账号（users.employee_id）
    public static Integer getCurrentId() {
        return CURRENT_LOCAL.get();
    }

    /** 当前登录账号（users.user_id），由 TokenFilter 解析 JWT 后写入 */
    public static void setCurrentUserId(String userId) {
        CURRENT_USERID_LOCAL.set(userId);
    }

    // 获取当前登录账号（users.user_id）
    public static String getCurrentUserId() {
        return CURRENT_USERID_LOCAL.get();
    }

    // 移除当前线程的登录账号信息
    public static void remove() {
        CURRENT_LOCAL.remove();
        CURRENT_USERID_LOCAL.remove();
    }
}
