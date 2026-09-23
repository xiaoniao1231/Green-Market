/* =========================================================
   青集市 · store.js —— 状态管理与本地持久化
   本地只保存「用户自己的数据」与「后端下发的快照」：
   购物车（后端缓存）/ 订单 / 收藏 / 地址 / 优惠券 / 聊天记录 / 店铺档案。
   商品库、店铺库、演示账号等静态演示数据已全部移除，
   这些内容一律以后端接口为准（商品 / 订单接口尚未实现时页面显示空态）。
   注意：favorites / addresses / cart 只是「后端数据的本地缓存」，写入一律先成功后同步，
   接口失败时不会本地假成功（详见 api.js 的 favorites / addresses / cart 段）。
   ========================================================= */

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
      cart: [],              // {key, productId, sku, qty, checked, price?, img?, product?} —— 后端数据缓存（api.js 写穿）
      favorites: [],         // productId[]
      /* 订单 / 地址 / 优惠券：本地不预置任何演示数据。
         目前这三项仍由本地存储承载（后端 /orders 等接口尚未实现），
         用户实际产生数据后才有内容；后端接口落地后改为服务端数据源。 */
      orders: [],
      addresses: [],
      coupons: [],
      contacts: [],        // 会话联系人：由「联系卖家」创建，或从 /messages/conversations 恢复
      chats: {},
      shopFavs: [],        // 关注的店铺名（用户可关注店铺，店铺页 / 详情页使用）
      /* 本地店铺档案：{ shopId: { id, name, avatar, color, intro, shopIntro, score, fans, founded, ... } }
         唯一来源是后端：GET /shops/profile、GET /shops/{id}、POST /shops 返回后由 rememberShop 写入。
         工作台 / 店铺主页 / 详情页 / 商品卡片统一读取这里（未拉取到时页面按 profileLoaded=false 处理）。 */
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
      if (!QM_STORE.state.contacts) QM_STORE.state.contacts = [];
      QM_STORE.state.shopFavs = QM_STORE.state.shopFavs || [];
      /* 独立商家账号体系已移除：清理旧版 sellerUser 会话数据（店铺绑定统一挂在 user.shopId 上） */
      if (QM_STORE.state.sellerUser) { delete QM_STORE.state.sellerUser; save(); }
      /* 会话保留规则：只有「有实际聊天记录」的联系人才算会话 ——
         没聊过天就不出现在会话列表（再次从商品详情进入时仍可创建，聊天后自然保留）。 */
      const chatKeys = new Set(Object.keys(QM_STORE.state.chats));
      QM_STORE.state.contacts = QM_STORE.state.contacts.filter(
          c => c && chatKeys.has(c.id)
      );
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
          QM_STORE.state.contacts = [];
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

    /* ---------- 购物车 ----------
       购物车是**后端数据**（strict 接口，见 api.js 的 cart 段）：本地 store 只存
       api.js 写穿缓存（syncCartFromApi）与服务端下发的商品快照，加购 / 删改一律走后端。
       条目里的 checked（勾选）是纯前端态，不落库，刷新后由 api.js 按条目保持。 */
    cart: {
      list() {
        /* 购物车条目自带服务端下发的商品快照（api.js 写入 item.product）；
           没有快照的条目直接跳过（不渲染空壳）。 */
        return QM_STORE.state.cart.map(item => Object.assign({}, item, { product: item.product || null }))
          .filter(item => item.product);
      },
      /* 条目单价：加购时选中的**款式价**优先，没有款式价才用商品当前默认价。
         购物车页 / 结算弹窗的单价与小计统一走这里，避免出现「卡片显示款式价、合计按默认价」的错账。 */
      unitPrice(item) {
        const skuPrice = Number(item && item.price);
        if (Number.isFinite(skuPrice) && skuPrice > 0) return skuPrice;
        const p = item && item.product;
        return (p && Number(p.price)) || 0;
      },
      /* 条目小计 = 单价 × 数量 */
      subTotal(item) { return QM_STORE.cart.unitPrice(item) * ((item && item.qty) || 0); },
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
        return QM_STORE.cart.selected().reduce((sum, i) => sum + QM_STORE.cart.subTotal(i), 0);
      }
    },

    /* ---------- 收藏 ----------
       productId 统一按「字符串」比较：后端商品 id 是数字（详情页 product.id 来自 /products/{id}），
       而收藏列表由 /favorites 下发后统一 String 化。历史 bug 正是 has(数字) 在字符串数组里
       永远为 false —— 后端已写入收藏，详情页爱心却始终空心，再点一次还会走「取消收藏」分支。
       这里用 String(v) 归一化比较，同时兼容本地存储里残留的旧格式。 */
    fav: {
      has(id) {
        const key = String(id);
        return QM_STORE.state.favorites.some(v => String(v) === key);
      },
      add(id) {
        const key = String(id);
        const list = QM_STORE.state.favorites;
        if (!list.some(v => String(v) === key)) list.unshift(key);
        save(); emit('favorites');
      },
      remove(id) {
        const key = String(id);
        const list = QM_STORE.state.favorites;
        const idx = list.findIndex(v => String(v) === key);
        if (idx >= 0) list.splice(idx, 1);
        save(); emit('favorites');
      },
      clear() {
        QM_STORE.state.favorites.length = 0;
        save(); emit('favorites');
      },
      /* 收藏商品的完整信息由后端下发（GET /favorites）；本地只存 productId，
         没有后端数据时返回空数组，不编造商品内容（收藏页走 QM_API.favorites.list）。 */
      list() { return []; }
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

    /* ---------- 我的店铺（账号持有店铺 shopId 即已开店；账号本身仍是普通用户） ---------- */
    /* 覆盖名 → shopId：店家改店名后，新店名也能定位到原店铺（找不到返回 null） */
  shopIdOf(shopName) {
    for (const [id, ov] of Object.entries(QM_STORE.state.shopProfile || {})) {
      if (ov && ov.name && ov.name === shopName) return id;
    }
    return null;
  },
  /* 店铺名展示映射：该店有档案则显示档案里的店名（商品卡片等处的店名渲染统一走这里） */
  displayShopName(name) {
    const id = QM_STORE.shopIdOf(name);
    const ov = id ? (QM_STORE.state.shopProfile || {})[id] : null;
    return (ov && ov.name) || name;
  },
  /* 按店铺名取店铺信息：**只依据后端店铺档案**（shopProfile，由 /shops/* 接口写入）。
     档案里没有时返回一个最小占位对象（profileLoaded=false），调用方据此去调
     GET /shops/{id} 把档案补齐，而不是渲染一份编造的店铺信息。 */
  shopService(shopName) {
    const id = QM_STORE.shopIdOf(shopName);
    const local = id ? (QM_STORE.state.shopProfile || {})[id] : null;
    if (!local) {
      return { id: id || '', name: shopName, shopName, color: '#ff6a2b', profileLoaded: false };
    }
    return Object.assign({}, local, { id, shopName: local.name || shopName, profileLoaded: true });
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
         avatar, shopIntro, score, fans, founded, profileLoaded}；未开店返回 null。

         ⚠️ 开店判定只看服务端：登录响应带回 shopId 即视为已开店，
         店名 / 头像 / 简介等展示字段一律来自后端店铺档案（shopProfile，由 /shops/* 写入）。
         profileLoaded=false 表示档案还没拉到（页面应去调 GET /shops/profile），
         此时仍算已开店，只是字段暂时为空 —— 不能据此判定"未开店"。 */
      current() {
        const u = QM_STORE.state.user;
        if (!u || !u.shopId) return null;
        const id = u.shopId;
        const local = (QM_STORE.state.shopProfile && QM_STORE.state.shopProfile[id]) || null;
        const base = local || {};
        return {
          username: u.userId,
          ownerName: u.nickname,
          shopName: base.name || '',
          id,                                  // 店铺标识
          shopId: id,
          /* 开店用户账号，兼作聊天身份（买家就是和这位用户聊天）；
             后端 /shops/profile 返回 ownerUserId，/shops/{id} 返回 ownerUserId 或 userId */
          userId: base.ownerUserId || base.userId || u.userId,
          name: base.name || '',
          color: base.color || '#ff6a2b',
          intro: base.intro || '',
          score: base.score,
          fans: base.fans,
          founded: base.founded,
          avatar: base.avatar || '',           // OSS 地址或 emoji
          shopIntro: base.shopIntro !== undefined ? base.shopIntro : '',
          profileLoaded: !!local               // 档案是否已就绪（false 时页面应去调 GET /shops/profile）
        };
      },
      /* 店铺资料保存（写入本地档案并落盘）：patch 形如 { name, avatar, shopIntro } */
      saveProfile(shopId, patch) {
        if (!shopId || !patch) return null;
        return QM_STORE.rememberShop(Object.assign({}, patch, { id: shopId }));
      },
      /* 已从后端拉取过档案的店铺列表 */
      list() {
        return Object.values(QM_STORE.state.shopProfile || {});
      },
      /* 店铺标识 / 开店用户 id → 店铺名（档案里没有返回 null） */
      shopName(shopId) {
        const ov = (QM_STORE.state.shopProfile || {})[shopId];
        return (ov && ov.name) || null;
      }
      /* 注：① 店家订单改走后端接口 QM_API.seller.orders()（原本地演示订单查询已移除）；
             ② 消息统一在「消息中心」（ChatView）处理——与任何联系人一样，
                聊天就是对端的用户账号，不再有独立商家消息中心 */
    }
  };

export default QM_STORE;
