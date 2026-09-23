package org.web03.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.web03.mapper.EmpMapper;
import org.web03.pojo.Result;
import org.web03.utils.CurrentHolder;
import org.web03.utils.JwtUtils;

import java.io.IOException;
import java.util.Set;

/**
 * 令牌校验过滤器
 * 白名单接口（登录/注册/验证码）与 OPTIONS 预检请求直接放行；
 * 其余接口校验令牌，令牌缺失或无效时返回 401 与统一错误结构。
 * 令牌来源：优先 Authorization: Bearer <jwt>，兼容旧版 token 头。
 *
 * <p>注册方式：交给 Spring 管理（{@code @Component}），不再用 {@code @WebFilter} 让 Servlet 容器扫描 ——
 * 只有成为 Spring 组件才能注入 {@link EmpMapper} 做「密码版本号」校验。
 * 两者不能并存：同时标注会导致同一个过滤器被注册两次、鉴权逻辑重复执行。
 *
 * <p>密码版本校验：token 里的 {@code pv} 声明必须与 users.pwd_version 一致。
 * 用户改密 / 忘记密码重置时 pwd_version 会 +1，于是此前签发的所有 token 立即失效
 * （无状态 JWT 天生没有「登出」能力，这是让旧令牌作废的最小代价方案）。
 */
@Slf4j
@Component
public class TokenFilter implements Filter {

    /** 密码版本校验要读 users.pwd_version，因此本过滤器必须是 Spring 组件才能注入 Mapper */
    @Autowired
    private EmpMapper empMapper;

    /** 无需登录即可访问的接口路径白名单（与控制器 @RequestMapping 精确对应） */
    private static final Set<String> WHITE_LIST = Set.of(
            "/login", "/login/phone",
            "/register", "/register/phone",
            "/sms-code",
            /* 忘记密码：未登录状态下的短信验证码重置，身份凭证是短信验证码而非 JWT */
            "/password/reset"
    );

    /** 公开浏览类接口前缀（首页/分类/搜索的商品列表与详情，浏览无需登录） */
    private static final Set<String> WHITE_PREFIXES = Set.of(
            "/home/", "/products"
    );

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 判断请求路径是否为公开接口（精确匹配白名单或以公开前缀开头） */
    private boolean isPublicPath(String path) {
        if (WHITE_LIST.contains(path)) return true;
        /* 商品图片上传（POST /products/image）不在公开浏览范围内：未登录不可上传（避免占用 OSS 配额）；
           排除后再按公开前缀判断，商品列表 /products、详情 /products/{id} 等浏览类请求不受影响 */
        if (path.startsWith("/products/image")) return false;
        for (String prefix : WHITE_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        // 0. 跨域响应头：浏览器直连后端（不走 nginx/Vite 同源代理）时必需。
        //    预检请求由本过滤器直接返回，不会进入 MVC 的 CORS 处理链，故必须在此补齐。
        applyCorsHeaders(request, response);

        // 1. 白名单接口（登录/注册/验证码/商品浏览）与 CORS 预检请求直接放行
        String path = request.getServletPath();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            // 预检请求：带上允许头后直接以 200 结束，无需进入业务链
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }
        if (isPublicPath(path)) {
            log.debug("放行无需鉴权请求: {}", path);
            chain.doFilter(request, response);
            return;
        }

        // WebSocket 握手放行：浏览器握手无法自定义 Authorization 头，
        //      token 走 query 参数（/ws?token=xxx），由 WsHandshakeInterceptor 单独校验，失败会拒绝握手
        if ("/ws".equals(path)) {
            log.debug("放行 WebSocket 握手请求: {}", path);
            chain.doFilter(request, response);
            return;
        }

        // 2. 获取令牌
        String jwt = resolveToken(request);

        // 3. 令牌缺失 → 401
        if (!StringUtils.hasLength(jwt)) {
            log.info("请求 {} 未携带令牌, 返回401", path);
            writeUnauthorized(response);
            return;
        }

        // 4. 解析令牌，失败 → 401
        int userId;
        int tokenPwdVersion;   // 令牌里携带的密码版本号（旧格式令牌没有 pv 声明，按 0 处理）
        String jwtUserId;
        try {
            Claims claims = JwtUtils.parseToken(jwt);
            Object idObj = claims.get("id");
            if (idObj == null) {
                log.warn("请求 {} 令牌缺少 id 声明, 返回401", path);
                writeUnauthorized(response);
                return;
            }
            userId = Integer.parseInt(idObj.toString());
            // 账号（userId 声明）在下面的密码版本校验通过后，才写入上下文供业务层使用
            jwtUserId = claims.get("userId", String.class);
            if (!StringUtils.hasLength(jwtUserId)) {
                // 旧格式令牌（只有 id、没有 userId）：无法识别当前账号，直接按未登录处理，
                // 避免业务层拿 null 账号去查店，误报“当前账号未开店”
                log.warn("请求 {} 令牌缺少 userId 声明（旧格式令牌）, 返回401", path);
                writeUnauthorized(response);
                return;
            }
            Object pvObj = claims.get("pv");
            tokenPwdVersion = (pvObj instanceof Number) ? ((Number) pvObj).intValue() : 0;
        } catch (Exception e) {
            log.error("请求 {} 解析令牌失败, 返回401: {}", path, e.getMessage());
            writeUnauthorized(response);
            return;
        }

        /* 5. 密码版本校验：改密 / 忘记密码重置后，此前签发的令牌立即失效。
              这是本过滤器唯一的查库动作（按 user_id 点查，走唯一索引）；
              查库失败时 fail-closed（同样拒绝），宁可让用户重新登录，
              也不放行一个可能已被作废的令牌。 */
        try {
            Integer currentPwdVersion = empMapper.findPwdVersion(jwtUserId);
            if (currentPwdVersion == null) {
                log.warn("请求 {} 令牌对应的账号 {} 不存在, 返回401", path, jwtUserId);
                writeUnauthorized(response);
                return;
            }
            if (tokenPwdVersion != currentPwdVersion) {
                log.info("请求 {} 的令牌已作废（账号 {} 密码已变更：令牌 pv={}, 当前 pv={}）, 返回401",
                        path, jwtUserId, tokenPwdVersion, currentPwdVersion);
                writeUnauthorized(response, "登录状态已失效，请重新登录");
                return;
            }
        } catch (Exception e) {
            log.error("请求 {} 校验密码版本失败, 返回401: {}", path, e.getMessage());
            writeUnauthorized(response);
            return;
        }

        // 6. 全部校验通过：写入 ThreadLocal，放行到业务链
        CurrentHolder.setCurrentUserId(jwtUserId);
        try {
            chain.doFilter(request, response);
        } finally {
            // 无论下游是否抛异常都清理，避免 ThreadLocal 泄漏
            CurrentHolder.remove();
        }
    }

    /** 从请求头解析令牌：Authorization: Bearer <jwt> 优先，token 头兜底（兼容旧客户端） */
    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasLength(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return request.getHeader("token");
    }

    /** 返回 401 及统一错误结构 */
    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        writeUnauthorized(response, "未登录或登录已失效");
    }

    /**
     * 返回 401 及统一错误结构（自定义提示）。
     *
     * <p>密码变更导致的令牌作废用「登录状态已失效，请重新登录」：前端 `api.js` 的
     * `isAuthFailure` 会按「登录状态」等关键词识别为会话失效，自动清除本地登录态并引导重新登录。
     */
    private void writeUnauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(), Result.error(msg));
    }

    /**
     * 补写跨域响应头（仅本机开发源，与 {@code WebConfig} 的 CORS 映射保持一致）。
     *
     * <p>过滤器在 MVC 的 CORS 处理之前执行，且鉴权失败、预检请求都会直接结束响应，
     * 若不在这里补头，浏览器只会看到「CORS 错误」而拿不到真正的 401 业务信息。
     */
    private void applyCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (origin == null || !isAllowedOrigin(origin)) {
            return;
        }
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Vary", "Origin");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, token, X-Requested-With");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Max-Age", "3600");
    }

    /** 仅放行本机开发源（localhost / 127.0.0.1 的任意端口）；生产部署请改为具体域名 */
    private boolean isAllowedOrigin(String origin) {
        return origin.startsWith("http://localhost:") || origin.equals("http://localhost")
                || origin.startsWith("http://127.0.0.1:") || origin.equals("http://127.0.0.1")
                || origin.startsWith("https://localhost:") || origin.startsWith("https://127.0.0.1:");
    }
}
