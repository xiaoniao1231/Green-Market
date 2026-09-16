package org.web03.websocket;


import io.jsonwebtoken.Claims;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.web03.utils.JwtUtils;

import java.util.Map;

@Slf4j
@Component
public class WsHandshakeInterceptor implements HandshakeInterceptor {
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        //取query参数token
        String token = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");
        if (!StringUtils.hasLength(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        //校验令牌
        Claims claims;
        try {
            claims = JwtUtils.parseToken(token);
        } catch (Exception e) {
            log.warn("WebSocket 握手令牌校验失败: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        //`id`：先拿到对象，转字符串再转 Integer（兼容 Jwt 解析出来数字是 Long 的坑，直接强转 Integer 会报错）
        //`userId`：直接取出字符串类型的 userId
        Object idObj = claims.get("id");
        Integer id = (idObj == null) ? null : Integer.valueOf(idObj.toString());
        String userId = claims.get("userId", String.class);

        if(id == null || !StringUtils.hasLength(userId)){
            log.warn("WebSocket 握手令牌校验失败: id 或 userId 声明不存在");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        //写入连接
        attributes.put("id", id);
        attributes.put("userId", userId);
        return true;

    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, @Nullable Exception exception) {

    }
}
