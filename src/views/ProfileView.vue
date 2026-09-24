<script setup>
/* =========================================================
   青集市 · views/ProfileView.vue —— 个人中心页
   移植自 mall-web/js/pages/profile.js（页面结构 / 交互逻辑不变）
   含：登录入口（data-action=open-login/logout 由 App.vue 全局代理处理）、
   地址管理弹窗（新增 / 编辑 / 删除 / 设默认，接口见 docs/地址簿接口文档.md）、
   优惠券展示弹窗（对应预留接口 /coupons）。
   ========================================================= */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import QM_UI from '../core/ui.js';
import QM_STORE from '../core/store.js';
import QM_API from '../core/api.js';
import { refreshView } from '../core/viewRefresh.js';
import openAddressModal from '../core/addressModal.js';

const { esc, toast, modal, confirmDialog } = QM_UI;

/* ---------- 页面数据：登录态与各项计数 ----------
   原实现是「视图创建时读一次的常量快照」：新增/删除地址、加入购物车后，
   页面上的"N 个地址 / N 个收藏"不会跟着变（store 事件也不会触发本页重渲染）。
   改为 computed + 订阅 store 事件：任一数据变化时计数自动重算。 */
const user = computed(() => QM_STORE.state.user);
const favCount = computed(() => (QM_STORE.state.favorites || []).length);
const cartCount = computed(() => (QM_STORE.state.cart || []).length);
const orderCount = computed(() => (QM_STORE.state.orders || []).length);
const couponCount = computed(() => QM_STORE.coupon.list().length);
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
/* 计数依赖的 store 事件（state 本身不是响应式的，靠这些事件驱动重算） */
const offs = [
  QM_STORE.on('cart', () => {}), QM_STORE.on('favorites', () => {}),
  QM_STORE.on('orders', () => { loadOrderCounts(); }), QM_STORE.on('addresses', () => {}),
  QM_STORE.on('user', () => { loadOrderCounts(); })
];
onMounted(loadOrderCounts);
onBeforeUnmount(() => { offs.forEach(off => { try { off(); } catch (e) { /* 忽略 */ } }); });

/* ---------- 头像：上传阿里云 OSS ----------
   user.avatar 存的是图片完整地址（旧数据可能是 emoji 字符，显示时兼容回退；
   底色调色盘已移除，不再有 avatarColor 字段）。 */
const AVATAR_MAX_SIZE = 5 * 1024 * 1024; // 头像图片大小上限 5MB
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

/* ---------- 编辑资料弹窗（昵称 / 头像 / 性别 / 个性签名） ---------- */
function profileModal() {
  const me = user.value; // computed 在 script 中不会自动解包，必须取 .value
  if (!me) return toast('请先登录', 'error');
  const m = modal(`
    <div>
      <h3>编辑资料</h3>
      <p class="modal-sub">修改昵称、头像、性别与个性签名</p>
      <div class="form-row"><label>昵称</label><input id="pfNickname" maxlength="20" /></div>
      <div class="form-row"><label>性别</label>
        <div class="gender-row">
          <label class="gender-opt"><input type="radio" name="pfGender" value="male" />男</label>
          <label class="gender-opt"><input type="radio" name="pfGender" value="female" />女</label>
          <label class="gender-opt"><input type="radio" name="pfGender" value="secret" />保密</label>
        </div>
      </div>
      <div class="form-row"><label>头像</label>
        <div class="avatar-upload">
          <span id="pfAvatarPreview" class="member-avatar big avatar-preview"></span>
          <div class="avatar-upload-actions">
            <button type="button" class="btn btn-plain" id="pfPickAvatar">选择图片</button>
            <small>支持 jpg / png / webp / gif，不超过 5MB；图片将上传至阿里云 OSS</small>
            <input type="file" id="pfAvatarFile" accept="image/*" class="hidden" />
          </div>
        </div>
      </div>
      <div class="form-row"><label>个性签名</label><input id="pfSignature" maxlength="40" placeholder="一句话介绍自己" /></div>
      <div class="modal-actions" style="margin-top:0">
        <button class="btn btn-plain" data-close>取消</button>
        <button class="btn btn-primary" id="pfSave">保存资料</button>
      </div>
    </div>`);

  let avatar = (me.avatar || '').trim();        // 当前头像（旧数据可能是 emoji）
  let pickedFile = null;                        // 本次新选的头像文件（点保存时才上传）
  let objectUrl = null;                         // 本地预览 URL（关闭 / 保存后释放）
  const preview = m.root.querySelector('#pfAvatarPreview');
  const fileInput = m.root.querySelector('#pfAvatarFile');
  const renderPreview = () => {
    preview.innerHTML = '';
    if (isAvatarImage(avatar)) preview.innerHTML = `<img src="${esc(avatar)}" alt="头像" />`;
    else preview.textContent = avatar || (me.nickname || '语').slice(0, 1);
  };
  /* 预填注册时已有的昵称 / 性别 / 签名 / 头像 */
  m.root.querySelector('#pfNickname').value = me.nickname || '';
  const gender = ['male', 'female', 'secret'].includes(me.gender) ? me.gender : 'secret';
  const g = m.root.querySelector('input[name="pfGender"][value="' + gender + '"]');
  if (g) g.checked = true;
  m.root.querySelector('#pfSignature').value = me.signature || '';
  renderPreview();
  /* 关闭弹窗（取消 / 保存）时释放本地预览 URL，避免内存泄漏 */
  m.root.addEventListener('click', e => {
    if (e.target.closest('[data-close]') && objectUrl) { URL.revokeObjectURL(objectUrl); objectUrl = null; }
  });
  m.root.querySelector('#pfPickAvatar').onclick = () => fileInput.click();
  fileInput.onchange = () => {
    const f = fileInput.files && fileInput.files[0];
    if (!f) return;
    if (!/^image\//.test(f.type)) return toast('请选择图片文件', 'error');
    if (f.size > AVATAR_MAX_SIZE) return toast('头像图片不能超过 5MB', 'error');
    if (objectUrl) URL.revokeObjectURL(objectUrl); // 换图时先释放上一张预览
    pickedFile = f;
    objectUrl = URL.createObjectURL(f);
    avatar = objectUrl;
    renderPreview();
  };
  m.root.querySelector('#pfSave').onclick = async () => {
    const nickname = m.root.querySelector('#pfNickname').value.trim();
    if (!nickname) return toast('昵称不能为空', 'error');
    const picked = m.root.querySelector('input[name="pfGender"]:checked');
    const signature = m.root.querySelector('#pfSignature').value.trim();
    const btn = m.root.querySelector('#pfSave');
    btn.disabled = true; btn.textContent = '保存中…';
    try {
      /* ① 选过新图 → 先上传到阿里云 OSS，拿到图片地址（multipart → POST /users/avatar） */
      if (pickedFile) {
        const data = await QM_API.user.uploadAvatar(pickedFile);
        const url = data && (data.url || data.avatar || data.fileUrl);
        if (!url) throw new Error('头像上传成功但未返回图片地址');
        avatar = url;
      }
      /* ② 资料（含头像地址）提交后端落库（PUT /users/profile），成功后同步本地登录态 */
      const payload = { nickname, gender: picked ? picked.value : 'secret', avatar, signature };
      await QM_API.user.updateProfile(payload);
      QM_STORE.user.update(payload);
      if (objectUrl) { URL.revokeObjectURL(objectUrl); objectUrl = null; }
      toast('资料已更新', 'success');
      m.close();
      refreshView(); // 重挂载本页刷新封面头像 / 昵称 / 性别 / 签名
    } catch (e) {
      /* 保存失败：保留弹窗与本地预览（不释放 objectUrl），便于用户重试 */
      toast(e.message || '保存失败', 'error');
    } finally {
      btn.disabled = false; btn.textContent = '保存资料';
    }
  };
}

/* ---------- 地址管理弹窗 ----------
   实现已抽到 core/addressModal.js（个人中心与购物车结算弹窗共用同一个弹窗）；
   保存 / 删除 / 设默认成功后由弹窗内部自行刷新列表。 */
async function addressModal() {
  await openAddressModal();
}

/* ---------- 优惠券弹窗（原 couponModal，对应预留接口 /coupons） ---------- */
function couponModal() {
  const m = modal(`
    <div>
      <h3>我的优惠券</h3>
      <p class="modal-sub">领取与使用状态（对应预留接口 /coupons）</p>
      <div id="couponList"></div>
      <div class="modal-actions"><button class="btn btn-plain" data-close>关闭</button></div>
    </div>`, { wide: true });
  const renderList = () => {
    const list = QM_STORE.coupon.list();
    m.root.querySelector('#couponList').innerHTML = list.map(c => `
      <div class="addr-option" style="display:flex;align-items:center;gap:12px">
        <div style="flex:none;width:86px;text-align:center;background:var(--brand-soft);border-radius:8px;padding:10px 0">
          <b style="color:var(--brand);font-size:20px">¥${c.amount}</b>
          <small style="display:block;color:var(--text-3)">满 ${c.threshold} 可用</small>
        </div>
        <div style="flex:1">
          <b>${esc(c.title)}</b>
          <small style="display:block;color:var(--text-3)">有效期至 ${esc(c.expire)}</small>
        </div>
        <span class="pill ${c.status === 'used' ? 'pill-gray' : 'pill-green'}">${c.status === 'used' ? '已使用' : '未使用'}</span>
      </div>`).join('');
  };
  renderList();
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
        <button v-if="user" class="btn" @click="profileModal">编辑资料</button>
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
        <button data-action="goto-orders" data-id="done">
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
        <button data-action="goto-placeholder" data-id="浏览足迹"><span class="s-icon">👣</span><b>浏览足迹</b><small>功能预留</small></button>
        <button data-action="goto-placeholder" data-id="账户设置"><span class="s-icon">⚙</span><b>账户设置</b><small>功能预留</small></button>
        <button data-action="goto-placeholder" data-id="售后服务"><span class="s-icon">📋</span><b>售后服务</b><small>功能预留</small></button>
      </div>
    </div>
  </div>
</template>
