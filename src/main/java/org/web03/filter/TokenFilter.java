package org.web03.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
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
 */
@Slf4j
@WebFilter(urlPatterns = "/*")
public class TokenFilter implements Filter {

    /** 无需登录即可访问的接口路径白名单（与控制器 @RequestMapping 精确对应） */
    private static final Set<String> WHITE_LIST = Set.of(
            "/login", "/login/phone",
            "/register", "/register/phone",
            "/sms-code"
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

        // 4. 解析令牌，失败 → 401；成功则存入 ThreadLocal 后放行
        int userId;
        try {
            Claims claims = JwtUtils.parseToken(jwt);
            Object idObj = claims.get("id");
            if (idObj == null) {
                log.warn("请求 {} 令牌缺少 id 声明, 返回401", path);
                writeUnauthorized(response);
                return;
            }
            userId = Integer.parseInt(idObj.toString());
            // 新增：把账号（userId 声明）一并写入上下文，业务层发消息时作为 senderId 使用
            String jwtUserId = claims.get("userId", String.class);
            if (StringUtils.hasLength(jwtUserId)) {
                CurrentHolder.setCurrentUserId(jwtUserId);
            } else {
                // 旧格式令牌（只有 id、没有 userId）：无法识别当前账号，直接按未登录处理，
                // 避免业务层拿 null 账号去查店，误报“当前账号未开店”
                log.warn("请求 {} 令牌缺少 userId 声明（旧格式令牌）, 返回401", path);
                writeUnauthorized(response);
                return;
            }
        } catch (Exception e) {
            log.error("请求 {} 解析令牌失败, 返回401: {}", path, e.getMessage());
            writeUnauthorized(response);
            return;
        }

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
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(), Result.error("未登录或登录已失效"));
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
