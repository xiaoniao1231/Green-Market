/* =========================================================
   青集市 · api.js —— 接口访问层
   · 所有页面只调用本文件的业务方法，不直接 fetch。
   · 聊天/用户接口（strict=true）直接请求真实后端（Spring Boot）；
   · 商品接口（products / seller）自 2026-09-17 起删除本地演示数据回退，
     统一 strict=true 严格对接后端（由后端实现 docs/店家中心商品管理接口文档.md 契约）。
   ========================================================= */
import QM_CFG from './config.js';
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
   * 通用调用：把后端的 Result 结构统一转成业务数据 / 业务错误。
   *
   * mockFn 是**本地离线回退**：目前只用于后端尚未实现的订单接口，
   * 让用户在不接后端时仍能把订单数据存到本地（存的是用户自己的操作，不是预置假数据）。
   * 后端已实现的接口一律 strict=true，出错就如实抛出，不再有任何演示数据兜底
   * —— 收藏、地址、购物车这类「服务端才是唯一真相」的数据尤其如此，静默回退只会造成假成功。
   */
  async function call(entry, mockFn, { strict = false } = {}) {
    let res;
    try {
      res = await http(entry.method, entry.path, entry);
      noteOnline(true);
    } catch (e) {
      // 网络不可达 → 后端未启动
      noteOnline(false);
      if (strict || !mockFn) throw new Error('无法连接后端服务，请确认 Spring Boot 已启动');
      if (!QM_CFG.FALLBACK_MOCK) throw new Error('无法连接后端服务');
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
    // HTTP 错误（404：后端接口尚未实现；500：服务异常…）
    if (strict || !mockFn) {
      if (res.status === 404) throw new Error('该功能后端接口尚未实现（404）');
      throw new Error(res.json && res.json.msg ? res.json.msg : '后端返回错误（' + res.status + '）');
    }
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

  /* =========================================================
     商城三模块（购物车 / 订单 / 收藏）：接口结构 ↔ 本地 store 结构 互转
     ---------------------------------------------------------
     本地 store 结构（store.js）：
       cart.item    { key, productId, sku, qty, checked, product? }
       orders.order { id, orderNo, status, createTime(millis), items[{productId,sku,qty,price,title,art}],
                      address, coupon, payMethod, remark, goodsAmount, discount, freight, total, logistics }
       favorites    productId[]
     接口结构（契约见 docs/商城三功能联调接口文档.md）：
       cart.item    { itemKey, productId, sku, qty, price, product{id,title,price,original,art,skus,sales,stock,tag,shop} }
       orders.order { 同上，但 createTime/payTime/shipTime/finishTime 为 'yyyy-MM-dd HH:mm:ss' 字符串，
                      items 额外携带 art 快照 }
       favorites    { total, page, size, list: [商品对象] }
     说明：真实接口成功（code=1）后同步本地 store（写穿缓存）；
      购物车 / 收藏 / 地址已改为 strict（服务端是唯一数据源，不做本地回退），
      订单在后端未实现时仍可由 call() 回退本地存储。
     ========================================================= */
  function fullTime(ts) {
    const d = new Date(ts);
    const pad = v => String(v).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  }
  function parseTime(v) {
    if (!v) return null;
    if (typeof v === 'number') return v;                       // 兼容本地演示数据（毫秒时间戳）
    const t = Date.parse(v);
    return isNaN(t) ? null : t;
  }
  /* 接口购物车列表 → 本地 store（写穿缓存：store 只作页面渲染 / 角标的缓存，
     购物车数据以后端为准；接口失败由调用方如实报错，不再回退本地数据）：
     checked 属前端态（勾选不落库）——刷新后对已存在条目保持原勾选，新条目默认不勾选（由用户手动勾选）；
     product 存服务端下发的商品快照，供页面渲染；
     price 为款式价（服务端按加入时的选款快照存，用于结算计价）；
     img 为款式图：优先取服务端下发的 img，其次按 product.skus 与条目 sku 解析（与详情页
     「主图随款式切换」同口径），两者都没有时沿用本地已有记录（仅渲染辅助，不改变数据来源） */
  function skuImgFromProduct(product, skuText) {
    if (!product || !Array.isArray(product.skus) || !product.skus.length) return null;
    const parts = String(skuText || '').split(' / ').map(s => s.trim());
    let img = null;
    product.skus.forEach((g, gi) => {
      if (img || !Array.isArray(g.values)) return;
      const want = parts[gi];
      if (!want) return;
      for (const v of g.values) {
        const vt = typeof v === 'string' ? v : (v && v.v);
        if (vt === want && v && v.img) { img = v.img; break; }
      }
    });
    return img || null;
  }
  function syncCartFromApi(apiList) {
    const prev = {};
    QM_STORE.state.cart.forEach(i => { prev[i.key] = { checked: i.checked, img: i.img || null }; });
    QM_STORE.state.cart = (apiList || []).map(it => ({
      key: it.itemKey,
      productId: it.productId,
      sku: it.sku || '默认',
      qty: it.qty,
      checked: prev[it.itemKey] !== undefined ? prev[it.itemKey].checked : false,
      price: (it.price === undefined || it.price === null) ? null : Number(it.price),
      img: it.img || skuImgFromProduct(it.product, it.sku) || (prev[it.itemKey] ? prev[it.itemKey].img : null),
      product: it.product || null
    }));
    QM_STORE.saveNow();
    QM_STORE.emit('cart');
  }
  /* 接口订单 → 本地 store 结构（时间字符串 → millis；条目自带 art 时直接用） */
  function orderFromApi(o) {
    if (!o) return null;
    return Object.assign({}, o, {
      createTime: parseTime(o.createTime),
      payTime: o.payTime ? parseTime(o.payTime) : null,
      shipTime: o.shipTime ? parseTime(o.shipTime) : null,
      finishTime: o.finishTime ? parseTime(o.finishTime) : null,
      items: (o.items || []).map(it => Object.assign({}, it, { art: it.art || null }))
    });
  }
  /* 接口地址 → 本地 store 结构：字段名对齐（id/name/phone/region/detail/tag/isDefault）；
     id 统一转字符串（后端自增主键返回数字，前端全站按字符串使用，兼容本地 nextId 生成的 'a-…'）；
     tag 是「家 / 公司 / 学校 / 自定义」这类地址标签，可选字段，后端不返回时按空串处理 */
  function addrFromApi(a) {
    if (!a) return null;
    return {
      id: a.id !== undefined ? String(a.id) : '',
      name: a.name || '',
      phone: a.phone || '',
      region: a.region || '',
      detail: a.detail || '',
      tag: a.tag || '',
      isDefault: !!(a.isDefault !== undefined ? a.isDefault : a.is_default)
    };
  }
  /* 本地订单 → 接口订单（本地离线回退路径使用；时间 millis → 字符串） */
  function orderToApi(o) {
    if (!o) return null;
    return Object.assign({}, o, {
      createTime: fullTime(o.createTime),
      payTime: o.payTime ? fullTime(o.payTime) : null,
      shipTime: o.shipTime ? fullTime(o.shipTime) : null,
      finishTime: o.finishTime ? fullTime(o.finishTime) : null
    });
  }

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

    /* ================= 店铺档案（读） =================
       契约见 docs/店家中心商品管理接口文档.md 2.11 / 2.12：
       · GET /shops/{shopId}  → 店铺公开档案（店铺主页 / 商品详情店铺栏使用，含店名 / 头像 / 简介 /
         评分 / 粉丝 / 开店时间 / 店主账号）；
       · GET /shops/profile   → 当前登录账号店铺档案（店家中心回显，见 seller.shopProfile）。
       两个接口都是 strict：后端未实现时抛错，由页面回退本地档案（演示数据 / 本地缓存），不白屏。 */
    shops: {
      get(shopId) {
        return call(
          { name: '店铺档案', method: 'GET', path: '/shops/' + encodeURIComponent(shopId), query: {} },
          null, { strict: true }
        );
      }
    },

    /* ================= 商品模块（strict：严格对接后端，无本地演示回退） ================= */
    products: {
      list(opts = {}) {
        return call(
          { name: '商品列表', method: 'GET', path: '/products', query: { page: opts.page || 1, size: opts.size || 20, category: opts.category, sub: opts.sub, sort: opts.sort, keyword: opts.keyword, shopId: opts.shopId } },
          null, { strict: true }
        );
      },
      get(id) {
        return call(
          { name: '商品详情', method: 'GET', path: '/products/' + encodeURIComponent(id) },
          null, { strict: true }
        );
      },
      search(q, opts = {}) {
        return call(
          { name: '商品搜索', method: 'GET', path: '/products/search', query: { q, page: opts.page || 1, size: opts.size || 20, sort: opts.sort } },
          null, { strict: true }
        );
      },
      flash() {
        return call(
          { name: '限时秒杀', method: 'GET', path: '/home/flash' },
          null, { strict: true }
        );
      },
      recommend(opts = {}) {
        return call(
          { name: '猜你喜欢', method: 'GET', path: '/home/recommend', query: { page: opts.page || 1, size: opts.size || 15 } },
          null, { strict: true }
        );
      },
      related(id, size = 5) {
        return call(
          { name: '相关推荐', method: 'GET', path: '/products/' + encodeURIComponent(id) + '/related', query: { size } },
          null, { strict: true }
        );
      },
      /* 上传商品图片：multipart 字段 file，后端上传阿里云 OSS 后返回 { url }（与 /users/avatar 同链路） */
      async uploadImage(file) {
        if (!file) throw new Error('文件不能为空');
        const form = new FormData();
        form.append('file', file, file.name);
        try {
          return await call(
            { name: '上传商品图片', method: 'POST', path: '/products/image', timeout: QM_CFG.UPLOAD_TIMEOUT, body: form, token: tokenOf() },
            null, { strict: true }
          );
        } catch (e) {
          throw formatUploadError(e);
        }
      }
    },

    /* ================= 购物车（strict：严格走后端，不做本地回退） =================
       契约要点（详见 docs/购物车接口文档.md）：
       · GET    /cart                     → data: [条目]，条目含 itemKey/productId/sku/qty/price/product{...}
       · POST   /cart                     → body {productId, skuText, quantity, price}，data: null（写接口无业务返回数据）
       · PUT    /cart/items               → body {itemKey, quantity}（itemKey 含 '/'，故不走路径参数），data: null
       · PUT    /cart/items/sku           → body {itemKey, skuText, price}（更换款式；新款式已存在则合并数量并删旧条目），data: null
       · DELETE /cart/items               → body {itemKeys: []} 批量删除，data: null
       · DELETE /cart                     → 清空购物车，data: null

       ⚠ 购物车一律 strict，**不做本地离线回退**：购物车是服务端数据（加购 / 列表 / 结算都来自后端）。
       历史实现曾静默回退本地存储，出现「后端 404 时本地假加购、刷新即消失」的问题；
       现与收藏 / 地址同策略：接口不可达 / 404 / 500 如实抛错，由页面提示，绝不假成功。
       成功（code=1）后仍写穿本地 store，但它只是渲染缓存，不再承担兜底职责。
       每个变更成功后重新拉取一次列表，保证本地缓存与服务端一致。 */
    cart: {
      async list() {
        const data = await call(
          { name: '购物车列表', method: 'GET', path: '/cart', query: {}, token: tokenOf() },
          null, { strict: true }
        );
        syncCartFromApi(Array.isArray(data) ? data : ((data && data.list) || []));
        return QM_STORE.cart.list();
      },
      /* add(productId, skuText, qty, price)：price 为选中款式的成交价（详情页算好传入；
         无款式价传 null，服务端按商品默认价处理）。加购只走后端，失败如实抛错。 */
      async add(productId, skuText, qty, price) {
        const skuPrice = (price === undefined || price === null || price === '') ? null : Number(price);
        await call(
          { name: '加入购物车', method: 'POST', path: '/cart', body: { productId, skuText, quantity: qty, price: skuPrice }, token: tokenOf() },
          null, { strict: true }
        );
        return QM_API.cart.list();
      },
      async update(itemKey, qty) {
        await call(
          { name: '修改数量', method: 'PUT', path: '/cart/items', body: { itemKey, quantity: qty }, token: tokenOf() },
          null, { strict: true }
        );
        return QM_API.cart.list();
      },
      /* 修改款式（换规格）：body { itemKey, skuText, price }——itemKey 为当前条目，
         skuText 为新款式（详情页同一口径的 ' / ' 拼接文本），price 为新款式成交价（无款式价传 null）。
         后端事务：新款式已存在则数量合并（上限 999），随后删除旧条目；写接口无业务返回数据。 */
      async updateSku(itemKey, skuText, skuPrice) {
        const price = (skuPrice === undefined || skuPrice === null || skuPrice === '') ? null : Number(skuPrice);
        await call(
          { name: '修改款式', method: 'PUT', path: '/cart/items/sku', body: { itemKey, skuText, price }, token: tokenOf() },
          null, { strict: true }
        );
        return QM_API.cart.list();
      },
      async remove(itemKeys) {
        const keys = [].concat(itemKeys || []);
        await call(
          { name: '删除购物车', method: 'DELETE', path: '/cart/items', body: { itemKeys: keys }, token: tokenOf() },
          null, { strict: true }
        );
        return QM_API.cart.list();
      },
      async clear() {
        await call(
          { name: '清空购物车', method: 'DELETE', path: '/cart', body: {}, token: tokenOf() },
          null, { strict: true }
        );
        return QM_API.cart.list();
      }
    },

    /* ================= 订单（后端实现后走真实接口；未实现时回退本地存储演示） =================
       契约要点（详见 docs/商城三功能联调接口文档.md）：
       · GET    /orders?status=&page=&size=      → {total,page,size,list}（status 空=全部）
       · GET    /orders/counts                   → {all,pending,paid,shipped,done,canceled}
       · GET    /orders/{orderId}                → 订单详情
       · POST   /orders                          → 创建订单，data: 创建的订单（status=pending）
       · POST   /orders/{orderId}/pay            → 支付（演示：直接置 paid）
       · POST   /orders/{orderId}/cancel         → 取消（仅 pending 可取消）
       · POST   /orders/{orderId}/confirm        → 确认收货（仅 shipped 可确认）
       · POST   /orders/{orderId}/remind         → 提醒发货（后端可选实现）
       · GET    /orders/{orderId}/logistics      → {list:[{text,time}]}
       时间统一 'yyyy-MM-dd HH:mm:ss'，前端映射回本地毫秒结构。 */
    orders: {
      async list(status, page = 1, size = 100) {
        const data = await call(
          { name: '订单列表', method: 'GET', path: '/orders', query: { status, page, size }, token: tokenOf() },
          () => {
            const all = QM_STORE.orders.list(status);
            const start = (page - 1) * size;
            return { total: all.length, page, size, list: all.slice(start, start + size).map(orderToApi) };
          }
        );
        const list = ((data && data.list) || []).map(orderFromApi);
        /* 「全部」列表同步本地 store.orders（卖家端等本地视图仍可复用；分页拉取时以全部页为准） */
        if (!status) {
          QM_STORE.state.orders = list;
          QM_STORE.saveNow();
          QM_STORE.emit('orders');
        }
        return { total: (data && data.total) || list.length, page: (data && data.page) || page, size: (data && data.size) || size, list };
      },
      async counts() {
        const data = await call(
          { name: '订单状态计数', method: 'GET', path: '/orders/counts', query: {}, token: tokenOf() },
          () => {
            const c = { all: 0, pending: 0, paid: 0, shipped: 0, done: 0, canceled: 0 };
            QM_STORE.state.orders.forEach(o => { c.all++; if (c[o.status] !== undefined) c[o.status]++; });
            return c;
          }
        );
        return data || {};
      },
      get(orderId) {
        return call(
          { name: '订单详情', method: 'GET', path: '/orders/' + encodeURIComponent(orderId), query: {}, token: tokenOf() },
          () => orderToApi(QM_STORE.orders.get(orderId))
        ).then(orderFromApi);
      },
      async create(payload) {
        const data = await call(
          { name: '创建订单', method: 'POST', path: '/orders', body: payload, token: tokenOf() },
          () => QM_STORE.orders.create(payload)
        );
        const order = orderFromApi(data);
        if (order && !QM_STORE.state.orders.some(o => o.id === order.id)) {
          QM_STORE.state.orders.unshift(order);
          QM_STORE.saveNow();
          QM_STORE.emit('orders');
        }
        return order;
      },
      async pay(orderId) {
        await call(
          { name: '订单支付', method: 'POST', path: '/orders/' + encodeURIComponent(orderId) + '/pay', body: {}, token: tokenOf() },
          () => { QM_STORE.orders.pay(orderId); return { paid: true }; }
        );
        QM_STORE.orders.pay(orderId); // 幂等：仅 pending→paid；mock 路径已改，重复调用无副作用
        return { paid: true };
      },
      async cancel(orderId) {
        await call(
          { name: '取消订单', method: 'POST', path: '/orders/' + encodeURIComponent(orderId) + '/cancel', body: {}, token: tokenOf() },
          () => { QM_STORE.orders.cancel(orderId); return { canceled: true }; }
        );
        QM_STORE.orders.cancel(orderId);
        return { canceled: true };
      },
      async confirm(orderId) {
        await call(
          { name: '确认收货', method: 'POST', path: '/orders/' + encodeURIComponent(orderId) + '/confirm', body: {}, token: tokenOf() },
          () => { QM_STORE.orders.confirm(orderId); return { confirmed: true }; }
        );
        QM_STORE.orders.confirm(orderId);
        return { confirmed: true };
      },
      async remind(orderId) {
        return call(
          { name: '提醒发货', method: 'POST', path: '/orders/' + encodeURIComponent(orderId) + '/remind', body: {}, token: tokenOf() },
          () => ({ reminded: true })
        );
      },
      async logistics(orderId) {
        return call(
          { name: '物流信息', method: 'GET', path: '/orders/' + encodeURIComponent(orderId) + '/logistics', query: {}, token: tokenOf() },
          () => {
            const o = QM_STORE.orders.get(orderId);
            return { list: (o && o.logistics) || [] };
          }
        );
      }
    },

    /* ================= 收藏（strict：严格走后端，不做本地回退） =================
       契约要点（详见 docs/收藏夹接口文档.md）：
       · GET    /favorites?page=&size=         → {total,page,size,list:[商品对象]}（商品对象含收藏页渲染所需字段）
       · POST   /favorites                     → body {productId}，添加收藏
       · DELETE /favorites/{productId}         → 取消收藏
       · DELETE /favorites                     → 清空全部收藏
       三个写接口成功后均无业务数据（data 为空），前端只按成败提示；
       成功响应后同步本地收藏 id 列表（驱动详情页收藏按钮状态）。

       ⚠ 收藏一律 strict，**不做本地离线回退**：收藏是服务端数据（收藏页 / 收藏数都来自后端）。
       历史 bug 与此前地址模块如出一辙 —— 后端 POST /favorites 参数绑定失败返回 400，
       前端因存在 mockFn 而静默把收藏写进浏览器存储并提示「已收藏」，数据库里根本没有这条记录，
       刷新后收藏凭空消失。接口出错必须如实抛出，绝不能假成功。 */
    favorites: {
      async list(page = 1, size = 100) {
        const data = await call(
          { name: '收藏列表', method: 'GET', path: '/favorites', query: { page, size }, token: tokenOf() },
          null, { strict: true }
        );
        const list = (data && data.list) || [];
        QM_STORE.state.favorites = list.map(p => String((p && p.id) || p));
        QM_STORE.saveNow();
        QM_STORE.emit('favorites');
        return { total: (data && data.total) || list.length, page: (data && data.page) || page, size: (data && data.size) || size, list };
      },
      async add(productId) {
        productId = String(productId);
        await call(
          { name: '添加收藏', method: 'POST', path: '/favorites', body: { productId }, token: tokenOf() },
          null, { strict: true }
        );
        const list = QM_STORE.state.favorites;
        if (list.indexOf(productId) < 0) list.unshift(productId);
        QM_STORE.saveNow();
        QM_STORE.emit('favorites');
      },
      async remove(productId) {
        productId = String(productId);
        await call(
          { name: '取消收藏', method: 'DELETE', path: '/favorites/' + encodeURIComponent(productId), token: tokenOf() },
          null, { strict: true }
        );
        const list = QM_STORE.state.favorites;
        const idx = list.indexOf(productId);
        if (idx >= 0) list.splice(idx, 1);
        QM_STORE.saveNow();
        QM_STORE.emit('favorites');
      },
      async clear() {
        await call(
          { name: '清空收藏', method: 'DELETE', path: '/favorites', body: {}, token: tokenOf() },
          null, { strict: true }
        );
        QM_STORE.state.favorites.length = 0;
        QM_STORE.saveNow();
        QM_STORE.emit('favorites');
      }
    },

    /* ================= 收货地址（strict：严格走后端，不做本地回退） =================
       契约要点（详见 docs/地址簿接口文档.md）：
       · GET    /addresses              → data: [地址对象]，无地址返回 []
       · POST   /addresses              → body {name, phone, region, detail, tag, isDefault}，data: 新建地址（含 id）
       · PUT    /addresses/{id}         → body 部分字段补丁，data: 更新后的完整地址
       · DELETE /addresses/{id}         → data: { deleted: true }
       · PUT    /addresses/{id}/default → data: { defaultId }；其余地址默认标记由后端清空（幂等）
       地址对象：{ id, name, phone, region, detail, tag, isDefault }

       ⚠ 地址一律 strict，**不做本地离线回退**：地址会作为收货信息进入订单，属于交易关键数据。
       接口不可达 / 404 / 500 时必须如实报错，绝不能在本地「保存成功」——历史 bug 就是
       后端路径误写成单数 /address 返回 404，前端静默把地址写进浏览器存储并提示保存成功，
       用户刷新或换设备后地址凭空消失，后端库里根本没有这条数据。
       成功后仍写穿本地 store，但它只是缓存，不再承担「后端失败时兜底」的职责。 */
    addresses: {
      /* 地址列表：成功（code=1）后写穿本地 store 并广播 addresses 事件（store 仅作缓存） */
      async list() {
        const data = await call(
          { name: '地址列表', method: 'GET', path: '/addresses', query: {}, token: tokenOf() },
          null,
          { strict: true }
        );
        const list = (Array.isArray(data) ? data : ((data && data.list) || [])).map(addrFromApi).filter(Boolean);
        QM_STORE.state.addresses = list;
        QM_STORE.saveNow();
        QM_STORE.emit('addresses');
        return QM_STORE.addr.list();
      },
      /* 新增地址：payload { name, phone, region, detail, tag, isDefault }，写入失败即抛错 */
      async create(payload) {
        await call(
          { name: '新增地址', method: 'POST', path: '/addresses', body: payload || {}, token: tokenOf() },
          null,
          { strict: true }
        );
        return QM_API.addresses.list();
      },
      /* 更新地址：patch 传需要修改的字段即可；isDefault=true 时后端应清空其他默认标记 */
      async update(id, patch) {
        await call(
          { name: '更新地址', method: 'PUT', path: '/addresses/' + encodeURIComponent(id), body: patch || {}, token: tokenOf() },
          null,
          { strict: true }
        );
        return QM_API.addresses.list();
      },
      /* 删除地址：成功与否只看 code，返回确认对象前端不消费 */
      async remove(id) {
        await call(
          { name: '删除地址', method: 'DELETE', path: '/addresses/' + encodeURIComponent(id), body: {}, token: tokenOf() },
          null,
          { strict: true }
        );
        return QM_API.addresses.list();
      },
      /* 设默认：PUT /addresses/{id}/default（幂等；其余地址默认标记由后端清空）。
         返回值 defaultId 前端不消费，成功即重新拉列表 */
      async setDefault(id) {
        await call(
          { name: '设置默认地址', method: 'PUT', path: '/addresses/' + encodeURIComponent(id) + '/default', body: {}, token: tokenOf() },
          null,
          { strict: true }
        );
        return QM_API.addresses.list();
      }
    },

    /* ================= 店家中心 · 商品管理（strict：严格对接后端，无本地演示回退） =================
       契约要点（详见 docs/店家中心商品管理接口文档.md）：
       · GET    /seller/products            → { total, page, size, list }（status=on/off 可筛选上下架）
       · GET    /seller/products/{id}       → 单个商品（编辑回显）
       · POST   /seller/products            → 新增商品，data: 创建后的完整商品（含 id）
       · PUT    /seller/products/{id}       → 更新商品（部分字段即可），data: 更新后的完整商品
       · PUT    /seller/products/{id}/status→ 上下架，body { onSale }，data: { onSale }
       · DELETE /seller/products/{id}       → 删除商品，data: { deleted }
       鉴权：全部需要 Bearer token（不在 TokenFilter 白名单）；后端从令牌解析 userId →
       shopId，返回该店铺商品，前端不传店铺参数。
       商品对象字段与全站商品结构一致：{ id,title,price,original,sales,stock,category,sub,tag,
       art:{img},shop:{name,score},skus:[{name,values:[{v,img}]}],desc,params:[[k,v]],
       detail:[{type:"text"|"img",...}],onSale }。 */
    seller: {
      /* 当前店铺商品列表（status: ''全部 / on在售 / off已下架；keyword 按标题模糊搜索） */
      products(opts = {}) {
        return call(
          { name: '店家商品列表', method: 'GET', path: '/seller/products', query: { page: opts.page || 1, size: opts.size || 100, status: opts.status, keyword: opts.keyword }, token: tokenOf() },
          null, { strict: true }
        );
      },
      /* 单个商品（编辑回显） */
      get(id) {
        return call(
          { name: '店家商品详情', method: 'GET', path: '/seller/products/' + encodeURIComponent(id), query: {}, token: tokenOf() },
          null, { strict: true }
        );
      },
      /* 新增商品：payload 为商品对象（不含 id），返回创建后的完整商品 */
      create(payload) {
        return call(
          { name: '新增商品', method: 'POST', path: '/seller/products', body: payload || {}, token: tokenOf() },
          null, { strict: true }
        );
      },
      /* 更新商品：payload 传需要修改的字段即可，返回更新后的完整商品 */
      update(id, payload) {
        return call(
          { name: '更新商品', method: 'PUT', path: '/seller/products/' + encodeURIComponent(id), body: payload || {}, token: tokenOf() },
          null, { strict: true }
        );
      },
      /* 上下架：onSale=true 上架 / false 下架，返回 { onSale } */
      setStatus(id, onSale) {
        return call(
          { name: '商品上下架', method: 'PUT', path: '/seller/products/' + encodeURIComponent(id) + '/status', body: { onSale: !!onSale }, token: tokenOf() },
          null, { strict: true }
        );
      },
      /* 删除商品 */
      remove(id) {
        return call(
          { name: '删除商品', method: 'DELETE', path: '/seller/products/' + encodeURIComponent(id), body: {}, token: tokenOf() },
          null, { strict: true }
        );
      },
      /* 上传店铺头像：multipart 字段 file → { url }（与商品图同一套 OSS 上传，见教程 Step 2(c)） */
      async uploadShopAvatar(file) {
        if (!file) throw new Error('文件不能为空');
        const form = new FormData();
        form.append('file', file, file.name);
        try {
          return await call(
            { name: '上传店铺头像', method: 'POST', path: '/shops/avatar', timeout: QM_CFG.UPLOAD_TIMEOUT, body: form, token: tokenOf() },
            null, { strict: true }
          );
        } catch (e) {
          throw formatUploadError(e);
        }
      },
      /* 保存店铺资料：body { avatar, intro } 部分更新（intro=店铺简介），返回最新店铺信息 */
      updateShopProfile(payload) {
        return call(
          { name: '保存店铺资料', method: 'PUT', path: '/shops/profile', body: payload || {}, token: tokenOf() },
          null, { strict: true }
        );
      },
      /* 开通店铺：body { name, intro, avatar }（开店基本信息，见接口文档 2.10），
         返回创建好的店铺档案（必须含店铺标识 shopId）；后端同时把该店铺绑到当前账号的 users.shop_id */
      createShop(payload) {
        return call(
          { name: '开通店铺', method: 'POST', path: '/shops', body: payload || {}, token: tokenOf() },
          null, { strict: true }
        );
      },
      /* 当前账号的店铺档案（店家中心回显：店名 / 头像 / 简介 / 评分 / 粉丝 / 开店时间） */
      shopProfile() {
        return call(
          { name: '店铺档案', method: 'GET', path: '/shops/profile', query: {}, token: tokenOf() },
          null, { strict: true }
        );
      },
      /* 店家订单列表：{ total, page, size, list }，订单结构同 docs/商城三功能联调接口文档.md
         （时间字段为 'yyyy-MM-dd HH:mm:ss'，这里统一转成毫秒时间戳供页面直接渲染） */
      async orders(opts = {}) {
        const data = await call(
          { name: '店家订单列表', method: 'GET', path: '/seller/orders', query: { page: opts.page || 1, size: opts.size || 100, status: opts.status }, token: tokenOf() },
          null, { strict: true }
        );
        const d = data || {};
        return Object.assign({ total: 0, page: 1, size: 0, list: [] }, d, {
          list: ((d.list) || []).map(orderFromApi).filter(Boolean)
        });
      },
      /* 发货：body 空，data { shipped: true }（幂等：非待发货状态由后端决定是否报错） */
      shipOrder(orderId) {
        return call(
          { name: '订单发货', method: 'PUT', path: '/seller/orders/' + encodeURIComponent(orderId) + '/ship', body: {}, token: tokenOf() },
          null, { strict: true }
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

    /* ================= 用户资料（头像上传 OSS / 资料更新） =================
       契约（后端实现见 docs/用户头像上传OSS与资料更新-后端实现教程.md）：
       · POST /users/avatar —— multipart 字段 file，后端上传阿里云 OSS 后返回 { url }；
       · PUT  /users/profile —— JSON { nickname, gender, avatar, signature }，返回更新后的用户。 */
    user: {
      /* 头像上传：multipart 提交（字段 file），后端把图片传到阿里云 OSS，返回 { url } */
      async uploadAvatar(file) {
        if (!file) throw new Error('文件不能为空');
        const form = new FormData();
        form.append('file', file, file.name);
        try {
          return await call(
            { name: '上传头像', method: 'POST', path: '/users/avatar', timeout: QM_CFG.UPLOAD_TIMEOUT, body: form, token: tokenOf() },
            null,
            { strict: true }
          );
        } catch (e) {
          throw formatUploadError(e);
        }
      },
      /* 资料更新：PUT /users/profile，body { nickname, gender, avatar, signature } */
      updateProfile(payload) {
        return call(
          { name: '更新资料', method: 'PUT', path: '/users/profile', body: payload, token: tokenOf() },
          null,
          { strict: true }
        );
      }
    },

    chat: {
      onlineUsers() {
        return call(
          { name: '在线用户', method: 'GET', path: '/users/online', query: {}, token: tokenOf() },
          null,
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
          null,
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
