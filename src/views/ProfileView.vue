<script setup>
/* =========================================================
   青集市 · views/ProfileView.vue —— 个人中心页
   移植自 mall-web/js/pages/profile.js（页面结构 / 交互逻辑不变）
   含：登录入口（data-action=open-login/logout 由 App.vue 全局代理处理）、
   地址管理弹窗（新增 / 编辑 / 删除 / 设默认，接口见 docs/地址簿接口文档.md）、
   优惠券展示弹窗（对应预留接口 /coupons）、账户设置入口（#/account）。
   资料编辑（昵称 / 头像 / 性别 / 签名）已**整合进账户设置页**的「基本资料」表单，
   本页不再单独提供「编辑资料」按钮，避免同一件事有两个入口。
   ========================================================= */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import QM_UI from '../core/ui.js';
import QM_STORE from '../core/store.js';
import QM_API from '../core/api.js';
import openAddressModal from '../core/addressModal.js';

const { esc, toast, modal } = QM_UI;

/* ---------- 页面数据：登录态与各项计数 ----------
   原实现是「视图创建时读一次的常量快照」：新增/删除地址、加入购物车后，
   页面上的"N 个地址 / N 个收藏"不会跟着变（store 事件也不会触发本页重渲染）。
   改为 computed + 订阅 store 事件：任一数据变化时计数自动重算。 */
const user = computed(() => QM_STORE.state.user);
const favCount = computed(() => (QM_STORE.state.favorites || []).length);
const cartCount = computed(() => (QM_STORE.state.cart || []).length);
const orderCount = computed(() => (QM_STORE.state.orders || []).length);
const couponTick = ref(0);   // 券列表变化时自增，驱动角标重算（store.state 非响应式）
const couponCount = computed(() => { couponTick.value; return QM_STORE.coupon.list().length; });
const addrCount = computed(() => QM_STORE.addr.list().length);
/* 各状态订单数量（GET /orders/counts）：待付款 / 待发货 / 待收货 属「未完成」，
   已完成但还没评价的（done）也归入「待处理」—— 这几个数量用于「我的订单」处的角标提醒 */
const orderCounts = ref({});
const todoOrderCount = computed(() => {
  const c = orderCounts.value || {};
  return Number(c.pending || 0) + Number(c.paid || 0) + Number(c.shipped || 0) + Number(c.done || 0);
});
async function loadOrderCounts() {
  try { orderCounts.value = (await QM_API.orders.counts()) || {}; }
  catch (e) { /* 订单接口不可用时角标不显示，不阻塞页面其它内容 */ }
}
/* 优惠券同理：数量来自 GET /coupons（券由平台在数据库配置），strict —— 接口失败不展示旧缓存 */
async function loadCoupons() {
  try { await QM_API.coupons.list(); }
  catch (e) { QM_STORE.state.coupons = []; QM_STORE.emit('coupons'); }
}
/* 计数依赖的 store 事件（state 本身不是响应式的，靠这些事件驱动重算） */
const offs = [
  QM_STORE.on('cart', () => {}), QM_STORE.on('favorites', () => {}),
  QM_STORE.on('orders', () => { loadOrderCounts(); }), QM_STORE.on('addresses', () => {}),
  QM_STORE.on('user', () => { loadOrderCounts(); loadCoupons(); }), QM_STORE.on('coupons', () => { couponTick.value++; })
];
onMounted(() => { loadOrderCounts(); loadCoupons(); });
onBeforeUnmount(() => { offs.forEach(off => { try { off(); } catch (e) { /* 忽略 */ } }); });

/* ---------- 头像展示 ----------
   user.avatar 存的是图片完整地址（旧数据可能是 emoji 字符，显示时兼容回退；
   底色调色盘已移除，不再有 avatarColor 字段）。
   头像上传 / 资料保存统一在账户设置页（#/account）的「基本资料」表单里完成。 */
/* 判断头像是否为图片地址：https 为 OSS 落库地址，blob: 为弹窗内本地预览地址 */
const isAvatarImage = (v) => typeof v === 'string' && /^(https?:|blob:)/i.test(v.trim());

/* 资料摘要文案（性别 / 签名），显示在封面副标题 */
function userProfileText() {
  const me = user.value;
  if (!me) return '';
  const parts = [];
  if (me.gender === 'male') parts.push('♂ 男');
  else if (me.gender === 'female') parts.push('♀ 女');
  else parts.push('保密');
  if (me.signature) parts.push('「' + me.signature + '」');
  return parts.join(' · ');
}

/* ---------- 地址管理弹窗 ----------
   实现已抽到 core/addressModal.js（个人中心与购物车结算弹窗共用同一个弹窗）；
   保存 / 删除 / 设默认成功后由弹窗内部自行刷新列表。 */
async function addressModal() {
  await openAddressModal();
}

/* ---------- 优惠券弹窗：可领取的券（每人每张限领一次）+ 我的券 ---------- */
async function couponModal() {
  const m = modal(`
    <div>
      <h3>我的优惠券</h3>
      <p class="modal-sub">每张券每人只能领一次</p>
      <div id="couponClaim"></div>
      <h4 style="margin:16px 0 8px">我的券</h4>
      <div id="couponList"></div>
      <div class="modal-actions"><button class="btn btn-plain" data-close>关闭</button></div>
    </div>`, { wide: true });
  const claimBox = m.root.querySelector('#couponClaim');
  const listBox = m.root.querySelector('#couponList');

  const cardHtml = (title, amount, threshold, expire, right) => `
    <div class="addr-option" style="display:flex;align-items:center;gap:12px">
      <div style="flex:none;width:86px;text-align:center;background:var(--brand-soft);border-radius:8px;padding:10px 0">
        <b style="color:var(--brand);font-size:20px">¥${amount}</b>
        <small style="display:block;color:var(--text-3)">满 ${threshold} 可用</small>
      </div>
      <div style="flex:1">
        <b>${esc(title)}</b>
        <small style="display:block;color:var(--text-3)">有效期至 ${esc(expire || '—')}</small>
      </div>
      ${right}
    </div>`;

  const load = async () => {
    claimBox.innerHTML = '<p class="hint">正在加载…</p>';
    try {
      const [claimable, mine] = await Promise.all([QM_API.coupons.claimable(), QM_API.coupons.list()]);
      claimBox.innerHTML = claimable.length
        ? claimable.map(c => cardHtml(c.title, c.amount, c.threshold, c.expire,
            c.claimed ? '<span class="pill pill-gray">已领取</span>'
                      : `<button class="btn btn-primary btn-sm" data-claim="${esc(c.couponId)}">领取</button>`)).join('')
        : '<p class="hint">暂无可领取的优惠券</p>';
      listBox.innerHTML = mine.length
        ? mine.map(c => {
            const expired = QM_STORE.coupon.isExpired(c);
            const cls = (c.status === 'used' || expired) ? 'pill-gray' : 'pill-green';
            const text = c.status === 'used' ? '已使用' : (expired ? '已过期' : '未使用');
            return cardHtml(c.title, c.amount, c.threshold, c.expire, `<span class="pill ${cls}">${text}</span>`);
          }).join('')
        : '<p class="hint">还没有优惠券</p>';
    } catch (e) {
      claimBox.innerHTML = `<p class="hint" style="color:var(--accent-ink)">优惠券加载失败：${esc((e && e.message) || '接口不可用')}</p>`;
      listBox.innerHTML = '';
    }
  };

  claimBox.onclick = async e => {
    const btn = e.target.closest('[data-claim]');
    if (!btn) return;
    btn.disabled = true;
    try {
      await QM_API.coupons.claim(btn.dataset.claim);
      toast('领取成功', 'success');
      await load();      // 领取后刷新「可领取」与「我的券」
    } catch (err) {
      btn.disabled = false;
      toast('领取失败：' + ((err && err.message) || '未知错误'), 'error');
    }
  };

  await load();
}
</script>

<template>
  <div>
    <div class="page-head"><div><div class="crumb">首页 / 个人中心</div><h1>我的青集市</h1></div></div>
    <div class="profile-cover">
      <span class="member-avatar big"><img v-if="user && isAvatarImage(user.avatar)" :src="user.avatar" alt="头像" /><template v-else>{{ user ? ((user.avatar && user.avatar.trim()) ? user.avatar : user.nickname.slice(0, 1)) : '语' }}</template></span>
      <div>
        <h1>{{ user ? user.nickname : '轻语用户' }}</h1>
        <p>{{ user ? '账号 @' + user.userId + (userProfileText() ? ' · ' + userProfileText() : '') : '登录后享受完整服务 · 记录每一次心动的发现' }}</p>
      </div>
      <div class="cover-actions">
        <button v-if="user" class="btn" data-action="goto-account">账户设置</button>
        <button v-if="user" class="btn btn-plain" data-action="logout">退出登录</button>
        <button v-else class="btn" data-action="open-login">立即登录</button>
      </div>
    </div>
    <div class="profile-stats">
      <button data-action="goto-fav"><b>{{ favCount }}</b><small>收藏商品</small></button>
      <button data-action="goto-cart"><b>{{ cartCount }}</b><small>购物车</small></button>
      <button data-action="goto-orders"><b>{{ orderCount }}</b><small>全部订单</small></button>
      <button data-action="goto-coupon" @click="couponModal"><b>{{ couponCount }}</b><small>优惠券</small></button>
    </div>
    <div class="profile-panel">
      <h3>⌁ 我的订单 <small v-if="todoOrderCount" class="panel-tip">有 {{ todoOrderCount }} 笔待处理</small></h3>
      <div class="service-grid">
        <button data-action="goto-orders" data-id="pending">
          <span v-if="orderCounts.pending" class="s-badge">{{ orderCounts.pending }}</span>
          <span class="s-icon">◴</span><b>待付款</b><small>及时付款不错过好价</small>
        </button>
        <button data-action="goto-orders" data-id="paid">
          <span v-if="orderCounts.paid" class="s-badge">{{ orderCounts.paid }}</span>
          <span class="s-icon">▣</span><b>待发货</b><small>卖家正在准备</small>
        </button>
        <button data-action="goto-orders" data-id="shipped">
          <span v-if="orderCounts.shipped" class="s-badge">{{ orderCounts.shipped }}</span>
          <span class="s-icon">▤</span><b>待收货</b><small>物流实时可查</small>
        </button>
        <button data-action="goto-reviews">
          <span v-if="orderCounts.done" class="s-badge">{{ orderCounts.done }}</span>
          <span class="s-icon">♧</span><b>评价晒单</b><small>分享你的体验</small>
        </button>
      </div>
    </div>
    <div class="profile-panel">
      <h3>✦ 我的服务</h3>
      <div class="service-grid">
        <button data-action="goto-fav"><span class="s-icon">♡</span><b>我的收藏</b><small>{{ favCount }} 件商品</small></button>
        <button data-action="open-addr" @click="addressModal"><span class="s-icon">⌂</span><b>收货地址</b><small>{{ addrCount }} 个地址</small></button>
        <button data-action="open-coupon" @click="couponModal"><span class="s-icon">🎫</span><b>优惠券</b><small>{{ couponCount }} 张可用</small></button>
        <button data-action="goto-chat"><span class="s-icon">◌</span><b>联系卖家</b><small>从商品详情页发起咨询</small></button>
        <button data-action="goto-seller"><span class="s-icon">🏪</span><b>我的店铺</b><small>{{ user && user.shopId ? '管理我的店铺' : '一个账号，既能买也能卖' }}</small></button>
        <button data-action="goto-footprints"><span class="s-icon">👣</span><b>浏览足迹</b><small>最近看过的商品</small></button>
        <button data-action="goto-account"><span class="s-icon">⚙</span><b>账户设置</b><small>资料编辑 · 手机号 · 密码</small></button>
        <button data-action="goto-after-sales"><span class="s-icon">📋</span><b>售后服务</b><small>申请 · 退款 · 换货</small></button>
      </div>
    </div>
  </div>
</template>
