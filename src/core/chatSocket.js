/* =========================================================
   core/chatSocket.js —— 全局聊天实时通道（登录即在线，常驻保持）
   ---------------------------------------------------------
   修复背景：原实现把 WebSocket 连接放在消息中心页（ChatView）内部，
   进入页面才建立、离开页面就关闭。于是：
   · 登录后不进入消息中心 → 后端在线表（ONLINE_SESSIONS）里查无此人，
     别人看他是离线，他自己也没有任何「在线」标识；
   · 只有点进消息中心才会触发连接 → 才会上线，一离开页面又立刻下线。
   本模块把连接生命周期提升到应用级（由 App.vue 驱动）：
   · 登录成功 / 刷新页面恢复登录态 → 立即建立连接，用户即刻在线；
   · 连接跨路由常驻，离开消息中心页不会掉线；
   · 退出登录 / 令牌失效 / 后端离线 → 主动关闭，真正下线。
   ChatView 不再自建连接，改为订阅本模块的实时帧（消息 / PRESENCE）。
   ========================================================= */
import QM_API from './api.js';
import QM_STORE from './store.js';

/* 心跳：浏览器 WebSocket 不暴露协议层 ping/pong，必须用应用层帧做保活。
   服务端按「最后一次收到客户端帧的时间」判定连接是否存活，超过 90s 会主动断开并广播离线；
   反之若前端不发心跳，拔网线 / 进程被杀造成的半开连接会一直"假在线"，绿点不灭。
   间隔 30s，服务端容忍 90s（3 次丢失）。 */
const HEARTBEAT_INTERVAL = 30000;

let socket = null;           // 当前 WebSocket 连接
let userKey = '';            // 连接所属账号：切换账号时强制重建，避免复用上一账号的连接
let heartbeatTimer = null;
const messageHandlers = new Set();  // 实时帧订阅者（ChatView 等）
const closeHandlers = new Set();    // 断线订阅者（页面内提示重连等）

function startHeartbeat() {
  stopHeartbeat();
  heartbeatTimer = setInterval(() => {
    if (socket && socket.readyState === WebSocket.OPEN) {
      try {
        socket.send(JSON.stringify({ type: 'PING' }));
      } catch (e) { /* 发送失败交由 onclose 处理 */ }
    }
  }, HEARTBEAT_INTERVAL);
}

function stopHeartbeat() {
  if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null; }
}

const chatSocket = {
  /** 当前是否已建立实时连接 */
  get connected() {
    return !!(socket && socket.readyState === WebSocket.OPEN);
  },

  /** 订阅实时帧（type: PONG / PRESENCE / COMM_MES / TO_ALL / FILE_MES）；返回取消函数 */
  onMessage(fn) {
    messageHandlers.add(fn);
    return () => messageHandlers.delete(fn);
  },

  /** 订阅断线通知（连接被服务端关闭 / 网络中断时触发）；返回取消函数 */
  onClose(fn) {
    closeHandlers.add(fn);
    return () => closeHandlers.delete(fn);
  },

  /**
   * 建立（或复用）当前登录账号的实时连接。
   * 幂等：同一账号已有可用连接时直接复用，不会重复建连；
   * 连接已死 / 账号已切换时先清理再重建。
   * 无人登录或后端离线时返回 null。
   */
  async ensure() {
    const user = QM_STORE.state.user;
    const token = user && user.token;
    if (!token) return null;

    /* 后端在线状态未知 / 离线时先探测：connectWebSocket 要求 QM_API.online 为 true 才会建连 */
    if (QM_API.online === null || QM_API.online === false) {
      await QM_API.health();
    }
    if (!QM_API.online) return null;

    /* 探测期间可能已登出 / 切换账号（await 是异步边界），以最新登录态为准 */
    const current = QM_STORE.state.user;
    if (!current || !current.token || current.token !== token) return null;

    /* 同账号已有可用连接（含正在握手）：直接复用 */
    if (socket && userKey === user.userId
        && socket.readyState !== WebSocket.CLOSED && socket.readyState !== WebSocket.CLOSING) {
      return socket;
    }

    /* 账号切换 / 旧连接已死：先清理再重建 */
    stopHeartbeat();
    if (socket) { try { socket.close(); } catch (e) { /* 忽略 */ } socket = null; }
    userKey = user.userId;

    const created = QM_API.chat.connectWebSocket(
      frame => { messageHandlers.forEach(fn => { try { fn(frame); } catch (e) { console.error(e); } }); },
      () => {
        /* 断线：只清当前连接的引用（防止旧连接的 onclose 误清新连接），并通知订阅者 */
        stopHeartbeat();
        if (socket === created) socket = null;
        closeHandlers.forEach(fn => { try { fn(); } catch (e) { console.error(e); } });
      }
    );
    if (created) {
      socket = created;
      startHeartbeat();
    }
    return created;
  },

  /** 主动关闭（退出登录 / 令牌失效 / 后端离线时由 App.vue 调用） */
  close() {
    stopHeartbeat();
    if (socket) { try { socket.close(); } catch (e) { /* 忽略 */ } socket = null; }
    userKey = '';
  }
};

export default chatSocket;
