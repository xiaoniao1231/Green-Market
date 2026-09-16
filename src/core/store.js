/* =========================================================
   青集市 · store.js —— 状态管理与本地持久化
   购物车 / 订单 / 收藏 / 地址 / 优惠券 / 聊天记录均为本地演示数据，
   后端对应接口落地后，api.js 会自动优先使用真实接口。
   ========================================================= */
import QM_MOCK from './mock.js';

const KEY = 'qm_v2_state';

/* =========================================================
   存储后端：sessionStorage —— 按「标签页」隔离
   ---------------------------------------------------------
   原实现用 localStorage，它按「源」（协议+主机名+端口）隔离，
   同源的所有标签页共享同一份数据，导致三个问题：
     ① 两个标签页只能登录一个账号：后登录的会覆盖先登录的；
     ② 两个标签页各自 save() 会把「整个 state」写回去，互相覆盖
        —— 登录态与聊天记录来回抖动；
     ③ 当前页看着正常，一刷新就变成另一个账号（store.js 未监听
        storage 事件，标签页之间不会实时同步）。
   改用 sessionStorage 后，每个标签页拥有独立的登录态与数据：
   开两个标签页即可同时登录两个账号，且刷新页面数据不丢失
   （同一标签页内刷新沿用同一份 sessionStorage）。
   取舍：关闭标签页后该页数据被清除。本项目状态多为演示数据，
   且聊天记录可从后端 /messages/history 重新拉取，故可接受。
   ========================================================= */

/* 取存储后端：优先 sessionStorage；浏览器禁用存储时回退 localStorage
   （回退后多标签页会重新共享登录态，即退化为改造前的行为） */
const storage = (() => {
  try {
    window.sessionStorage.setItem('__qm_probe__', '1');
    window.sessionStorage.removeItem('__qm_probe__');
    return window.sessionStorage;
  } catch (e) {
    return window.localStorage;
  }
})();

/* 旧版本把状态存在 localStorage，改用 sessionStorage 后不再读取它，
   清理一次，避免残留数据占空间、也避免与「重新登录」的行为混淆 */
try { localStorage.removeItem(KEY); } catch (e) { /* 忽略 */ }

  function defaults() {
    return {
      user: null,            // {userId, nickname, token, demo}
      cart: [],              // {key, productId, sku, qty, checked}
      favorites: [],         // productId[]
      /* 演示订单种子（首次打开时展示各订单状态；下单后由真实逻辑接管） */
      orders: [
        {
          id: 'o-demo-shipped', orderNo: 'QM20260820D3F9A2', status: 'shipped',
          createTime: Date.now() - 3600e3 * 30, shipTime: Date.now() - 3600e3 * 4,
          items: [
            { productId: 'p09', sku: '坚果混合', qty: 2, price: 59, title: '办公室休闲零食大礼包 坚果果干混合装 网红解馋小吃 30袋' },
            { productId: 'p24', sku: '樱花粉', qty: 1, price: 79, title: '大容量随行杯 316不锈钢 保温保冷 便携水杯 750ml' }
          ],
          address: { name: '小语', phone: '138****8000', region: '浙江省 杭州市 西湖区', detail: '文三路 100 号 3 幢 502 室' },
          coupon: { id: 'c1', title: '新人专享券', amount: 30, threshold: 199 }, payMethod: '支付宝',
          goodsAmount: 197, discount: 30, freight: 0, total: 167,
          logistics: [
            { text: '包裹已到达【杭州转运中心】，正在派送中', time: Date.now() - 3600e3 * 2 },
            { text: '卖家已发货，等待揽收', time: Date.now() - 3600e3 * 20 }
          ]
        },
        {
          id: 'o-demo-done', orderNo: 'QM20260810A1B2C3', status: 'done',
          createTime: Date.now() - 3600e3 * 240, payTime: Date.now() - 3600e3 * 239, shipTime: Date.now() - 3600e3 * 220, finishTime: Date.now() - 3600e3 * 170,
          items: [{ productId: 'p01', sku: '曜石黑 / 标准版', qty: 1, price: 299, title: '青禾无线降噪耳机 头戴式蓝牙5.3 超长续航 重低音游戏音乐耳机' }],
          address: { name: '小语', phone: '138****8000', region: '浙江省 杭州市 西湖区', detail: '文三路 100 号 3 幢 502 室' },
          coupon: null, payMethod: '微信支付',
          goodsAmount: 299, discount: 0, freight: 0, total: 299,
          logistics: [
            { text: '包裹已签收，感谢您使用青集市', time: Date.now() - 3600e3 * 170 },
            { text: '包裹已到达【杭州转运中心】，正在派送中', time: Date.now() - 3600e3 * 178 },
            { text: '卖家已发货，等待揽收', time: Date.now() - 3600e3 * 220 }
          ]
        }
      ],
      addresses: [
        { id: 'a1', name: '小语', phone: '138****8000', region: '浙江省 杭州市 西湖区', detail: '文三路 100 号 3 幢 502 室', isDefault: true },
        { id: 'a2', name: '小语', phone: '138****8000', region: '浙江省 杭州市 余杭区', detail: '梦想小镇 88 号（公司）', isDefault: false }
      ],
      coupons: [
        { id: 'c1', title: '新人专享券', amount: 30, threshold: 199, status: 'unused', expire: '2026-12-31' },
        { id: 'c2', title: '满 299 减 50', amount: 50, threshold: 299, status: 'unused', expire: '2026-12-31' },
        { id: 'c3', title: '满 99 减 10', amount: 10, threshold: 99, status: 'unused', expire: '2026-10-01' },
        { id: 'c4', title: '数码专享券', amount: 80, threshold: 999, status: 'unused', expire: '2026-11-11' }
      ],
      contacts: QM_MOCK.contacts.slice(),
      chats: {},
      shopFavs: [],        // 关注的店铺名（用户可关注店铺，店铺页 / 详情页使用）
      sellerProducts: {}   // 卖家商品管理状态 { productId: { onSale, price } }
    };
  }

  const listeners = {};
  function emit(event, payload) {
    (listeners[event] || []).slice().forEach(fn => { try { fn(payload); } catch (e) { console.error(e); } });
  }

  let saveTimer = null;
  /* 立即落盘：关键路径（登录 / 退出 / 提交订单等）必须同步写入，
     否则 60ms 防抖期间用户关闭标签页会丢失状态（sessionStorage 随标签页销毁）。 */
  function persist() {
    clearTimeout(saveTimer);
    saveTimer = null;
    try { storage.setItem(KEY, JSON.stringify(QM_STORE.state)); } catch (e) { /* 存储满等异常忽略 */ }
  }
  /* 普通写入走防抖，避免高频操作（改数量、加购连点）反复序列化整个 state */
  function save() {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(persist, 60);
  }

  function nextId(prefix) { return prefix + '-' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6); }

  const QM_STORE = {
    state: null,
    listeners,
    emit,

    load() {
      let saved = null;
      try { saved = JSON.parse(storage.getItem(KEY) || 'null'); } catch (e) { saved = null; }
      const base = defaults();
      QM_STORE.state = saved ? Object.assign(base, saved) : base;
      // 保证聊天联系人与本地聊天记录存在
      if (!QM_STORE.state.chats) QM_STORE.state.chats = {};
      QM_STORE.state.contacts = QM_STORE.state.contacts || QM_MOCK.contacts.slice();
      QM_STORE.state.shopFavs = QM_STORE.state.shopFavs || [];
      /* 独立商家账号体系已移除：清理旧版 sellerUser 会话数据（店铺绑定统一挂在 user.shopId 上） */
      if (QM_STORE.state.sellerUser) { delete QM_STORE.state.sellerUser; save(); }
      /* 迁移：① 旧版以店铺标识（shop-qinghe）为聊天 key → 新版以开店用户 id 为 key；
         ② 旧版「客服用户 id」（kf-xxx）→ 新版开店用户 id（owner0xx），保证老会话不丢失 */
      Object.keys(QM_STORE.state.chats).forEach(k => {
        const svc = QM_MOCK.serviceById(k);
        const legacy = QM_MOCK.kfLegacyToOwner[k];
        const target = (svc && svc.userId && svc.userId !== k) ? svc.userId : legacy;
        const list = QM_STORE.state.chats[k];
        /* 会话内旧「客服用户 id」发送的消息归属同步迁移为开店用户 id（幂等） */
        list.forEach(m => {
          const mapped = QM_MOCK.kfLegacyToOwner[m.from];
          if (mapped) m.from = mapped;
        });
        if (target && target !== k) {
          QM_STORE.state.chats[target] = list;
          delete QM_STORE.state.chats[k];
        }
      });
      /* 会话保留规则：① 店铺会话（role=shop，商品详情「联系卖家」创建）；
         ② 有实际聊天记录的联系人（收到对方消息时以 role=friend 自动创建）。
         旧实现只保留 role=shop，会让「接收方」一刷新就丢掉会话入口与聊天记录，
         导致两个用户无法持续互聊 —— 这是多用户互相聊天必须放开的一环。 */
      const chatKeys = new Set(Object.keys(QM_STORE.state.chats));
      QM_STORE.state.contacts = QM_STORE.state.contacts.filter(
          c => c && (c.role === 'shop' || chatKeys.has(c.id))
      );
      QM_STORE.state.contacts.forEach(c => {
        const svc = QM_MOCK.serviceById(c.id);
        const legacy = QM_MOCK.kfLegacyToOwner[c.id];
        const target = (svc && svc.userId && svc.userId !== c.id) ? svc.userId : legacy;
        if (target && target !== c.id) c.id = target;
        /* 迁移后按新版开店用户信息刷新对端显示名（旧「客服」名一并更新为昵称） */
        const fresh = QM_MOCK.serviceById(c.id);
        if (fresh) { c.name = fresh.name; c.color = fresh.color; c.intro = fresh.intro; }
      });
      const validIds = new Set(QM_STORE.state.contacts.map(c => c.id));
      Object.keys(QM_STORE.state.chats).forEach(k => {
        if (!validIds.has(k)) delete QM_STORE.state.chats[k];
      });
    },

    reset() {
      storage.removeItem(KEY);
      QM_STORE.load();
      emit('reset');
    },

    /* 关键路径立即落盘（原实现误接防抖版 save，等于没有立即保存） */
    saveNow() { persist(); },

    on(event, fn) { (listeners[event] = listeners[event] || []).push(fn); return () => { listeners[event] = (listeners[event] || []).filter(f => f !== fn); }; },

    /* ---------- 用户 ---------- */
    user: {
      set(user, token) {
        /* 切换账号时重置聊天数据：会话在语义上属于某个账号，
           否则在同一标签页换账号登录，会看到上一个账号的联系人与聊天记录。
           注意只在「确实从 A 账号切到 B 账号」时清空（prevId 非空且不同）：
           退出登录后重新登录同一账号（prevId 为 null）不清空，本地会话直接保留；
           即便本地被清（关闭标签页 / 换账号），登录后 ChatView 也会从后端
           /messages/conversations 重建会话列表，不会白屏。 */
        const prevId = QM_STORE.state.user && QM_STORE.state.user.userId;
        const nextId = user && user.userId;
        if (nextId && prevId && prevId !== nextId) {
          QM_STORE.state.chats = {};
          QM_STORE.state.contacts = QM_MOCK.contacts.slice();
        }
        QM_STORE.state.user = user ? Object.assign({}, user, { token }) : null;
        persist(); // 登录态是关键数据：同步落盘，避免关标签页丢登录
        emit('user', QM_STORE.state.user);
      },
      /** 更新当前登录用户资料（昵称 / 头像 / 性别 / 个性签名等），合并后广播 user 事件 */
      update(patch) {
        if (!QM_STORE.state.user) return null;
        QM_STORE.state.user = Object.assign({}, QM_STORE.state.user, patch || {});
        persist(); emit('user', QM_STORE.state.user);
        return QM_STORE.state.user;
      },
      logout() { QM_STORE.state.user = null; persist(); emit('user', null); },
      /** 会话失效（令牌缺失/无效/过期、服务端返回未登录等）：清除本地登录态并广播 auth-expired */
      expire(message, opts) {
        if (!QM_STORE.state.user) return; // 已是未登录态，忽略重复触发
        QM_STORE.state.user = null;
        persist();
        emit('user', null);
        emit('auth-expired', { message: message || '登录已过期，请重新登录', fromBoot: !!(opts && opts.fromBoot) });
      }
    },

    /* ---------- 购物车 ---------- */
    cart: {
      list() {
        return QM_STORE.state.cart.map(item => {
          const p = QM_MOCK.byId(item.productId);
          return Object.assign({}, item, { product: p || null });
        }).filter(item => item.product);
      },
      add(productId, sku, qty) {
        const key = productId + '|' + (sku || '默认');
        const found = QM_STORE.state.cart.find(i => i.key === key);
        if (found) found.qty = Math.min(999, found.qty + qty);
        else QM_STORE.state.cart.unshift({ key, productId, sku: sku || '默认', qty, checked: true });
        save(); emit('cart');
        return found || QM_STORE.state.cart[0];
      },
      setQty(key, qty) {
        const item = QM_STORE.state.cart.find(i => i.key === key);
        if (item) { item.qty = Math.max(1, Math.min(999, qty)); save(); emit('cart'); }
      },
      toggle(keys, checked) {
        const set = new Set(keys);
        QM_STORE.state.cart.forEach(i => { if (set.has(i.key)) i.checked = checked; });
        save(); emit('cart');
      },
      toggleAll(checked) { QM_STORE.state.cart.forEach(i => { i.checked = checked; }); save(); emit('cart'); },
      remove(keys) {
        const set = new Set(keys);
        QM_STORE.state.cart = QM_STORE.state.cart.filter(i => !set.has(i.key));
        save(); emit('cart');
      },
      clear() { QM_STORE.state.cart = []; save(); emit('cart'); },
      count() { return QM_STORE.state.cart.reduce((n, i) => n + i.qty, 0); },
      selected() {
        return QM_STORE.cart.list().filter(i => i.checked);
      },
      total() {
        return QM_STORE.cart.selected().reduce((sum, i) => sum + i.product.price * i.qty, 0);
      }
    },

    /* ---------- 收藏 ---------- */
    fav: {
      has(id) { return QM_STORE.state.favorites.includes(id); },
      toggle(id) {
        const list = QM_STORE.state.favorites;
        const idx = list.indexOf(id);
        if (idx >= 0) list.splice(idx, 1); else list.unshift(id);
        save(); emit('favorites');
        return idx < 0;
      },
      list() { return QM_STORE.state.favorites.map(QM_MOCK.byId).filter(Boolean); }
    },

    /* ---------- 关注店铺 ---------- */
    shopFav: {
      has(name) { return QM_STORE.state.shopFavs.includes(name); },
      toggle(name) {
        const list = QM_STORE.state.shopFavs;
        const idx = list.indexOf(name);
        if (idx >= 0) list.splice(idx, 1); else list.unshift(name);
        save(); emit('shopFavs');
        return idx < 0;
      },
      list() { return QM_STORE.state.shopFavs.slice(); }
    },

    /* ---------- 订单 ---------- */
    orders: {
      list(status) {
        const all = QM_STORE.state.orders;
        return status ? all.filter(o => o.status === status) : all;
      },
      /** payload: {items:[{productId,sku,qty,price}], address, coupon, payMethod, remark} */
      create(payload) {
        const order = {
          id: nextId('o'),
          orderNo: 'QM' + new Date().toISOString().slice(0, 10).replace(/-/g, '') + Math.random().toString(36).slice(2, 8).toUpperCase(),
          status: 'pending', // pending 待付款 / paid 待发货 / shipped 待收货 / done 已完成 / canceled 已取消
          createTime: Date.now(),
          items: payload.items,
          address: payload.address,
          coupon: payload.coupon || null,
          payMethod: payload.payMethod || '支付宝',
          goodsAmount: payload.items.reduce((s, i) => s + i.price * i.qty, 0),
          discount: payload.coupon ? payload.coupon.amount : 0,
          freight: 0,
          total: 0,
          logistics: []
        };
        order.freight = order.goodsAmount >= 99 ? 0 : 8;
        order.total = order.goodsAmount - order.discount + order.freight;
        QM_STORE.state.orders.unshift(order);
        save(); emit('orders');
        return order;
      },
      get(id) { return QM_STORE.state.orders.find(o => o.id === id); },
      pay(id) {
        const o = QM_STORE.orders.get(id);
        if (!o || o.status !== 'pending') return;
        o.status = 'paid'; o.payTime = Date.now();
        save(); emit('orders');
      },
      cancel(id) {
        const o = QM_STORE.orders.get(id);
        if (!o || o.status !== 'pending') return;
        o.status = 'canceled';
        save(); emit('orders');
      },
      ship(id) {
        const o = QM_STORE.orders.get(id);
        if (!o || o.status !== 'paid') return;
        o.status = 'shipped'; o.shipTime = Date.now();
        o.logistics = [
          { text: '包裹已到达【杭州转运中心】', time: Date.now() - 3600e3 * 2 },
          { text: '卖家已发货，等待揽收', time: Date.now() - 3600e3 * 20 }
        ];
        save(); emit('orders');
      },
      confirm(id) {
        const o = QM_STORE.orders.get(id);
        if (!o || o.status !== 'shipped') return;
        o.status = 'done'; o.finishTime = Date.now();
        o.logistics.unshift({ text: '包裹已签收，感谢您使用青集市', time: Date.now() });
        save(); emit('orders');
      }
    },

    /* ---------- 地址 ---------- */
    addr: {
      list() { return QM_STORE.state.addresses; },
      add(addr) { addr.id = nextId('a'); QM_STORE.state.addresses.push(addr); save(); emit('addresses'); },
      update(id, patch) {
        const a = QM_STORE.state.addresses.find(x => x.id === id);
        if (a) { Object.assign(a, patch); save(); emit('addresses'); }
      },
      remove(id) { QM_STORE.state.addresses = QM_STORE.state.addresses.filter(x => x.id !== id); save(); emit('addresses'); },
      setDefault(id) {
        QM_STORE.state.addresses.forEach(x => { x.isDefault = x.id === id; });
        save(); emit('addresses');
      }
    },

    /* ---------- 优惠券 ---------- */
    coupon: {
      list() { return QM_STORE.state.coupons; }
    },

    /* ---------- 聊天 ---------- */
    chat: {
      contacts() { return QM_STORE.state.contacts; },
      ensureContact(id, name, extra) {
        let c = QM_STORE.state.contacts.find(x => x.id === id);
        if (!c) {
          c = Object.assign({ id, name: name || id, role: 'friend', online: false, color: '#6b6bdf' }, extra || {});
          QM_STORE.state.contacts.unshift(c);
          save();
        }
        return c;
      },
      messages(id) { return QM_STORE.state.chats[id] || []; },
      push(id, msg) {
        const list = QM_STORE.state.chats[id] || (QM_STORE.state.chats[id] = []);
        list.push(Object.assign({ id: nextId('m') }, msg));
        if (list.length > 200) list.splice(0, list.length - 200);
        save(); emit('chat', { peerId: id, msg });
      },
      markRead(id) {
        /* 已读状态以会话（联系人）维度记录：打开会话即清零未读数；
           顺带清掉历史遗留的消息级 unread 标记 */
        const c = QM_STORE.state.contacts.find(x => x.id === id);
        if (c) c.unread = 0;
        const list = QM_STORE.state.chats[id] || [];
        list.forEach(m => { m.unread = false; });
        save(); emit('chat');
      },
      unreadTotal() {
        let n = 0;
        QM_STORE.state.contacts.forEach(c => { if (typeof c.unread === 'number') n += c.unread; });
        return n;
      },
      lastOf(id) {
        const list = QM_STORE.state.chats[id] || [];
        return list[list.length - 1] || null;
      }
    },

    /* ---------- 我的店铺（账号持有店铺 shopId 即已开店，见 mock.shopOwners；账号本身仍是普通用户） ---------- */
    seller: {
      /* 当前账号的店铺：返回 {username, ownerName, shopName, id, userId, name, color, intro}；
         未开店（无 shopId）返回 null */
      current() {
        const u = QM_STORE.state.user;
        if (!u || !u.shopId) return null;
        const s = QM_MOCK.serviceById(u.shopId);
        if (!s) return null;
        return {
          username: u.userId,
          ownerName: u.nickname,
          shopName: QM_MOCK.shopNameById(s.id) || '',
          id: s.id,          // 店铺标识
          userId: s.userId,  // 开店用户账号，兼作聊天身份（买家就是和这位用户聊天）
          name: s.name,
          color: s.color,
          intro: s.intro
        };
      },
      /* 全部店铺（含开店用户映射），供开店引导 / 演示账号提示使用 */
      list() {
        return Object.keys(QM_MOCK.shopServices).map(name => Object.assign({ shopName: name }, QM_MOCK.shopServices[name]));
      },
      /* 店铺标识 / 开店用户 id → 店铺名 */
      shopName(shopId) { return QM_MOCK.shopNameById(shopId); },
      /* 该店铺商品（合并卖家上下架 / 改价状态） */
      products(shopId) {
        const name = QM_MOCK.shopNameById(shopId);
        return QM_MOCK.products.filter(p => p.shop.name === name).map(p => Object.assign({}, p, QM_STORE.state.sellerProducts[p.id] || {}));
      },
      setProduct(pid, patch) {
        QM_STORE.state.sellerProducts[pid] = Object.assign({}, QM_STORE.state.sellerProducts[pid] || {}, patch);
        save(); emit('seller');
      },
      /* 该店铺商品产生的订单 */
      orders(shopId) {
        const name = QM_MOCK.shopNameById(shopId);
        return QM_STORE.state.orders.filter(o => o.items.some(it => {
          const p = QM_MOCK.byId(it.productId);
          return p && p.shop.name === name;
        }));
      }
      /* 注：消息统一在「消息中心」（ChatView）处理——与任何联系人一样，
         聊天就是对端的用户账号，不再有独立商家消息中心 */
    }
  };

export default QM_STORE;
