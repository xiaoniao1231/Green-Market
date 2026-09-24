<script setup>
/* =========================================================
   青集市 · App.vue —— 应用外壳（原 index.html 骨架 + app.js 启动逻辑）
   页面骨架、全局事件代理、导航状态、角标、后端状态胶囊、登录态守卫。
   ========================================================= */
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import QM_CFG from './core/config.js';
import QM_JWT from './core/jwt.js';
import { CATEGORIES } from './core/catalog.js';
import QM_API from './core/api.js';
import QM_STORE from './core/store.js';
import QM_UI from './core/ui.js';
import QM_CHAT_SOCKET from './core/chatSocket.js';
import QM_CHAT_INBOX from './core/chatInbox.js';
import { registerViewRefresh } from './core/viewRefresh.js';

const { esc, toast, confirmDialog, modal, artStyle, artHtml } = QM_UI;
const router = useRouter();

/* ---------- 响应式状态 ---------- */
const user = ref(null);
const cartCount = ref(0);
const unread = ref(0);
const chipClass = ref('');
const chipHtml = ref('');
const viewKey = ref(0);

/* ---------- 渲染：用户区 / 角标 / 后端状态胶囊 ---------- */
function renderUserArea() {
  user.value = QM_STORE.state.user;
}
function renderBadges() {
  cartCount.value = QM_STORE.cart.count();
  unread.value = QM_STORE.chat.unreadTotal();
}
function renderBackendChip(online) {
  /* chipClass 只负责状态类：容器上的静态 class="backend-chip" 已由模板提供，
     原实现把基础类重复写两遍（静态 + 动态），生成的 class 属性里会出现两个 backend-chip。
     只在异常时提示：后端连通是预期状态，常驻「● 后端已连接」属于调试信息，
     不该出现在用户界面上（模板用 v-show 控制显隐，空串即隐藏） */
  if (online === false) { chipClass.value = 'offline'; chipHtml.value = '○ 后端未连接'; }
  else { chipClass.value = ''; chipHtml.value = ''; }
}

/* ---------- 分类浮层 ---------- */
let flyoutEl = null;
let allCatsBtn = null;
let hideTimer = null;
/* 显示 / 延迟隐藏（鼠标从按钮移到浮层时不会闪断） */
function showFlyout() {
  clearTimeout(hideTimer);
  if (flyoutEl) flyoutEl.classList.remove('hidden');
}
function hideFlyoutSoon() {
  clearTimeout(hideTimer);
  hideTimer = setTimeout(() => { if (flyoutEl) flyoutEl.classList.add('hidden'); }, 200);
}
function hideFlyoutNow() { clearTimeout(hideTimer); if (flyoutEl) flyoutEl.classList.add('hidden'); }

/* 分类浮层：分类与子类目都使用真实 href（#/category/…、#/search?q=…），
   既保留原有点击跳转行为，又让键盘与辅助技术可直接聚焦、访问。 */
function buildFlyout() {
  flyoutEl.innerHTML = `<h4>☰ 全部商品分类</h4><div class="flyout-grid">
    ${CATEGORIES.map(c => `
      <div class="flyout-cat">
        <b><a href="#/category/${encodeURIComponent(c.id)}">${c.icon} ${esc(c.id)}</a></b>
        ${c.subs.map(s => `<a href="#/search?q=${encodeURIComponent(s)}">${esc(s)}</a>`).join('')}
      </div>`).join('')}
  </div>`;
  allCatsBtn.addEventListener('mouseenter', showFlyout);
  allCatsBtn.addEventListener('mouseleave', hideFlyoutSoon);
  /* 注意：#catFlyout 的 mouseenter/mouseleave 已在模板上绑定（@mouseenter/@mouseleave），
     这里不再重复 addEventListener，否则每次进出浮层都会触发两次（原实现的双重绑定） */
  /* 点击任一分类链接后立即收起浮层 */
  flyoutEl.addEventListener('click', e => { if (e.target.closest('a')) hideFlyoutNow(); });
}

/* 后端状态胶囊：点击手动重新探测 */
async function checkBackend() {
  renderBackendChip(null);
  renderBackendChip(await QM_API.health());
}

/* ---------- 全局实时通道：登录即在线 ----------
   WebSocket 连接提升到应用级：登录成功 / 恢复登录态后立即建立，
   用户即刻进入后端在线表并广播上线；退出 / 令牌失效 / 后端离线时关闭。
   （原实现把连接放在消息中心页内，只有点进消息中心才会"上线"。） */
async function ensureChatSocket() {
  if (!QM_STORE.state.user || !QM_STORE.state.user.token) return;
  await QM_CHAT_SOCKET.ensure();
}

/* 登录后从后端拉取商城数据（购物车 / 收藏 / 订单「全部」）同步本地 store 与角标；
   顺带同步一次会话列表 —— 未读数只有服务端知道，不拉这一次，不进消息中心就永远没有
   未读角标（顶部「消息中心」与首页会员卡的「消息」入口都靠它）；
   后端未实现时 api.js 自动回退本地演示数据，无副作用、不阻塞登录流程 */
async function refreshServerData() {
  if (!QM_STORE.state.user || !QM_STORE.state.user.token) return;
  await Promise.allSettled([
    QM_API.cart.list(), QM_API.favorites.list(), QM_API.orders.list(''),
    QM_CHAT_INBOX.syncConversations()   // 会话未读数（失败静默：不影响登录流程）
  ]);
}
function onUserChange(u) {
  renderUserArea();
  if (u && u.token) { ensureChatSocket(); refreshServerData(); }
  else QM_CHAT_SOCKET.close();
}
function onBackendChange(online) {
  renderBackendChip(online);
  /* 后端恢复在线且已登录 → 重建实时通道；后端离线 → 关闭通道（用户下线） */
  if (online) ensureChatSocket();
  else QM_CHAT_SOCKET.close();
}

/* 未登录操作拦截：需要登录的功能先跳独立登录页，登录后回跳当前页面 */
function requireLogin() {
  if (QM_STORE.state.user) return true;
  const current = router.currentRoute.value;
  const redirect = current.fullPath && current.fullPath !== '/login' ? current.fullPath : '/home';
  toast('请先登录后再继续', 'error');
  router.push({ path: '/login', query: { redirect } });
  return false;
}

/* ---------- 快捷加购的款式选择（商品卡「＋购物车」带规格时弹出，口径与详情页一致） ---------- */
const valOf = v => (typeof v === 'string' ? v : (v && v.v) || '');
const valImg = v => (typeof v === 'string' ? '' : (v && v.img) || '');
const valPrice = v => {
  const n = Number(typeof v === 'object' && v ? v.price : NaN);
  return Number.isFinite(n) && n > 0 ? n : null;
};
const moneyText = n => (Number.isInteger(Number(n)) ? String(Number(n)) : Number(n).toFixed(2));
function skuTextOf(skus, pick) { return skus.map((g, gi) => valOf(g.values[pick[gi]])).join(' / '); }
function skuPriceOf(product, pick) {
  let unit = Number(product.price) || 0;
  (product.skus || []).forEach((g, gi) => {
    const sp = valPrice((g.values || [])[pick[gi]]);
    if (sp !== null) unit = sp;
  });
  return unit;
}
function pickArtOf(product, pick, lastGroup) {
  const skus = (product && Array.isArray(product.skus)) ? product.skus : [];
  let first = null;
  for (let gi = 0; gi < skus.length; gi++) {
    const v = (skus[gi].values || [])[pick[gi]];
    const img = valImg(v);
    if (!img) continue;
    if (!first) first = { img };
    if (gi === lastGroup) return { img };
  }
  return first || (product && product.art) || null;
}
/* 商品卡「＋购物车」：带规格商品先选款式（图/价随选实时更新），确认后按所选款式加购 */
function openSkuPickerForAdd(p) {
  const skus = Array.isArray(p.skus) ? p.skus : [];
  if (!skus.length) return;
  const pick = skus.map(() => 0);
  let lastGroup = -1;
  let pickArt = pickArtOf(p, pick, lastGroup);
  const m = modal(`
    <div class="sku-picker">
      <div class="sku-picker-head">
        <span id="skuPickArt" class="ci-art" style="${artStyle(pickArt)}">${artHtml(pickArt)}</span>
        <div class="sku-picker-info">
          <h3 class="ellipsis-2">${esc(p.title)}</h3>
          <p class="sku-picker-price">款式价：¥<b id="skuPickPrice">${esc(moneyText(skuPriceOf(p, pick)))}</b></p>
        </div>
      </div>
      <div class="sku-picker-body">
        ${skus.map((g, gi) => `
          <div class="sku-group">
            <b>${esc(g.name || '规格')}：</b>
            ${(g.values || []).map((v, vi) => `
              <button class="sku-chip${vi === pick[gi] ? ' active' : ''}" data-group="${gi}" data-vi="${vi}">
                ${esc(valOf(v))}${valPrice(v) ? `<em class="sku-chip-price">¥${esc(moneyText(valPrice(v)))}</em>` : ''}
              </button>`).join('')}
          </div>`).join('')}
      </div>
      <div class="modal-actions">
        <button class="btn btn-plain" data-close>取消</button>
        <button class="btn btn-primary btn-lg" id="skuPickConfirm" style="flex:1">加入购物车</button>
      </div>
    </div>`, { wide: true });

  m.root.querySelectorAll('[data-group]').forEach(btn => btn.onclick = () => {
    const gi = Number(btn.dataset.group);
    const vi = Number(btn.dataset.vi);
    pick[gi] = vi;
    lastGroup = gi;
    m.root.querySelectorAll(`[data-group="${gi}"]`).forEach(x => x.classList.toggle('active', Number(x.dataset.vi) === vi));
    m.root.querySelector('#skuPickPrice').textContent = moneyText(skuPriceOf(p, pick));
    const artEl = m.root.querySelector('#skuPickArt');
    const art = pickArtOf(p, pick, lastGroup);
    artEl.style.cssText = artStyle(art);
    artEl.innerHTML = artHtml(art);
  });
  m.root.querySelector('#skuPickConfirm').onclick = async () => {
    try {
      await QM_API.cart.add(p.id, skuTextOf(skus, pick), 1, skuPriceOf(p, pick));
      m.close();
      toast('已加入购物车 🛒', 'success');
    } catch (e) { toast((e && e.message) || '加入购物车失败，请稍后重试', 'error'); }
  };
}

/* ---------- 全局事件代理（原 app.js 同款） ---------- */
async function onGlobalClick(e) {
  const t = e.target.closest('[data-action]');
  if (!t) return;
  const action = t.dataset.action;

  switch (action) {
    /* 登录/注册统一走独立登录页（/login?mode=register 直达注册窗口） */
    case 'open-login': router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } }); break;
    case 'open-register': router.push({ path: '/login', query: { mode: 'register', redirect: router.currentRoute.value.fullPath } }); break;
    case 'logout':
      if (await confirmDialog('退出登录', '确定退出当前账号吗？', '退出', true)) {
        QM_API.auth.logout();
        QM_STORE.user.logout();
        toast('已退出登录');
        renderUserArea();
        viewKey.value++;
      }
      break;
    case 'open-product': router.push('/detail/' + encodeURIComponent(t.dataset.id)); break;
    case 'open-order': router.push('/order/' + encodeURIComponent(t.dataset.id)); break;
    case 'quick-add-cart': {
      if (!requireLogin()) return;
      /* 先取商品详情拿规格：带规格（每组都有可选值）→ 弹款式选择；
         无规格 → 直接按默认规格加购（收藏页等列表未带 skus 的入口也统一走这里） */
      let product;
      try { product = await QM_API.products.get(t.dataset.id); }
      catch (e) { return toast((e && e.message) || '商品信息获取失败，请稍后重试', 'error'); }
      if (product && Array.isArray(product.skus) && product.skus.length
        && product.skus.every(g => Array.isArray(g.values) && g.values.length > 0)) {
        openSkuPickerForAdd(product);
        return;
      }
      try {
        await QM_API.cart.add(t.dataset.id, '默认', 1);
        toast('已加入购物车 🛒', 'success');
      } catch (e) { toast((e && e.message) || '加入购物车失败，请稍后重试', 'error'); }
      break;
    }
    case 'toggle-fav': {
      if (!requireLogin()) return;
      const id = t.dataset.id;
      /* 收藏接口已改为 strict（不做本地离线回退）：失败必须如实提示。
         旧行为是后端报错时静默写进浏览器存储并提示「已收藏」，数据库里却没有记录。 */
      try {
        if (QM_STORE.fav.has(id)) {
          await QM_API.favorites.remove(id);
          toast('已取消收藏');
        } else {
          await QM_API.favorites.add(id);
          toast('已收藏 ♥', 'success');
        }
      } catch (e) {
        toast((e && e.message) || '收藏操作失败，请稍后重试', 'error');
      }
      break;
    }
    case 'remove-fav': {
      /* 收藏页商品卡的单条取消（含已删除 / 已下架商品）：
         成功后 api.js 会 emit('favorites')，收藏页订阅后自动刷新列表 */
      if (!requireLogin()) return;
      try {
        await QM_API.favorites.remove(t.dataset.id);
        toast('已取消收藏');
      } catch (e) {
        toast((e && e.message) || '取消收藏失败，请稍后重试', 'error');
      }
      break;
    }
    case 'goto-category': router.push('/category/' + encodeURIComponent(t.dataset.id)); break;
    case 'goto-search': router.push('/search?q=' + encodeURIComponent(t.dataset.q || '')); break;
    case 'goto-shop': {
      /* 带上后端下发的店铺标识（?id=）：店铺页据此直接定位，不必依赖本地「店名 → shopId」档案 */
      const sid = t.dataset.shopId;
      router.push('/shop/' + encodeURIComponent(t.dataset.id) + (sid ? '?id=' + encodeURIComponent(sid) : ''));
      break;
    }
    case 'goto-seller': router.push('/seller'); break;
    case 'goto-chat': router.push('/chat' + (t.dataset.id ? '?peer=' + encodeURIComponent(t.dataset.id) : '')); break;
    case 'goto-placeholder': router.push('/placeholder/' + encodeURIComponent(t.dataset.id || '')); break;
    case 'goto-orders': router.push('/orders' + (t.dataset.id ? '?status=' + encodeURIComponent(t.dataset.id) : '')); break;
    case 'goto-cart': router.push('/cart'); break;
    case 'goto-fav': router.push('/favorites'); break;
  }
}

/* 搜索框 */
function onSearchSubmit(e) {
  e.preventDefault();
  const q = e.target.querySelector('#searchInput').value.trim();
  if (!q) return toast('请输入搜索关键词', 'error');
  router.push('/search?q=' + encodeURIComponent(q));
}

/* 导航高亮 */
function navActive(route) {
  const map = { home: 'home', chat: 'chat', placeholder: 'placeholder' };
  const key = map[route.name] || '';
  document.querySelectorAll('.nav-inner a').forEach(a => {
    a.classList.toggle('active', key ? (a.dataset.nav === key) : false);
  });
}

/* ---------- 令牌验证与登录态守卫（jwt.js，见接口文档 1.4.4） ---------- */
/* 启动时校验本地保存的 JWT：损坏/篡改/过期 → 自动登出并回登录页 */
async function verifySavedSession() {
  const saved = QM_STORE.state.user;
  if (!saved || !saved.token) return; // 未登录或演示用户（无令牌），无需校验
  let verdict;
  try { verdict = await QM_JWT.verify(saved.token, QM_CFG.JWT_SECRET || ''); }
  catch (e) { verdict = { ok: false, reason: '令牌校验异常' }; }
  if (verdict.ok) return;
  QM_STORE.user.expire(verdict.reason || '登录已过期，请重新登录', { fromBoot: true });
}

/* 周期守卫：本地令牌过期 / 失效时自动登出（在浏览过程中触发） */
function sessionGuard() {
  const saved = QM_STORE.state.user;
  if (!saved || !saved.token) return;
  const verdict = QM_JWT.check(saved.token);
  if (verdict.ok) return;
  QM_STORE.user.expire(verdict.reason || '登录已过期，请重新登录');
}

/* auth-expired 事件：清本地登录态后的统一收尾（store.user.expire 已清空用户并刷新顶部条） */
function onAuthExpired(info) {
  renderUserArea();
  renderBadges();
  /* 强制重挂载当前页面：否则个人中心等页面仍按旧的登录态渲染（令牌过期后仍显示已登录） */
  viewKey.value++;
  const current = router.currentRoute.value;
  if (current.name === 'login') return; // 已在登录页，无需再跳
  if (info && info.fromBoot) toast((info.message) || '登录已过期，请重新登录', 'error');
  const redirect = current.fullPath && current.fullPath !== '/login' ? current.fullPath : '/home';
  router.push({ path: '/login', query: { redirect } });
}

/* ---------- 订阅状态事件 ---------- */
const offs = [
  QM_STORE.on('cart', renderBadges),
  QM_STORE.on('chat', renderBadges),
  QM_STORE.on('user', onUserChange),
  QM_STORE.on('favorites', renderBadges),
  QM_STORE.on('backend', onBackendChange),
  QM_STORE.on('auth-expired', onAuthExpired)
];
const navOff = router.afterEach(navActive);

let guardTimer = null;
let globalClickHandler = null;
let offInboxFrame = null;

/* ---------- 初始化 ---------- */
onMounted(() => {
  /* QM_STORE.load() 已提前到 src/main.js 挂载前执行（保证子页面 setup 时 state 可用） */
  renderUserArea();
  renderBadges();
  flyoutEl = document.getElementById('catFlyout');
  allCatsBtn = document.getElementById('allCatsBtn');
  buildFlyout();
  globalClickHandler = e => { onGlobalClick(e).catch(err => console.error(err)); };
  document.addEventListener('click', globalClickHandler);
  /* 全局订阅实时帧：消息中心页挂载时由该页处理并渲染，其余页面（首页 / 商品页 / 订单页…）
     由 chatInbox 兜底落库并累加未读 —— 否则不在消息中心就收不到任何未读提示 */
  offInboxFrame = QM_CHAT_SOCKET.onMessage(frame => {
    QM_CHAT_INBOX.handleFrame(frame);
    /* 卖家催发货提醒（SELLER_REMIND）：与消息中心是否打开无关，店家在任意页面都要收到。
       后端已把提醒落库（卖家订单列表靠它打「催发货」角标），这里只负责「让店家立刻知道」，
       再广播一个本地事件，让店家相关页面自行重拉列表刷新角标 */
    if (!frame || frame.type !== 'SELLER_REMIND') return;
    const msg = frame.message || {};
    const me = QM_STORE.state.user;
    if (me && msg.receiverId && String(msg.receiverId) !== String(me.userId)) return;
    QM_UI.toast(msg.content || '买家提醒你尽快发货', 'success');
    QM_STORE.emit('sellerRemind', msg);
  });
  QM_API.health().then(online => {
    renderBackendChip(online);
    /* 刷新页面恢复登录态（sessionStorage）后立即上线，无需先进消息中心 */
    if (online) ensureChatSocket();
  });
  /* 恢复登录态（刷新页面）后同步一次服务端商城数据（购物车角标 / 收藏状态等） */
  refreshServerData();
  verifySavedSession();
  guardTimer = setInterval(sessionGuard, QM_CFG.TOKEN_CHECK_INTERVAL || 60 * 1000);
  registerViewRefresh(() => { viewKey.value++; });
  navActive(router.currentRoute.value);
});

onBeforeUnmount(() => {
  offs.forEach(off => off());
  navOff();
  if (offInboxFrame) offInboxFrame();
  QM_CHAT_SOCKET.close(); // 应用销毁时关闭全局实时通道
  if (guardTimer) clearInterval(guardTimer);
  if (globalClickHandler) document.removeEventListener('click', globalClickHandler);
});
</script>

<template>
  <!-- ===== 顶部横条 ===== -->
  <div class="top-strip">
    <div class="shell top-inner">
      <span class="welcome">您好，欢迎来到青集市！</span>
      <span id="topUser" class="top-user" :class="{ hidden: !user }">Hi，<b id="topNickname">{{ user ? user.nickname : '' }}</b></span>
      <a id="topLogin" href="#/login" :class="{ hidden: !!user }">请登录</a>
      <i class="top-sep"></i>
      <a href="#/profile">个人中心</a><i class="top-sep"></i>
      <a href="#/orders">我的订单</a><i class="top-sep"></i>
      <a href="#/chat">消息中心<b id="msgBadge" class="count-badge" :class="{ hidden: !unread }">{{ unread }}</b></a><i class="top-sep"></i>
      <a href="#/favorites">我的收藏</a>
      <span class="top-spacer"></span>
      <a href="#/seller" title="店铺绑定在当前用户账号下，开店后仍用这一个账号管理">我的店铺</a>
    </div>
  </div>

  <!-- ===== 头部：logo + 搜索 + 购物车 ===== -->
  <header class="main-header shell">
    <a class="logo" href="#/home">
      <span class="logo-mark">青</span>
      <span class="logo-text">青集市<small>QING MARKET</small></span>
    </a>
    <form id="searchForm" class="search-box" @submit="onSearchSubmit">
      <input id="searchInput" placeholder="搜索好物 / 店铺 / 分类" autocomplete="off" maxlength="30" />
      <button type="submit">搜 索</button>
    </form>
    <a class="header-cart" href="#/cart" aria-label="购物车">
      <span class="cart-glyph" aria-hidden="true">🛒<em id="cartCount">{{ cartCount }}</em></span>
      <span>购物车</span>
    </a>
    <!-- 后端状态胶囊：只在未连接时出现（v-show 由 chipHtml 是否为空驱动） -->
    <button v-show="chipHtml" id="backendChip" class="backend-chip" :class="chipClass" v-html="chipHtml" title="点击重新检测后端连通性" @click="checkBackend"></button>
  </header>

  <!-- ===== 主导航 ===== -->
  <nav class="main-nav">
    <div class="shell nav-inner">
      <button class="all-cats" id="allCatsBtn">☰ 全部分类</button>
      <a href="#/home" data-nav="home">首页</a>
      <a href="#/home?sec=flash" data-nav="flash">限时秒杀</a>
      <a href="#/home?sec=recommend" data-nav="recommend">猜你喜欢</a>
      <a href="#/chat" data-nav="chat">消息中心</a>
    </div>
  </nav>
  <div id="catFlyout" class="cat-flyout hidden" @mouseenter="showFlyout" @mouseleave="hideFlyoutSoon"></div>

  <!-- ===== 页面视图 ===== -->
  <main id="view" class="shell">
    <router-view :key="viewKey" />
  </main>

  <!-- ===== 页脚 ===== -->
  <footer class="site-footer">
    <div class="shell footer-inner">
      <div class="footer-brand">
        <span class="logo-mark">青</span>
        <div><b>青集市</b><p>发现值得买的日常</p></div>
      </div>
      <div class="footer-col"><b>服务支持</b><a href="#/chat">联系卖家</a><a href="#/placeholder/物流查询">物流查询</a><a href="#/placeholder/售后服务">售后服务</a></div>
      <div class="footer-col"><b>关于我们</b><a href="#/placeholder/平台介绍">平台介绍</a><a href="#/seller">卖家入驻</a><a href="#/chat">联系我们</a></div>
    </div>
    <p class="copyright">© 2026 青集市 · 课程演示项目 · 前后端分离，数据全部来自后端接口</p>
  </footer>

  <div id="modalRoot"></div>
  <div id="toastRoot" class="toast-root"></div>
</template>
