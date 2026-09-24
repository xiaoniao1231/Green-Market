package org.web03.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.web03.pojo.Messages.WsMessage;
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
        // 最后收到任意客户端帧的时间（含自动心跳）—— 用于判断连接是否还活着
        volatile long lastActiveAt;
        /* 最后一次「真实用户操作」的时间（前端监听鼠标 / 键盘等交互，节流后上报 ACTIVE 帧）。
           「在线 / 离开」的判定只看这个值：心跳照常、页面还开着，但人很久没动了 → 离开 */
        volatile long lastUserActiveAt;
        // 是否收到客户端心跳
        volatile boolean heartbeatSeen;
        // 当前状态缓存（只会是 ONLINE / AWAY；离线即从表中移除），用于检测状态迁移并广播
        volatile String status = STATUS_ONLINE;

        OnlineSession(WebSocketSession session) {
            this.session = session;
            long now = System.currentTimeMillis();
            this.lastActiveAt = now;
            this.lastUserActiveAt = now;
        }

        void touch() {
            this.lastActiveAt = System.currentTimeMillis();
        }

        /** 用户真实操作：刷新「离开」判定的基准时间 */
        void markUserActive() {
            this.lastUserActiveAt = System.currentTimeMillis();
        }
    }

    //在线会话表
    private static final Map<String, OnlineSession> ONLINE_SESSIONS = new ConcurrentHashMap<>();

    private static final ObjectMapper OBJECT_MAPPER =  new ObjectMapper();

    //单帧发送超时（毫秒）与发送缓冲上限（字节）
    private static final int SEND_TIME_LIMIT = 5000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    /* 心跳超时（毫秒）：超过该时长没收到任何客户端帧 → 连接判定已死，主动断开（离线）。
       可用 application.yml 的 app.presence.heartbeat-timeout-ms 覆盖。 */
    @Value("${app.presence.heartbeat-timeout-ms:90000}")
    private long heartbeatTimeoutMs;

    /* 离开阈值（毫秒）：连接还活着（心跳正常、既没退出页面也没退出登录），
       但这么久都没有任何「用户操作」→ 状态由 ONLINE 变为 AWAY（离开）。
       注意与心跳的区别：心跳只能证明页面还开着，证明不了人还在。
       默认 5 分钟；联调时可临时调小（例如 6000）快速验证状态流转。 */
    @Value("${app.presence.away-timeout-ms:300000}")
    private long awayTimeoutMs;

    /** 在线状态取值：ONLINE 在线 / AWAY 离开 / OFFLINE 离线（离线即已不在线表里，不缓存） */
    public static final String STATUS_ONLINE = "ONLINE";
    public static final String STATUS_AWAY = "AWAY";
    public static final String STATUS_OFFLINE = "OFFLINE";
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
        log.info("用户 [{}] 上线（状态 ONLINE），在线表 {} 人", userId, ONLINE_SESSIONS.size());
        // 入表之后再广播
        broadcastPresence(userId, STATUS_ONLINE);
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
        log.info("用户 [{}] 下线（{}，状态 OFFLINE），在线表 {} 人", userId, status, ONLINE_SESSIONS.size());
        if (wentOffline[0]) {
            // 出表之后再广播
            broadcastPresence(userId, STATUS_OFFLINE);
        }
    }

    //处理客户端帧
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

        OnlineSession entry = currentEntry(session);
        if (entry != null) {
            entry.touch();
        }
        /* 客户端两种帧：
           · PING   —— 保活心跳（前端每 30s 自动发）：只证明「页面还开着」，不刷新用户活动时间；
           · ACTIVE —— 用户真实操作（前端监听鼠标 / 键盘等交互，节流 20s 上报一次）：刷新离开判定基准。
           其余客户端帧暂不处理。 */
        try {
            Map<?, ?> payload = OBJECT_MAPPER.readValue(message.getPayload(), Map.class);
            String type = payload.get("type") == null ? "" : String.valueOf(payload.get("type"));
            if ("PING".equals(type)) {
                if (entry != null) {
                    entry.heartbeatSeen = true; // 首次收到心跳后才对该连接启用超时判定
                }
                sendRaw(session, "{\"type\":\"PONG\"}");
            } else if ("ACTIVE".equals(type)) {
                if (entry != null) {
                    entry.markUserActive();
                }
            }
        } catch (Exception e) {
            log.debug("忽略无法解析的客户端帧: {}", e.getMessage());
        }
    }

    //清理僵死连接 + 检测「在线 / 离开」状态迁移（巡检间隔可用 app.presence.sweep-interval-ms 覆盖）
    @Scheduled(fixedDelayString = "${app.presence.sweep-interval-ms:30000}")
    public void sweepDeadSessions() {
        long now = System.currentTimeMillis();

        /* 1) 先回收心跳超时的僵死连接（连接层面）。
              必须先于状态迁移：否则一个已经收不到任何帧的连接可能先被判成「离开」并广播，
              紧接着同一轮又被摘表广播「离线」，对外表现为状态来回闪烁。
              判定前提是「收到过心跳」（heartbeatSeen）——从没发过 PING 的旧客户端不参与超时回收。 */
        ONLINE_SESSIONS.forEach((userId, entry) -> {
            if (!entry.heartbeatSeen) {
                return;
            }
            long idle = now - entry.lastActiveAt;
            if (idle <= heartbeatTimeoutMs) {
                return;
            }
            log.warn("用户 [{}] 心跳超时（{}s 未收到任何客户端帧，含自动心跳），主动断开连接", userId, idle / 1000);
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
            broadcastPresence(userId, STATUS_OFFLINE);
        });

        /* 2) 再对**仍然活着**的连接做「在线 ↔ 离开」迁移。
              为什么挂机不会被上一步判成离线：心跳是前端**定时自动**发的（每 30s，与用户是否操作无关），
              它会持续刷新 lastActiveAt，所以 90s 的存活超时根本不会触发；
              而离开判定只看 lastUserActiveAt（只由 ACTIVE 帧刷新）。
              两个时钟互不干扰 —— 这正是「挂了 5 分钟还在线表里、只是变成离开」成立的原因。
              变化的账号先收集再统一广播一次快照（逐个广播会重复推送完整名单）。 */
        List<String> changed = new ArrayList<>();
        ONLINE_SESSIONS.forEach((userId, entry) -> {
            String next = statusOf(entry, now);
            if (!next.equals(entry.status)) {
                String prev = entry.status;
                entry.status = next;
                changed.add(userId);
                log.info("用户 [{}] 状态变化: {} → {}（已 {}s 无用户操作）",
                        userId, prev, next, (now - entry.lastUserActiveAt) / 1000);
            }
        });
        if (!changed.isEmpty()) {
            String first = changed.get(0);
            OnlineSession firstEntry = ONLINE_SESSIONS.get(first);
            broadcastPresence(first, firstEntry == null ? STATUS_OFFLINE : firstEntry.status, changed);
        }
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

    /** 计算某个会话当前的状态：连接活着且近期有用户操作 → ONLINE，否则 AWAY */
    private String statusOf(OnlineSession entry, long now) {
        return (now - entry.lastUserActiveAt <= awayTimeoutMs) ? STATUS_ONLINE : STATUS_AWAY;
    }

    /** 在线账号集合快照（GET /users/online 使用）：只含「近期有用户操作」的账号 */
    public Set<String> onlineUsers() {
        Set<String> users = new LinkedHashSet<>();
        long now = System.currentTimeMillis();
        ONLINE_SESSIONS.forEach((userId, entry) -> {
            if (entry.session.isOpen() && STATUS_ONLINE.equals(statusOf(entry, now))) {
                users.add(userId);
            }
        });
        return users;
    }

    /** 离开账号集合快照：连接还在（没退出页面、没退出登录），但已长时间没有任何用户操作 */
    public Set<String> awayUsers() {
        Set<String> users = new LinkedHashSet<>();
        long now = System.currentTimeMillis();
        ONLINE_SESSIONS.forEach((userId, entry) -> {
            if (entry.session.isOpen() && STATUS_AWAY.equals(statusOf(entry, now))) {
                users.add(userId);
            }
        });
        return users;
    }

    /**
     * 账号 → 在线状态映射（只含当前有连接的账号，值为 ONLINE / AWAY）。
     *
     * <p>状态按「当前时间」实时计算，而不是读 {@code entry.status} 缓存：
     * 缓存由 sweep 每 30 秒刷新一次，读缓存最坏会晚 30 秒才反映出「离开」。
     */
    public Map<String, String> userStatusMap() {
        Map<String, String> statusMap = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        ONLINE_SESSIONS.forEach((userId, entry) -> {
            if (entry.session.isOpen()) {
                statusMap.put(userId, statusOf(entry, now));
            }
        });
        return statusMap;
    }

    /** 用户是否「在线」（含离开：只要连接还在，就仍属于在线表成员） */
    public boolean isOnline(String userId) {
        OnlineSession entry = ONLINE_SESSIONS.get(userId);
        return entry != null && entry.session.isOpen();
    }

    /**
     * 在线状态广播（接口文档 5.4）：
     * {type:"PRESENCE", message:{onlineUsers, awayUsers, onlineCount, awayCount, userStatus,
     *                            changedUserId, changedStatus, changedOnline}}
     *
     * <p>推的是<b>全量</b>快照而非增量：丢一帧也能被下一次广播自动纠正，
     * 前端只需用快照覆盖本地状态，无需维护增删。
     *
     * @param changedUserId 本次状态发生变化的账号（定时刷新快照时传列表里的第一个）
     * @param changedStatus 变化后的状态：ONLINE / AWAY / OFFLINE
     */
    public void broadcastPresence(String changedUserId, String changedStatus) {
        broadcastPresence(changedUserId, changedStatus, null);
    }

    /**
     * 同上，额外带上本次一起变化的账号列表（「在线 ↔ 离开」迁移可能一次涉及多人）。
     *
     * @param changedUsers 本次发生状态变化的账号列表；只用于前端提示 / 排错，可为 null
     */
    public void broadcastPresence(String changedUserId, String changedStatus, List<String> changedUsers) {
        Map<String, String> statusMap = userStatusMap();
        List<String> users = new ArrayList<>();
        List<String> away = new ArrayList<>();
        statusMap.forEach((id, status) -> {
            if (STATUS_AWAY.equals(status)) away.add(id);
            else users.add(id);
        });
        Map<String, Object> message = new HashMap<>();
        message.put("onlineUsers", users);
        message.put("onlineCount", users.size());
        /* 离开：页面还开着、心跳正常，但已长时间没有任何用户操作 */
        message.put("awayUsers", away);
        message.put("awayCount", away.size());
        message.put("userStatus", statusMap);
        message.put("changedUserId", changedUserId);
        message.put("changedStatus", changedStatus);
        // 兼容旧字段：只有真正在线才算 true（离开不算在线）
        message.put("changedOnline", STATUS_ONLINE.equals(changedStatus));
        if (changedUsers != null) message.put("changedUsers", changedUsers);
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
            log.info("用户 [{}] 的连接发送失败，已从在线表移除（状态 OFFLINE），在线表 {} 人", userId, ONLINE_SESSIONS.size());
            broadcastPresence(userId, STATUS_OFFLINE);
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


