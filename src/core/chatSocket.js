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

/* 用户活动上报：心跳只能证明「页面还开着」，服务端据此判断的是「连接是否存活」；
   而「在线 / 离开」要看用户有没有真的在操作 —— 所以这里监听真实交互事件，
   节流上报 {"type":"ACTIVE"}，服务端以最后一次收到 ACTIVE 的时间作为离开判定的基准。
   间隔 20s：鼠标移动这类高频事件不会刷爆连接，服务端 5 分钟无 ACTIVE 才判定离开，
   20s 的粒度完全够用。 */
const ACTIVITY_REPORT_INTERVAL = 20000;
/* 只监听「用户确实动了」的事件：鼠标移动 / 按下、键盘、滚轮、触摸、滚动。
   不使用 focus / visibilitychange 作为活动来源，切回标签页时单独补报一次（见下）。 */
const ACTIVITY_EVENTS = ['mousemove', 'mousedown', 'keydown', 'wheel', 'touchstart', 'scroll'];

let socket = null;           // 当前 WebSocket 连接
let userKey = '';            // 连接所属账号：切换账号时强制重建，避免复用上一账号的连接
let heartbeatTimer = null;
let lastActivityReportAt = 0;   // 上次上报 ACTIVE 的时间（节流用）
let activityBound = false;      // 是否已挂上全局交互监听（避免重复注册）
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

/* ---------- 用户活动上报（「离开」状态的判定依据） ---------- */

function reportActivity() {
  const now = Date.now();
  if (now - lastActivityReportAt < ACTIVITY_REPORT_INTERVAL) return;  // 节流
  lastActivityReportAt = now;
  if (socket && socket.readyState === WebSocket.OPEN) {
    try {
      socket.send(JSON.stringify({ type: 'ACTIVE' }));
    } catch (e) { /* 发送失败交由 onclose 处理 */ }
  }
}

/* 从后台标签页切回来：立即补报一次（把节流窗口重置，用户显然又在了） */
function onVisibilityChange() {
  if (document.visibilityState === 'visible') {
    lastActivityReportAt = 0;
    reportActivity();
  }
}

function bindActivity() {
  if (activityBound) return;
  activityBound = true;
  /* capture: true —— scroll 事件不冒泡，只有在捕获阶段才能被 window 收到，
     这样「在消息列表里滚动」也算用户操作（否则只有 mousemove / keydown 能刷新状态） */
  ACTIVITY_EVENTS.forEach(ev => window.addEventListener(ev, reportActivity, { passive: true, capture: true }));
  document.addEventListener('visibilitychange', onVisibilityChange);
}

function unbindActivity() {
  if (!activityBound) return;
  activityBound = false;
  // 移除时第三个参数要与添加时一致（capture 标记必须匹配），否则监听不会被摘掉
  ACTIVITY_EVENTS.forEach(ev => window.removeEventListener(ev, reportActivity, true));
  document.removeEventListener('visibilitychange', onVisibilityChange);
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
        unbindActivity();
        if (socket === created) socket = null;
        closeHandlers.forEach(fn => { try { fn(); } catch (e) { console.error(e); } });
      }
    );
    if (created) {
      socket = created;
      startHeartbeat();
      /* 连接可用：挂上用户活动监听。登录即建连，所以登录后立刻上报一次活动
         —— 刚登录的用户显然是在操作，不该一进来就显示「离开」 */
      lastActivityReportAt = 0;
      bindActivity();
      reportActivity();
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
