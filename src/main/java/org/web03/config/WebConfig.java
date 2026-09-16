package org.web03.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域（CORS）配置。
 *
 * <p>日常联调走 nginx / Vite 的同源代理（/api → 8080）不需要它，但一旦前端
 * 把接口基址改成直连后端（如 http://localhost:8080，或调试时指向另一个后端实例），
 * 浏览器就会按同源策略拦截——请求头带 Authorization 时还会先发 OPTIONS 预检。
 * 未配置时表现为「无法连接后端服务」，但后端日志里看不到任何请求。
 *
 * <p>这里只放开本机开发端口（localhost / 127.0.0.1 的任意端口），
 * 与 WebSocket 端点的 setAllowedOriginPatterns 保持一致；
 * 生产部署请改为具体域名。
 *
 * <p>注意：{@code TokenFilter} 会对预检请求直接返回，不走 MVC 的 CORS 处理链，
 * 因此该过滤器内也会补写响应头（见 TokenFilter#applyCorsHeaders）。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*", "https://localhost:*", "https://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}