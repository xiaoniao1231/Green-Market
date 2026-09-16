/* =========================================================
   青集市 · api.js —— 接口访问层
   · 所有页面只调用本文件的业务方法，不直接 fetch。
   · 聊天/用户接口（strict=true）直接请求真实后端（Spring Boot）；
   · 商城预留接口在 404/501（后端未实现）时自动使用本地演示数据，
     保证预留页面可正常浏览。
   ========================================================= */
import QM_CFG from './config.js';
import QM_MOCK from './mock.js';
import QM_STORE from './store.js';
import { uploadToOss } from './oss.js';

class HttpError extends Error {
  constructor(message) { super(message); this.name = 'HttpError'; }
}

function noteOnline(online) {
  if (QM_API.online === online) return;
  QM_API.online = online;
  QM_STORE.emit('backend', online);
}

  /** 基础 HTTP 请求：网络不可达时抛 HttpError，其余返回 {status, ok, json} */
  async function http(method, path, { query, body, token, timeout } = {}) {
    let url;
    const raw = String(QM_CFG.API_BASE).replace(/\/+$/, '') + path;
    try {
      url = new URL(raw); // 绝对基址（如 http://localhost:8080）可直接解析
    } catch (e) {
      /* 相对基址（默认 /api，同源经 nginx/Vite 反代到 8080）：
         new URL 无法解析相对串，须以当前页面源为 base 补成绝对地址再发请求 */
      try { url = new URL(raw, window.location.origin); }
      catch (e2) { throw new HttpError('接口地址格式错误'); }
    }
    if (query) Object.keys(query).forEach(k => {
      const v = query[k];
      if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, v);
    });
    const controller = new AbortController();
    /* 上传等长耗时请求可自带超时（entry.timeout），否则用全局默认值 */
    const timer = setTimeout(() => controller.abort(), timeout || QM_CFG.TIMEOUT);
    const headers = {};
    if (token) headers['Authorization'] = 'Bearer ' + token;
    let payload;
    if (body instanceof FormData) payload = body;
    else if (body !== undefined) { headers['Content-Type'] = 'application/json'; payload = JSON.stringify(body); }
    let response;
    try {
      response = await fetch(url.toString(), { method, headers, body: payload, signal: controller.signal });
    } catch (e) {
      throw new HttpError('无法连接后端服务');
    } finally {
      clearTimeout(timer);
    }
    let json = null;
    try { json = await response.json(); } catch (e) { /* 非 JSON 响应（如文件流/网关错误页） */ }
    /* 前后端分离：网关在后端不可达时会返回 502/503/504；
       Vite dev 代理断链时返回的是「空体 500」（无 JSON 体）。
       两者都等价于「后端未启动」，必须抛出，否则上层会把网关错误误判为
       “后端已连接”——顶部健康胶囊变绿、聊天页 loadRealData 抛错被静默吞掉。 */
    const gatewayDown = response.status === 502 || response.status === 503 || response.status === 504
      || (response.status === 500 && json === null);
    if (gatewayDown) {
      throw new HttpError('后端服务暂不可用（网关错误 ' + response.status + '）');
    }
    return { status: response.status, ok: response.ok, json };
  }

  /**
   * 鉴权失败判定：后端返回 HTTP 401/403，或以 Result 结构返回 code=0 且 msg 提示
   * 「未登录 / 未授权 / 令牌无效或已过期」等。命中时清除本地登录态并通知页面引导重新登录。
   */
  function isAuthFailure(res, entry) {
    if (!entry || !entry.token) return false;                    // 本请求未携带令牌，不在此处理
    if (!QM_STORE.state || !QM_STORE.state.user) return false;   // 已处于未登录态
    if (res.status === 401 || res.status === 403) return true;
    if (res.json && res.json.code === 0) {
      const msg = String(res.json.msg || '');
      return /未登录|未授权|未登录或|请先?登录|请登录后|令牌|凭证|登录已过期|登录状态|(token|令牌).*(无效|失效|过期)|(无效|失效|过期).*(token|令牌)/i.test(msg);
    }
    return false;
  }

  /** 会话失效统一处理：清空本地登录态并广播 auth-expired（由 app.js 负责提示 + 引导重新登录） */
  function handleSessionExpired(msg) {
    QM_STORE.user.expire(msg || '登录状态已失效，请重新登录');
  }

  /**
   * 通用调用：strict=true 的接口（聊天相关，后端已实现）在服务可达时会抛出真实业务错误；
   * strict=false 的接口（商城预留接口）在 404/501 等情况下自动使用本地演示数据。
   */
  async function call(entry, mockFn, { strict = false } = {}) {
    let res;
    try {
      res = await http(entry.method, entry.path, entry);
      noteOnline(true);
    } catch (e) {
      // 网络不可达 → 后端未启动
      noteOnline(false);
      if (strict) throw new Error('无法连接后端服务，请确认 Spring Boot 已启动');
      if (!QM_CFG.FALLBACK_MOCK) throw new Error('无法连接后端服务');
      await QM_MOCK.delay();
      return mockFn();
    }
    // 令牌校验（后端侧）：令牌缺失 / 无效 / 过期 → 清除本地登录态
    if (isAuthFailure(res, entry)) handleSessionExpired(res.json && res.json.msg);
    if (res.ok && res.json && res.json.code === 1) {
      return res.json.data;
    }
    if (res.ok && res.json && res.json.code === 0) {
      throw new Error(res.json.msg || '请求未成功');
    }
    // HTTP 错误（404：预留接口未实现；500：服务异常…）
    if (strict) throw new Error(res.json && res.json.msg ? res.json.msg : '后端返回错误（' + res.status + '）');
    await QM_MOCK.delay();
    return mockFn();
  }

  /**
   * 统一「发送文件」的返回结构，屏蔽后端实现差异：
   * 后端理想返回 { msgId, fileName, fileSize, fileUrl, sendTime }；
   * 兼容只返回 OSS 地址（字符串，或 { url: '...' }）的简易实现。
   * 返回的 fileUrl 即数据库里存的那条 OSS 地址，前端据此渲染可下载的文件气泡。
   */
  function normalizeFileResult(data, file, fallbackUrl) {
    if (typeof data === 'string') data = { fileUrl: data };
    const d = data || {};
    return {
      msgId: d.msgId || d.id || '',
      /* 后端有的实现把文件名放在 fileName，有的复用 content 列存文件名 */
      fileName: d.fileName || d.content || file.name,
      fileSize: (d.fileSize !== undefined && d.fileSize !== null) ? d.fileSize : file.size,
      fileUrl: d.fileUrl || d.url || fallbackUrl || '',
      sendTime: d.sendTime || ''
    };
  }

  /** 上传失败信息友好化：把网关 / 参数类 HTTP 错误翻译成可操作的提示 */
  function formatUploadError(e) {
    const msg = String((e && e.message) || '文件发送失败');
    if (/413/.test(msg)) return new Error('文件太大被网关拒绝：请调大 nginx 的 client_max_body_size 与后端 multipart 限制');
    if (/415|400/.test(msg)) return new Error('文件上传参数有误：后端需要 multipart 字段 file + receiverId（接口未按教程改造？）');
    return (e instanceof Error) ? e : new Error(msg);
  }

  const state = { online: null };
  const tokenOf = () => (QM_STORE.state.user && QM_STORE.state.user.token) || '';

  const QM_API = {
    get online() { return state.online; },
    set online(v) { state.online = v; },

    /** 健康探测：能收到任何 HTTP 响应即视为后端在线。
        探活路径必须选 TokenFilter 白名单内的公开接口：
        原来探测的 /users/online 需要鉴权，未携带令牌会被后端判 401
        并打印「未携带令牌」的 INFO 日志 —— 每次刷新页面、每次打开首页
        都会刷一条，污染后端日志。/products 属于白名单公开前缀，不会触发鉴权。 */
    async health() {
      try {
        await http('GET', '/products', {});
        noteOnline(true);
        return true;
      } catch (e) {
        noteOnline(false);
        return false;
      }
    },

    /* ================= 商品模块（后端预留 → 自动演示数据） ================= */
    products: {
      list(opts = {}) {
        return call(
          { name: '商品列表', method: 'GET', path: '/products', query: { page: opts.page || 1, size: opts.size || 20, category: opts.category, sub: opts.sub, sort: opts.sort } },
          () => {
            let list = opts.category ? QM_MOCK.byCategory(opts.category, opts.sub) : QM_MOCK.products.slice();
            if (opts.sort) list = QM_MOCK.sortProducts(list, opts.sort);
            return QM_MOCK.paginate(list, opts.page || 1, opts.size || 20);
          }
        );
      },
      get(id) {
        return call(
          { name: '商品详情', method: 'GET', path: '/products/' + encodeURIComponent(id) },
          () => {
            const p = QM_MOCK.byId(id);
            if (!p) throw new Error('商品不存在');
            return p;
          }
        );
      },
      search(q, opts = {}) {
        return call(
          { name: '商品搜索', method: 'GET', path: '/products/search', query: { q, page: opts.page || 1, size: opts.size || 20, sort: opts.sort } },
          () => {
            let list = QM_MOCK.search(q);
            if (opts.sort) list = QM_MOCK.sortProducts(list, opts.sort);
            return QM_MOCK.paginate(list, opts.page || 1, opts.size || 20);
          }
        );
      },
      flash() {
        return call(
          { name: '限时秒杀', method: 'GET', path: '/home/flash' },
          () => ({ endTime: QM_MOCK.getFlashEnd(), list: QM_MOCK.flashIds.map(QM_MOCK.byId).filter(Boolean) })
        );
      },
      recommend(opts = {}) {
        return call(
          { name: '猜你喜欢', method: 'GET', path: '/home/recommend', query: { page: opts.page || 1, size: opts.size || 15 } },
          () => QM_MOCK.paginate(QM_MOCK.products.slice(), opts.page || 1, opts.size || 15)
        );
      },
      related(id, size = 5) {
        return call(
          { name: '相关推荐', method: 'GET', path: '/products/' + encodeURIComponent(id) + '/related', query: { size } },
          () => {
            const p = QM_MOCK.byId(id);
            const same = QM_MOCK.byCategory(p ? p.category : '数码科技').filter(x => x.id !== id);
            const rest = QM_MOCK.products.filter(x => x.id !== id && !same.includes(x));
            return (same.concat(rest)).slice(0, size);
          }
        );
      }
    },

    /* ================= 购物车（后端预留 → 本地存储演示） ================= */
    cart: {
      list() {
        return call(
          { name: '购物车列表', method: 'GET', path: '/cart', query: {}, token: tokenOf() },
          () => QM_STORE.cart.list()
        );
      },
      add(productId, skuText, qty) {
        return call(
          { name: '加入购物车', method: 'POST', path: '/cart', body: { productId, skuText, quantity: qty }, token: tokenOf() },
          () => { QM_STORE.cart.add(productId, skuText, qty); return { added: true }; }
        );
      },
      update(itemKey, qty) {
        return call(
          { name: '修改数量', method: 'PUT', path: '/cart/items/' + encodeURIComponent(itemKey), body: { quantity: qty }, token: tokenOf() },
          () => { QM_STORE.cart.setQty(itemKey, qty); return { updated: true }; }
        );
      },
      remove(itemKeys) {
        return call(
          { name: '删除购物车', method: 'DELETE', path: '/cart/items', body: { itemKeys }, token: tokenOf() },
          () => { QM_STORE.cart.remove(itemKeys); return { removed: itemKeys.length }; }
        );
      }
    },

    /* ================= 订单（后端预留 → 本地存储演示） ================= */
    orders: {
      list(status) {
        return call(
          { name: '订单列表', method: 'GET', path: '/orders', query: { status }, token: tokenOf() },
          () => QM_STORE.orders.list(status)
        );
      },
      create(payload) {
        return call(
          { name: '创建订单', method: 'POST', path: '/orders', body: payload, token: tokenOf() },
          () => QM_STORE.orders.create(payload)
        );
      },
      pay(orderId) {
        return call(
          { name: '订单支付', method: 'POST', path: '/orders/' + encodeURIComponent(orderId) + '/pay', body: {}, token: tokenOf() },
          () => { QM_STORE.orders.pay(orderId); return { paid: true }; }
        );
      },
      cancel(orderId) {
        return call(
          { name: '取消订单', method: 'POST', path: '/orders/' + encodeURIComponent(orderId) + '/cancel', body: {}, token: tokenOf() },
          () => { QM_STORE.orders.cancel(orderId); return { canceled: true }; }
        );
      },
      confirm(orderId) {
        return call(
          { name: '确认收货', method: 'POST', path: '/orders/' + encodeURIComponent(orderId) + '/confirm', body: {}, token: tokenOf() },
          () => { QM_STORE.orders.confirm(orderId); return { confirmed: true }; }
        );
      }
    },

    /* ================= 收藏（后端预留 → 本地存储演示） ================= */
    favorites: {
      list() {
        return call(
          { name: '收藏列表', method: 'GET', path: '/favorites', query: {}, token: tokenOf() },
          () => QM_STORE.fav.list()
        );
      },
      toggle(productId) {
        return call(
          { name: '收藏/取消收藏', method: 'POST', path: '/favorites', body: { productId }, token: tokenOf() },
          () => ({ favorited: QM_STORE.fav.toggle(productId) })
        );
      }
    },

    /* ================= 用户认证 / 聊天（后端已实现，strict） ================= */
    auth: {
      /* 登录接口返回：Result.success(LoginInfo) → data = { id, username, name, token }，
         密令(token) 与登入用户信息同处一个 data 对象，前端整份使用、不拆开。
         登录成功后由 ui.js 用 jwt.js 对 token 做本地校验（结构 / exp / 可选 HS256 签名），
         校验通过才写入本地登录态；校验不通过视为登录失败。 */
      /* 账号登录：POST /login，body { userId, password } */
      async login({ userId, password }) {
        /* strict：后端未启动时直接报错 */
        return call(
          { name: '账号登录', method: 'POST', path: '/login', body: { userId, password } },
          null,
          { strict: true }
        );
      },
      /* 手机号登录：POST /login/phone，body { phone, smsCode }（验证码由 /sms-code scene=login 获取），
         返回结构 data = { id, username, name, token } 与账号登录一致 */
      async loginByPhone(phone, smsCode) {
        return call(
          { name: '手机号登录', method: 'POST', path: '/login/phone', body: { phone, smsCode } },
          null,
          { strict: true }
        );
      },
      async register(payload) {
        return call(
          { name: '用户注册', method: 'POST', path: payload.phone ? '/register/phone' : '/register', body: payload },
          () => { throw new Error('后端未启动，注册功能不可用'); },
          { strict: true }
        );
      },
      async smsCode(phone, scene) {
        return call(
          { name: '发送验证码', method: 'POST', path: '/sms-code', body: { phone, scene } },
          () => { throw new Error('后端未启动，验证码功能不可用'); },
          { strict: true }
        );
      },
      async logout() {
        try { await http('POST', '/logout', { token: tokenOf() }); } catch (e) { /* 忽略 */ }
      }
    },

    chat: {
      onlineUsers() {
        return call(
          { name: '在线用户', method: 'GET', path: '/users/online', query: {}, token: tokenOf() },
          () => ({ onlineCount: QM_MOCK.contacts.filter(c => c.online).length, onlineUsers: QM_MOCK.contacts.filter(c => c.online).map(c => c.id) }),
          { strict: true }
        );
      },
      friends() {
        /* 好友体系已下线（friends 表已删除、后端无该接口、前端无调用方），
           保留本方法仅为兼容旧调用点：直接返回空列表，不再打后端 404。 */
        return Promise.resolve({ list: [] });
      },
      history(peerId, page = 1, size = 50) {
        return call(
          { name: '历史消息', method: 'GET', path: '/messages/history', query: { peerId, page, size }, token: tokenOf() },
          () => {
            const msgs = (QM_STORE.chat.messages(peerId) || []);
            const start = (page - 1) * size;
            return { total: msgs.length, page, size, list: msgs.slice(start, start + size).map(m => ({ msgId: m.id, senderId: m.from === 'me' ? 'me' : m.from, receiverId: peerId, mesType: m.type === 'file' ? 'FILE_MES' : 'COMM_MES', content: m.content, sendTime: new Date(m.time).toLocaleString('zh-CN'), recalled: false })) };
          },
          { strict: true }
        );
      },
      /* 我的会话列表：登录后调用，把后端库里的会话（对端 + 最后一条消息）重建到本地，
         否则本地存储一旦清空（重新登录 / 关闭标签页），消息中心就是空白 */
      conversations() {
        return call(
          { name: '会话列表', method: 'GET', path: '/messages/conversations', query: {}, token: tokenOf() },
          () => [],
          { strict: true }
        );
      },
      /* 标记会话已读：打开会话 / 正在查看时收到新消息时调用，服务端记录 last_read_time */
      read(peerId) {
        return call(
          { name: '标记已读', method: 'POST', path: '/messages/read', body: { peerId }, token: tokenOf() },
          () => null,
          { strict: true }
        );
      },
      send(receiverId, content) {
        return call(
          { name: '发送消息', method: 'POST', path: '/messages/private', body: { receiverId, content, msgId: ('m-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8)) }, token: tokenOf() },
          () => {
            QM_STORE.chat.push(receiverId, { from: 'me', type: 'text', content });
            return { msgId: 'm-' + Date.now(), sendTime: new Date().toLocaleString('zh-CN'), delivered: true };
          },
          { strict: true }
        );
      },
      /* 发送文件（文件最终都存在阿里云 OSS，数据库 messages.file_url 存的就是 OSS 地址）：
         上传方式由 QM_CFG.UPLOAD_MODE 决定（见 core/config.js）：
         · 'server'（默认）—— 后端转发：文件以 multipart 提交给 POST /messages/file，
           后端调用 AliyunOSSOperator 上传 OSS，得到地址后以 FILE_MES 落库、WebSocket 推送、返回地址；
           注意：FormData 由 http() 直接作为 body，Content-Type 交给浏览器自动生成
           （手动设置会丢失 boundary，后端必然解析失败）。
         · 'oss-sts' —— 前端直传：GET /oss/sts 取临时凭证 → 浏览器直传 OSS（core/oss.js）
           → 把 OSS 地址回传后端 /messages/file 登记。 */
      async sendFile(file, receiverId) {
        if (!file) throw new Error('文件不能为空');
        const max = QM_CFG.UPLOAD_MAX_SIZE || 0;
        if (max && file.size > max) {
        const fmt = (b) => b >= 1024*1024*1024 ? (b/1024/1024/1024).toFixed(1)+'GB' : (b/1024/1024).toFixed(1)+'MB';
        throw new Error('文件不能超过 ' + fmt(max) + '（当前 ' + fmt(file.size) + '）');
        }
        if (!receiverId) throw new Error('接收方账号不能为空');

        if (QM_CFG.UPLOAD_MODE === 'oss-sts') {
          const { url } = await uploadToOss(file);
          let data;
          try {
            data = await call(
              { name: '发送文件', method: 'POST', path: '/messages/file', timeout: QM_CFG.UPLOAD_TIMEOUT, body: { fileUrl: url, fileName: file.name, fileSize: file.size, receiverId }, token: tokenOf() },
              () => { throw new Error('后端未启动，文件发送不可用'); },
              { strict: true }
            );
          } catch (e) { throw formatUploadError(e); }
          return normalizeFileResult(data, file, url);
        }

        /* 默认：后端转发（multipart/form-data，字段名 file + receiverId，与 MessagesController 一致） */
        const form = new FormData();
        form.append('file', file, file.name);
        form.append('receiverId', receiverId);
        let data;
        try {
          data = await call(
            { name: '发送文件', method: 'POST', path: '/messages/file', timeout: QM_CFG.UPLOAD_TIMEOUT, body: form, token: tokenOf() },
            () => { throw new Error('后端未启动，文件发送不可用'); },
            { strict: true }
          );
        } catch (e) { throw formatUploadError(e); }
        return normalizeFileResult(data, file, '');
      },
      connectWebSocket(onMessage, onClose) {
        if (!state.online) return null;
        const token = tokenOf();
        if (!token) return null;
        const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
        /* 前后端分离：API_BASE 为相对路径（/api，经 nginx 反代）时，
           走当前站点同源地址 + /ws；为绝对地址（直连后端，接口调试面板可改）时按原逻辑转换协议。
           注意：替换协议时必须补回 "://"，否则会拼出 "ws//host" 这种非法地址
           （浏览器会把 "ws//…" 当相对路径解析成 ws://当前站点/ws//host/ws，连接必然失败）。 */
        let base = String(QM_CFG.API_BASE);
        if (base.startsWith('/')) base = protocol + '://' + location.host;
        else base = base.replace(/^https?:\/\//, protocol + '://');
        try {
          const socket = new WebSocket(base.replace(/\/+$/, '') + QM_CFG.WS_PATH + '?token=' + encodeURIComponent(token));
          socket.onmessage = ev => { try { onMessage(JSON.parse(ev.data)); } catch (e) { /* 忽略异常帧 */ } };
          socket.onclose = () => onClose && onClose();
          return socket;
        } catch (e) {
          return null;
        }
      }
    }
  };

export default QM_API;
