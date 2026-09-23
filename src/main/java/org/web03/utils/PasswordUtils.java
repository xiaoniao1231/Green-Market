package org.web03.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 密码哈希工具（BCrypt 单向哈希）
 *
 * <p>为什么是 BCrypt：
 * <ul>
 *   <li>单向：只做哈希不做可逆加密，库里存的是「哈希值」，即使整库泄露也无法直接还原出密码；</li>
 *   <li>自带随机盐：同一个密码每次哈希结果都不同（盐写进哈希串本身，不需要额外存盐列），
 *       撞库 / 彩虹表对每个用户都要重新计算；</li>
 *   <li>可调成本因子：计算 2^strength 轮，故意「慢」，把离线爆破的成本抬高到不可行，
 *       而单次登录只多花几十毫秒；</li>
 *   <li>恒定时间比较：matches 内部不会因为前几位匹配就提前返回，避免时序侧信道。</li>
 * </ul>
 *
 * <p>MD5 / SHA-1 / SHA-256 这类通用摘要不能用于密码：它们为「快」而设计，
 * 单卡就能每秒算几十亿次，加盐也无法阻止单个账号被高速爆破。
 */
public final class PasswordUtils {

    /**
     * BCrypt 成本因子：2^10 = 1024 轮。
     * 取值权衡：10 在本机约 50~100ms/次，登录、注册几乎无感，
     * 但把离线爆破速度压到每秒几千次量级；安全要求更高可调到 12（耗时约 4 倍）。
     */
    private static final int STRENGTH = 10;

    /** BCrypt 哈希串固定长度（$2a$10$ + 22 位盐 + 31 位摘要） */
    private static final int BCRYPT_HASH_LENGTH = 60;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(STRENGTH);

    private PasswordUtils() {
    }

    /**
     * 生成密码哈希（入库用）。
     * 每次调用结果都不同（随机盐），因此不能用「哈希相等」判断两次输入是否是同一个密码，
     * 校验一律走 {@link #matches(String, String)}。
     */
    public static String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) return null;
        return ENCODER.encode(rawPassword);
    }

    /**
     * 校验明文密码与库里的哈希是否匹配。
     *
     * @param rawPassword     用户本次提交的明文密码
     * @param encodedPassword 数据库中保存的密码字段
     * @return 匹配返回 true；任一为空、或库里存的是非法 BCrypt 串（如历史明文数据）返回 false
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || rawPassword.isEmpty()
                || encodedPassword == null || encodedPassword.isEmpty()) {
            return false;
        }
        try {
            return ENCODER.matches(rawPassword, encodedPassword);
        } catch (IllegalArgumentException e) {
            /* 库里不是合法 BCrypt 哈希（长度/编码不符）时会抛 IllegalArgumentException。
               这里按「不匹配」处理，不把异常抛给全局处理器，避免 500 页面 */
            return false;
        }
    }

    /**
     * 判断一个密码字段是否已是 BCrypt 哈希。
     * 用于识别历史明文数据：老库里存的是 6-15 位明文，登录校验通过后据此升级为哈希。
     */
    public static boolean isEncoded(String value) {
        return value != null
                && value.length() == BCRYPT_HASH_LENGTH
                && value.startsWith("$2");
    }

    /**
     * 历史明文密码比对 —— **只用于老数据兼容迁移**，新数据永远走 {@link #matches(String, String)}。
     *
     * <p>改造前 users.password 存的是明文，若直接拒绝明文比对，老库里所有账号（含演示店家
     * owner001~owner010）都将无法登录。因此登录时识别出「非 BCrypt 串」就按明文比对一次，
     * 通过后立刻把该行升级为 BCrypt 哈希，明文随即消失。
     *
     * <p>用 {@link MessageDigest#isEqual} 而不是 String.equals：前者是恒定时间比较，
     * 不会因为前几位匹配就提前返回，避免通过响应时间逐位猜密码的时序侧信道。
     *
     * @param rawPassword      用户本次提交的明文密码
     * @param storedPlaintext  数据库中保存的历史明文密码
     */
    public static boolean matchesPlaintext(String rawPassword, String storedPlaintext) {
        if (rawPassword == null || storedPlaintext == null) return false;
        return MessageDigest.isEqual(
                rawPassword.getBytes(StandardCharsets.UTF_8),
                storedPlaintext.getBytes(StandardCharsets.UTF_8));
    }
}
