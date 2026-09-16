<script setup>
/* =========================================================
   青集市 · views/ProfileView.vue —— 个人中心页
   移植自 mall-web/js/pages/profile.js（页面结构 / 交互逻辑不变）
   含：登录入口（data-action=open-login/logout 由 App.vue 全局代理处理）、
   地址管理弹窗（新增 / 编辑 / 删除 / 设默认，对应预留接口 /addresses）、
   优惠券展示弹窗（对应预留接口 /coupons）。
   ========================================================= */
import { computed, onBeforeUnmount } from 'vue';
import QM_UI from '../core/ui.js';
import QM_STORE from '../core/store.js';
import { refreshView } from '../core/viewRefresh.js';

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
/* 计数依赖的 store 事件（state 本身不是响应式的，靠这些事件驱动重算） */
const offs = [
  QM_STORE.on('cart', () => {}), QM_STORE.on('favorites', () => {}),
  QM_STORE.on('orders', () => {}), QM_STORE.on('addresses', () => {}),
  QM_STORE.on('user', () => {})
];
onBeforeUnmount(() => { offs.forEach(off => { try { off(); } catch (e) { /* 忽略 */ } }); });

/* ---------- 头像选择预设（emoji + 底色，存于 user.avatar / user.avatarColor） ---------- */
const AVATARS = ['😀', '🦊', '🐱', '🐰', '🐻', '🐼', '🦁', '🐯', '🦄', '🐧', '🌸', '🍀', '🌟', '🔥', '🎧', '🍉'];
const AVATAR_COLORS = ['#ff6a2b', '#6b6bdf', '#d971a4', '#38ad90', '#e08b5e', '#4b6cb7', '#ff416c', '#5f2c82'];

/* 资料摘要文案（性别 / 签名），显示在封面副标题 */
function userProfileText() {
  if (!user) return '';
  const parts = [];
  if (user.gender === 'male') parts.push('♂ 男');
  else if (user.gender === 'female') parts.push('♀ 女');
  else parts.push('保密');
  if (user.signature) parts.push('「' + user.signature + '」');
  return parts.join(' · ');
}

/* ---------- 编辑资料弹窗（昵称 / 头像 / 性别 / 个性签名） ---------- */
function profileModal() {
  if (!user) return toast('请先登录', 'error');
  const m = modal(`
    <div style="position:relative">
      <button class="modal-close" data-close>×</button>
      <h3>编辑资料</h3>
      <p class="modal-sub">修改昵称、头像、性别与个性签名；收货地址请在「我的服务 → 收货地址」中管理</p>
      <div class="form-row"><label>昵称</label><input id="pfNickname" maxlength="20" placeholder="怎么称呼你" /></div>
      <div class="form-row"><label>性别</label>
        <div class="gender-row">
          <label class="gender-opt"><input type="radio" name="pfGender" value="male" />男</label>
          <label class="gender-opt"><input type="radio" name="pfGender" value="female" />女</label>
          <label class="gender-opt"><input type="radio" name="pfGender" value="secret" />保密</label>
        </div>
      </div>
      <div class="form-row"><label>头像</label>
        <div id="pfAvatarList" class="avatar-pick">${AVATARS.map(a => `<button type="button" class="avatar-opt" data-avatar="${esc(a)}">${a}</button>`).join('')}</div>
      </div>
      <div class="form-row"><label>头像底色</label>
        <div id="pfColorList" class="color-pick">${AVATAR_COLORS.map(c => `<button type="button" class="color-opt" data-color="${c}" style="background:${c}"></button>`).join('')}</div>
      </div>
      <div class="form-row"><label>个性签名</label><input id="pfSignature" maxlength="40" placeholder="一句话介绍自己" /></div>
      <div class="modal-actions" style="margin-top:0">
        <button class="btn btn-plain" data-close>取消</button>
        <button class="btn btn-primary" id="pfSave">保存资料</button>
      </div>
    </div>`);

  let avatar = (user.avatar || '').trim();
  let color = user.avatarColor || '#ff6a2b';
  const avatarList = m.root.querySelector('#pfAvatarList');
  const colorList = m.root.querySelector('#pfColorList');
  const refreshMark = () => {
    avatarList.querySelectorAll('.avatar-opt').forEach(b => b.classList.toggle('active', b.dataset.avatar === avatar));
    colorList.querySelectorAll('.color-opt').forEach(b => b.classList.toggle('active', b.dataset.color === color));
  };
  m.root.querySelector('#pfNickname').value = user.nickname || '';
  const gender = ['male', 'female', 'secret'].includes(user.gender) ? user.gender : 'secret';
  const g = m.root.querySelector('input[name="pfGender"][value="' + gender + '"]');
  if (g) g.checked = true;
  m.root.querySelector('#pfSignature').value = user.signature || '';
  refreshMark();
  avatarList.onclick = e => { const b = e.target.closest('[data-avatar]'); if (b) { avatar = b.dataset.avatar; refreshMark(); } };
  colorList.onclick = e => { const b = e.target.closest('[data-color]'); if (b) { color = b.dataset.color; refreshMark(); } };
  m.root.querySelector('#pfSave').onclick = () => {
    const nickname = m.root.querySelector('#pfNickname').value.trim();
    if (!nickname) return toast('昵称不能为空', 'error');
    const picked = m.root.querySelector('input[name="pfGender"]:checked');
    QM_STORE.user.update({
      nickname,
      gender: picked ? picked.value : 'secret',
      avatar,
      avatarColor: color,
      signature: m.root.querySelector('#pfSignature').value.trim()
    });
    toast('资料已更新', 'success');
    m.close();
    refreshView(); // 重挂载本页刷新封面头像 / 昵称 / 性别 / 签名
  };
}

/* ---------- 地址管理弹窗（原 addressModal，对应预留接口 /addresses） ---------- */
function addressModal() {
  const m = modal(`
    <div style="position:relative">
      <button class="modal-close" data-close>×</button>
      <h3>收货地址</h3>
      <p class="modal-sub">新增、编辑、删除与设置默认地址（对应预留接口 /addresses）</p>
      <div id="addrList"></div>
      <div id="addrForm" class="hidden" style="border-top:1px dashed var(--border);padding-top:14px;margin-top:10px">
        <div class="form-row"><label>收货人</label><input id="afName" maxlength="20" placeholder="姓名" /></div>
        <div class="form-row"><label>手机号</label><input id="afPhone" maxlength="11" placeholder="11 位手机号" /></div>
        <div class="form-row"><label>所在地区</label><input id="afRegion" placeholder="省 / 市 / 区" /></div>
        <div class="form-row"><label>详细地址</label><input id="afDetail" maxlength="60" placeholder="街道、楼牌号等" /></div>
        <div class="form-row"><label><input type="checkbox" id="afDefault" style="width:auto;height:auto;margin-right:6px" />设为默认地址</label></div>
        <div class="modal-actions" style="margin-top:0">
          <button class="btn btn-plain" data-close-form>取消</button>
          <button class="btn btn-primary" id="afSave">保存地址</button>
        </div>
      </div>
      <div class="modal-actions">
        <button class="btn btn-primary" id="addAddr">＋ 新增地址</button>
      </div>
    </div>`, { wide: true });

  let editingId = null;
  const renderList = () => {
    const list = QM_STORE.addr.list();
    m.root.querySelector('#addrList').innerHTML = list.length ? list.map(a => `
      <div class="addr-option">
        <b>${esc(a.name)} ${esc(a.phone)}${a.isDefault ? ' <span class="pill pill-orange">默认</span>' : ''}</b>
        <small>${esc(a.region)} ${esc(a.detail)}</small>
        <span class="addr-actions">
          <button type="button" class="link-btn" data-action="edit-addr" data-id="${esc(a.id)}">编辑</button>
          ${a.isDefault ? '' : `<button type="button" class="link-btn" data-action="default-addr" data-id="${esc(a.id)}">设默认</button>`}
          <button type="button" class="link-btn danger" data-action="del-addr" data-id="${esc(a.id)}">删除</button>
        </span>
      </div>`).join('') : '<p class="modal-sub">暂无收货地址</p>';
  };
  const openForm = (addr) => {
    editingId = addr ? addr.id : null;
    m.root.querySelector('#afName').value = addr ? addr.name : '';
    m.root.querySelector('#afPhone').value = addr ? addr.phone : '';
    m.root.querySelector('#afRegion').value = addr ? addr.region : '';
    m.root.querySelector('#afDetail').value = addr ? addr.detail : '';
    m.root.querySelector('#afDefault').checked = addr ? addr.isDefault : false;
    m.root.querySelector('#addrForm').classList.remove('hidden');
  };
  renderList();
  m.root.querySelector('#addAddr').onclick = () => openForm(null);
  m.root.querySelector('[data-close-form]').onclick = () => m.root.querySelector('#addrForm').classList.add('hidden');
  m.root.querySelector('#afSave').onclick = () => {
    const data = {
      name: m.root.querySelector('#afName').value.trim(),
      phone: m.root.querySelector('#afPhone').value.trim(),
      region: m.root.querySelector('#afRegion').value.trim(),
      detail: m.root.querySelector('#afDetail').value.trim()
    };
    if (!data.name || !data.phone || !data.region || !data.detail) return toast('请完整填写地址信息', 'error');
    if (!/^1\d{10}$/.test(data.phone)) return toast('请输入正确的 11 位手机号', 'error');
    if (editingId) QM_STORE.addr.update(editingId, data);
    else QM_STORE.addr.add(data);
    if (m.root.querySelector('#afDefault').checked) {
      const list = QM_STORE.addr.list();
      const last = list[list.length - 1];
      QM_STORE.addr.setDefault(editingId || last.id);
    }
    toast('地址已保存', 'success');
    m.root.querySelector('#addrForm').classList.add('hidden');
    renderList();
  };
  m.root.querySelector('#addrList').onclick = async e => {
    const a = e.target.closest('[data-action]');
    if (!a) return;
    const addr = QM_STORE.addr.list().find(x => x.id === a.dataset.id);
    if (a.dataset.action === 'edit-addr') openForm(addr);
    else if (a.dataset.action === 'default-addr') { QM_STORE.addr.setDefault(a.dataset.id); toast('已设为默认地址', 'success'); renderList(); }
    else if (a.dataset.action === 'del-addr') {
      if (await confirmDialog('删除地址', '确定删除该收货地址吗？', '删除', true)) { QM_STORE.addr.remove(a.dataset.id); toast('已删除'); renderList(); }
    }
  };
}

/* ---------- 优惠券弹窗（原 couponModal，对应预留接口 /coupons） ---------- */
function couponModal() {
  const m = modal(`
    <div style="position:relative">
      <button class="modal-close" data-close>×</button>
      <h3>我的优惠券</h3>
      <p class="modal-sub">领取与使用状态（对应预留接口 /coupons）</p>
      <div id="couponList"></div>
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
      <span class="member-avatar big" :style="{ background: user ? (user.avatarColor || '#ff6a2b') : '#ff6a2b' }">{{ user ? ((user.avatar && user.avatar.trim()) ? user.avatar : user.nickname.slice(0, 1)) : '语' }}</span>
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
      <h3>⌁ 我的订单</h3>
      <div class="service-grid">
        <button data-action="goto-orders" data-id="pending"><span class="s-icon">◴</span><b>待付款</b><small>及时付款不错过好价</small></button>
        <button data-action="goto-orders" data-id="paid"><span class="s-icon">▣</span><b>待发货</b><small>卖家正在准备</small></button>
        <button data-action="goto-orders" data-id="shipped"><span class="s-icon">▤</span><b>待收货</b><small>物流实时可查</small></button>
        <button data-action="goto-orders" data-id="done"><span class="s-icon">♧</span><b>评价晒单</b><small>分享你的体验</small></button>
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
        <button data-action="goto-placeholder" data-id="退款售后"><span class="s-icon">↩</span><b>退款售后</b><small>功能预留</small></button>
        <button data-action="goto-placeholder" data-id="会员中心"><span class="s-icon">👑</span><b>会员中心</b><small>功能预留</small></button>
      </div>
    </div>
  </div>
</template>
