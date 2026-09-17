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
      chatOwner: null,       // 本地聊天数据归属的账号（登录写入、退出保留；换账号登录时据此清空旧会话）
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
      /* 本地店铺档案：{ shopId: { id, name, avatar, color, intro, shopIntro, score, fans, founded, userId } }
         来源：① 店家「店铺信息管理」保存的即时覆盖；② 后端店铺档案（GET /shops/profile、
         GET /shops/{id}、POST /shops）返回的真实数据；③ 新开通店铺的档案。
         优先级高于 mock 演示数据，供工作台 / 店铺主页 / 详情页 / 商品卡片统一读取 */
      shopProfile: {}
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
      /* 会话保留规则：只有「有实际聊天记录」的联系人才算会话。
         以前无条件保留 role=shop 联系人（商品详情「联系卖家」创建但从未发消息），
         会让从未建立过聊天的账号在消息中心看到空会话；统一以 chats 记录为准 ——
         没聊过天就不出现在会话列表（再次从商品详情进入时仍可创建，聊天后自然保留）。 */
      const chatKeys = new Set(Object.keys(QM_STORE.state.chats));
      QM_STORE.state.contacts = QM_STORE.state.contacts.filter(
          c => c && chatKeys.has(c.id)
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
           判定依据是本地会话归属账号 chatOwner（随登录写入、退出后保留）：
           · chatOwner 存在且与本次登录账号不同 → 换号，清空本地会话；
           · chatOwner 为空但本地已有会话数据 → 旧版本残留（归属不明），
             同样清空 —— 本地会话可由后端 /messages/conversations 恢复；
           · chatOwner 与本次登录账号相同（退出后重登同一账号）→ 保留本地会话。 */
        const prevOwner = QM_STORE.state.chatOwner || (QM_STORE.state.user && QM_STORE.state.user.userId);
        const nextId = user && user.userId;
        const staleLocal = !prevOwner && (
          Object.keys(QM_STORE.state.chats || {}).length > 0
          || (QM_STORE.state.contacts || []).length > 0
        );
        if (nextId && ((prevOwner && prevOwner !== nextId) || staleLocal)) {
          QM_STORE.state.chats = {};
          QM_STORE.state.contacts = QM_MOCK.contacts.slice();
        }
        QM_STORE.state.chatOwner = nextId; // 本地会话归属账号：退出登录后保留，用于下次登录识别是否换号
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
        /* product 优先取本地 mock 商品库；后端已落地时条目自带服务端下发的商品快照
           （api.js 写入 state.cart[].product），mock 没有该商品时回退快照，保证可渲染 */
        return QM_STORE.state.cart.map(item => {
          const p = QM_MOCK.byId(item.productId) || item.product || null;
          return Object.assign({}, item, { product: p });
        }).filter(item => item.product);
      },
      add(productId, sku, qty) {
        const key = productId + '|' + (sku || '默认');
        const found = QM_STORE.state.cart.find(i => i.key === key);
        if (found) found.qty = Math.min(999, found.qty + qty);
        /* 新加入的商品默认不勾选：由用户手动勾选后再结算 */
        else QM_STORE.state.cart.unshift({ key, productId, sku: sku || '默认', qty, checked: false });
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
      /* 角标计数：按「不同商品（productId）」去重 —— 同一商品无论加多少次、多少件，都只算 1 */
      count() { return new Set(QM_STORE.state.cart.map(i => i.productId)).size; },
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
    /* 覆盖名 → shopId：店家改店名后，新店名也能定位到原店铺（找不到返回 null） */
  shopIdOf(shopName) {
    for (const [id, ov] of Object.entries(QM_STORE.state.shopProfile || {})) {
      if (ov && ov.name && ov.name === shopName) return id;
    }
    return null;
  },
  /* 店铺名展示映射：该店有改名覆盖则显示新名（商品卡片 / 各处原店名渲染统一走这里） */
  displayShopName(name) {
    const s = QM_MOCK.shopServices[name];
    const ov = s && QM_STORE.state.shopProfile[s.id];
    return ov && ov.name ? ov.name : name;
  },
  /* 按店铺名取店铺信息（合并本地档案，供店铺页 / 详情页浏览；
     店家改店名后，新店名通过档案映射同样可定位到原店铺；
     本地档案 shopProfile 同时承载「店家保存的即时覆盖」与「后端返回的真实店铺档案」，
     新开通的店铺（演示数据里没有）也在这里，因此本地档案优先于演示数据） */
  shopService(shopName) {
    const byOverride = QM_STORE.shopIdOf(shopName);
    const demo = byOverride ? QM_MOCK.serviceById(byOverride) : QM_MOCK.shopServices[shopName];
    const local = (byOverride && QM_STORE.state.shopProfile[byOverride]) || null;
    if (!demo && !local) return QM_MOCK.serviceOf(shopName);   // 未知店铺：兜底平台客服信息
    const base = Object.assign({}, demo || {}, local || {});
    const id = base.id || byOverride;
    const ov = id ? QM_STORE.state.shopProfile[id] : null;
    return ov ? Object.assign({}, base, ov, { id }) : Object.assign({}, base, id ? { id } : {});
  },

  /* 记住（合并）一份店铺档案：来源可以是后端返回的店铺信息，也可以是本地保存的覆盖。
     后端 GET /shops/profile、GET /shops/{id}、POST /shops 成功后就调这里，
     工作台 / 店铺主页 / 商品卡片随即显示真实店铺；接口未实现时页面用已有档案兜底。 */
  rememberShop(shop) {
    if (!shop) return null;
    const id = shop.id || shop.shopId || shop.shop_id;
    if (!id) return null;
    const prev = QM_STORE.state.shopProfile[id] || {};
    const next = Object.assign({}, prev, shop, { id });
    /* 后端字段名 intro → 前端展示字段 shopIntro（店铺简介），两边都保留便于复用 */
    if (next.intro !== undefined && next.shopIntro === undefined) next.shopIntro = next.intro;
    if (next.shopIntro !== undefined && next.intro === undefined) next.intro = next.shopIntro;
    if (!next.color) next.color = prev.color || '#ff6a2b';
    /* 无实质变化时不写盘、不广播：进页面拉一次后端档案不会触发多余的持久化与重渲染 */
    const changed = !QM_STORE.state.shopProfile[id] || Object.keys(shop).some(k => prev[k] !== shop[k]);
    QM_STORE.state.shopProfile[id] = next;
    if (!changed) return next;
    persist();
    emit('shopProfile', next);
    return next;
  },

  seller: {
      /* 当前账号的店铺：返回 {username, ownerName, shopName, id, shopId, userId, name, color, intro,
         avatar, shopIntro, score, fans, founded}；未开店（无 shopId）返回 null。
         演示店铺（mock.shopServices）与本地档案（后端返回 / 开店结果）合并，本地档案优先——
         因此改过的店名 / 头像 / 简介、以及新开通的店铺都能立即生效。
         shopId 既不在演示数据、也没有本地档案时视为未开店。 */
      current() {
        const u = QM_STORE.state.user;
        if (!u || !u.shopId) return null;
        const id = u.shopId;
        const demo = QM_MOCK.serviceById(id);
        const local = QM_STORE.state.shopProfile[id] || null;
        if (!demo && !local) return null;
        const base = Object.assign({}, demo || {}, local || {});
        /* 注意：mock 店铺条目的 name 是「店主昵称」，店名要看档案里的 name 或 mock 的键名 */
        const shopName = (local && local.name) || QM_MOCK.shopNameById(id) || base.name || '';
        return {
          username: u.userId,
          ownerName: u.nickname,
          shopName,
          id,                                  // 店铺标识
          shopId: id,
          userId: base.userId || u.userId,     // 开店用户账号，兼作聊天身份（买家就是和这位用户聊天）
          name: base.name || '',
          color: base.color || '#ff6a2b',
          intro: base.intro || '',
          score: base.score,
          fans: base.fans,
          founded: base.founded,
          avatar: base.avatar || '',           // OSS 地址或 emoji
          shopIntro: base.shopIntro !== undefined ? base.shopIntro : '',
          isDemo: !local && !!demo             // 只有演示数据、没有本地/真实档案
        };
      },
      /* 店铺资料保存（写入本地档案并落盘）：patch 形如 { name, avatar, shopIntro } */
      saveProfile(shopId, patch) {
        if (!shopId || !patch) return null;
        return QM_STORE.rememberShop(Object.assign({}, patch, { id: shopId }));
      },
      /* 全部店铺（含开店用户映射），供开店引导 / 演示账号提示使用 */
      list() {
        return Object.keys(QM_MOCK.shopServices).map(name => Object.assign({ shopName: name }, QM_MOCK.shopServices[name]));
      },
      /* 店铺标识 / 开店用户 id → 店铺名 */
      shopName(shopId) { return QM_MOCK.shopNameById(shopId); }
      /* 注：① 店家订单改走后端接口 QM_API.seller.orders()（原本地演示订单查询已移除）；
             ② 消息统一在「消息中心」（ChatView）处理——与任何联系人一样，
                聊天就是对端的用户账号，不再有独立商家消息中心 */
    }
  };

export default QM_STORE;
