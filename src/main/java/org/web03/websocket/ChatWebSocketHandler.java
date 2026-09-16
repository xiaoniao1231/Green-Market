package org.web03.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.web03.pojo.WsMessage;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * 聊天 WebSocket 处理器：
 * 1. 维护在线会话表（userId -> 连接 + 最后活跃时间），供私聊推送、在线判断与 GET /users/online 使用；
 * 2. 心跳：客户端 {"type":"PING"} → 服务端 {"type":"PONG"}；并定时回收心跳超时的僵死连接；
 * 3. pushTo(receiverId, type, message) 推送统一帧 {type, message}（接口文档 5.1.3）；
 * 4. broadcastPresence(...) 在有人上线 / 下线时向所有在线用户广播在线状态快照（接口文档 5.4）。
 */

@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    /**
     * 在线连接 = 会话 + 最后活跃时间。
     * 封装成一个对象而不是用两个 Map，避免"连接表"与"时间表"在多端换端、异常路径下不同步。
     */
    private static final class OnlineSession{
        final WebSocketSession session;
        // 最后活跃时间
        volatile long lastActiveAt;
        // 是否收到客户端心跳
        volatile boolean heartbeatSeen;

        OnlineSession(WebSocketSession session) {
            this.session = session;
            this.lastActiveAt = System.currentTimeMillis();
        }

        void touch() {
            this.lastActiveAt = System.currentTimeMillis();
        }
    }

    //在线会话表
    private static final Map<String, OnlineSession> ONLINE_SESSIONS = new ConcurrentHashMap<>();

    private static final ObjectMapper OBJECT_MAPPER =  new ObjectMapper();

    //单帧发送超时（毫秒）与发送缓冲上限（字节）
    private static final int SEND_TIME_LIMIT = 5000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    //心跳超时阈值（毫秒）
    private static final long HEARTBEAT_TIMEOUT_MS = 90_000L;
    //清理任务执行间隔
    private static final long SWEEP_INTERVAL_MS = 30_000L;
    //业务自定义关闭码（4000-4999 为应用保留区间）
    private static final CloseStatus HEARTBEAT_TIMEOUT_STATUS = new CloseStatus(4000, "heartbeat timeout");

    //上线广播
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId == null) {
            // 握手拦截器未写入 userId（异常路径）：直接拒绝，不能把 null 键写入在线表
            log.warn("连接 [{}] 缺少 userId 属性，拒绝建立会话", session.getId());
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        //包装为并发对话
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session,SEND_TIME_LIMIT,BUFFER_SIZE_LIMIT);
        OnlineSession old = ONLINE_SESSIONS.put(userId, new OnlineSession(safe));
        if (old != null && old.session.isOpen()){
            old.session.close(CloseStatus.NORMAL);//同一个账户登录，关闭旧连接
        }
        log.info("用户 [{}] 上线，当前在线 {} 人", userId, ONLINE_SESSIONS.size());
        // 入表之后再广播
        broadcastPresence(userId, true);
    }

    //下线广播
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status){
        String userId = (String) session.getAttributes().get("userId");
        if(userId == null){
            return;
        }
        boolean[] wentOffline = { false };
        ONLINE_SESSIONS.computeIfPresent(userId, (k, v) -> {
            if (v.session.getId().equals(session.getId())) {
                wentOffline[0] = true;
                return null;
            }
            return v;
        });
        log.info("用户 [{}] 下线（{}），当前在线 {} 人", userId, status, ONLINE_SESSIONS.size());
        if (wentOffline[0]) {
            // 出表之后再广播
            broadcastPresence(userId, false);
        }
    }

    //处理客户端帧
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

        OnlineSession entry = currentEntry(session);
        if (entry != null) {
            entry.touch();
        }
        // 心跳：{"type":"PING"} → {"type":"PONG"}；其余客户端帧暂不处理
        try {
            Map<?, ?> payload = OBJECT_MAPPER.readValue(message.getPayload(), Map.class);
            if ("PING".equals(payload.get("type"))) {
                if (entry != null) {
                    entry.heartbeatSeen = true; // 首次收到心跳后才对该连接启用超时判定
                }
                sendRaw(session, "{\"type\":\"PONG\"}");
            }
        } catch (Exception e) {
            log.debug("忽略无法解析的客户端帧: {}", e.getMessage());
        }
    }

    //清理僵死连接
    @Scheduled(fixedDelay = SWEEP_INTERVAL_MS)
    public void sweepDeadSessions() {
        long now = System.currentTimeMillis();
        ONLINE_SESSIONS.forEach((userId, entry) -> {
            if (!entry.heartbeatSeen) {
                return;
            }
            long idle = now - entry.lastActiveAt;
            if (idle <= HEARTBEAT_TIMEOUT_MS) {
                return;
            }
            log.warn("用户 [{}] 心跳超时（{}s 未收到客户端帧），主动断开连接", userId, idle / 1000);
            // 先摘表再关闭：即使 close() 没能触发 afterConnectionClosed，状态也已经干净，
            // 且不会与回调里的广播重复（回调此时已找不到该 user，wentOffline 为 false）。
            boolean[] removed = { false };
            ONLINE_SESSIONS.computeIfPresent(userId, (k, v) -> {
                if (v.session.getId().equals(entry.session.getId())) {
                    removed[0] = true;
                    return null;
                }
                return v;
            });
            if (!removed[0]) {
                return; // 期间已被新连接替换，交给新连接处理
            }
            try {
                entry.session.close(HEARTBEAT_TIMEOUT_STATUS);
            } catch (Exception e) {
                log.debug("关闭超时连接失败（可能已断开）: {}", e.getMessage());
            }
            broadcastPresence(userId, false);
        });
    }

    /** 取当前 session 对应的在线记录（按 sessionId 校验，避免多端换端时拿到别人的记录） */
    private OnlineSession currentEntry(WebSocketSession session) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId == null) {
            return null;
        }
        OnlineSession entry = ONLINE_SESSIONS.get(userId);
        return (entry != null && entry.session.getId().equals(session.getId())) ? entry : null;
    }

    /** 在线账号集合快照（GET /users/online 使用） */
    public Set<String> onlineUsers() {
        Set<String> users = new LinkedHashSet<>();
        ONLINE_SESSIONS.forEach((userId, entry) -> {
            if (entry.session.isOpen()) {
                users.add(userId);
            }
        });
        return users;
    }

    /** 用户是否在线 */
    public boolean isOnline(String userId) {
        OnlineSession entry = ONLINE_SESSIONS.get(userId);
        return entry != null && entry.session.isOpen();
    }

    /**
     * 在线状态广播（接口文档 5.4）：{type:"PRESENCE", message:{onlineUsers, onlineCount, changedUserId, changedOnline}}
     *
     * <p>推的是<b>全量</b>在线账号快照而非增量：丢一帧也能被下一次广播自动纠正，
     * 前端只需用 Set 覆盖本地状态，无需维护增删。
     */
    public void broadcastPresence(String changedUserId, boolean changedOnline) {
        List<String> users = new ArrayList<>(onlineUsers());
        Map<String, Object> message = new HashMap<>();
        message.put("onlineUsers", users);
        message.put("onlineCount", users.size());
        message.put("changedUserId", changedUserId);
        message.put("changedOnline", changedOnline);
        try {
            String frame = OBJECT_MAPPER.writeValueAsString(
                    Map.of("type", "PRESENCE", "message", message));
            // ConcurrentHashMap.forEach 是弱一致遍历：遍历中增删不会抛异常；
            // 每个 session 又是并发安全的 decorator，所以这里可以直接广播。
            // 注意广播在握手 / 关闭 / 定时任务的线程里同步执行，在线人数很多时应改为异步投递。
            // 并发安全的 decorator 发送超时不会立刻断开连接，而广播会持续尝试并不断堆积缓冲，
            // 因此这里把 userId 一并带上，由 sendRaw 在发送失败时从在线表摘除（见 evictDeadSession）
            ONLINE_SESSIONS.forEach((userId, entry) -> sendRaw(entry.session, frame, userId));
        } catch (Exception e) {
            log.error("广播在线状态失败: {}", e.getMessage());
        }
    }

    /** 底层发送：统一 catch，单个连接失败不影响其它连接 */
    private void sendRaw(WebSocketSession session, String frame) {
        sendRaw(session, frame, null);
    }

    /**
     * 底层发送：统一 catch，单个连接失败不影响其它连接。
     *
     * @param userId 该连接对应的账号；非空时发送失败会从在线表摘除并广播离线（避免僵尸连接一直占用在线名额）
     */
    private void sendRaw(WebSocketSession session, String frame, String userId) {
        if (session == null) {
            return;
        }
        if (!session.isOpen()) {
            if (userId != null) {
                evictDeadSession(userId, session);
            }
            return;
        }
        try {
            session.sendMessage(new TextMessage(frame));
        } catch (Exception e) {
            log.debug("发送失败（连接可能已断开）: {}", e.getMessage());
            if (userId != null) {
                evictDeadSession(userId, session);
            }
        }
    }

    /** 从在线表摘除指定的失效连接（按 sessionId 精确匹配，避免误删同账号的新连接） */
    private void evictDeadSession(String userId, WebSocketSession session) {
        boolean[] removed = { false };
        ONLINE_SESSIONS.computeIfPresent(userId, (k, v) -> {
            if (v.session.getId().equals(session.getId())) {
                removed[0] = true;
                return null;
            }
            return v;
        });
        if (removed[0]) {
            log.info("用户 [{}] 的连接发送失败，已从在线表移除，当前在线 {} 人", userId, ONLINE_SESSIONS.size());
            broadcastPresence(userId, false);
        }
    }

    /**
     * 向指定用户推送统一帧 {type, message}。
     *
     * @return true 表示确实写入连接；会话不存在 / 已关闭 / 发送异常均为 false
     */
    public boolean pushTo(String receiverId, String type, WsMessage message) {
        OnlineSession entry = ONLINE_SESSIONS.get(receiverId);
        if (entry == null || !entry.session.isOpen()) {
            log.debug("用户 [{}] 不在线，跳过推送", receiverId);
            return false;
        }
        try {
            String frame = OBJECT_MAPPER.writeValueAsString(Map.of("type", type, "message", message));
            entry.session.sendMessage(new TextMessage(frame));
            return true;
        } catch (Exception e) {
            log.error("推送给用户 [{}] 失败: {}", receiverId, e.getMessage());
            return false;
        }
    }
}


