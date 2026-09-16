package org.web03.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.web03.websocket.ChatWebSocketHandler;
import org.web03.websocket.WsHandshakeInterceptor;


/**
 * WebSocket 配置：注册 /ws 端点，前端连接 ws://host/ws?token=<JWT>
 */

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Autowired
    private ChatWebSocketHandler chatWebSocketHandler;

    @Autowired
    private WsHandshakeInterceptor wsHandshakeInterceptor;

    // 注册 WebSocket 处理器和拦截器
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws")
                .addInterceptors(wsHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
