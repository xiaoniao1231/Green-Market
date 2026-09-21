<script setup>
/* =========================================================
   青集市 · views/SellerView.vue —— 我的店铺（工作台）
   店铺绑定在当前登录用户账号上（user.shopId）：
   · 全站只有一个用户账号，开店不会新增账号，也没有独立的店家身份；
   · 商品 / 订单数据走后端接口（strict，契约见 docs/店家中心商品管理接口文档.md），
     接口未实现时提示失败 + 空态，不回退演示数据；
   · 店铺档案（店名 / 头像 / 简介 / 评分 / 粉丝 / 开店时间）优先取后端
     GET /shops/profile，未实现时用本地档案 / 演示数据兜底，保证页面不白屏；
   · 未开店的账号可在本页直接开店（开店弹窗 → POST /shops）。
   ========================================================= */
import { computed, onActivated, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import QM_UI from '../core/ui.js';
import QM_API from '../core/api.js';
import QM_STORE from '../core/store.js';

const { confirmDialog, toast, timeText, esc, sales, modal, avatarHtml, isImage, openShopCreate } = QM_UI;
const router = useRouter();

/* 当前账号名下的店铺（未开店返回 null）。
   store 不是响应式对象：用 shopTick 版本号驱动重算（存入后端档案 / 开店成功时自增） */
const shopTick = ref(0);
const me = computed(() => { shopTick.value; return QM_STORE.seller.current(); });
const shopId = computed(() => (me.value ? me.value.id : ''));
const user = computed(() => QM_STORE.state.user);

/* ---------- 商品（strict：GET /seller/products） ---------- */
const products = ref([]);
async function loadProducts() {
  if (!shopId.value) { products.value = []; return; }
  try {
    const data = await QM_API.seller.products({ page: 1, size: 100 });
    products.value = (data && data.list) || [];
  } catch (e) {
    products.value = [];
    toast('商品数据加载失败：' + e.message, 'error');
  }
}

/* ---------- 订单（strict：GET /seller/orders；时间字段已由 api.js 归一为毫秒时间戳） ---------- */
const orderList = ref([]);
async function loadOrders() {
  if (!shopId.value) { orderList.value = []; return; }
  try {
    const data = await QM_API.seller.orders({ page: 1, size: 100 });
    orderList.value = (data && data.list) || [];
  } catch (e) {
    orderList.value = [];
    toast('订单数据加载失败：' + e.message, 'error');
  }
}

/* ---------- 店铺档案：接口优先（GET /shops/profile），接口未实现时沿用本地档案 ---------- */
async function loadShopProfile() {
  if (!shopId.value) return;
  try {
    const shop = await QM_API.seller.shopProfile();
    if (shop) QM_STORE.rememberShop(Object.assign({}, shop, { id: shop.id || shop.shopId || shopId.value }));
  } catch (e) {
    /* 后端店铺档案接口尚未实现：沿用本地档案 / 演示数据即可，不弹错打扰用户 */
  }
}

function loadAll() { loadShopProfile(); loadProducts(); loadOrders(); }
onMounted(loadAll);
onActivated(loadAll);

/* 店铺档案变化（保存资料 / 拉取后端档案 / 开店）后刷新工作台 */
let offShopProfile = null;
onMounted(() => { offShopProfile = QM_STORE.on('shopProfile', () => { shopTick.value++; }); });
onBeforeUnmount(() => { if (offShopProfile) { offShopProfile(); offShopProfile = null; } });

/* 开店：填写店铺基本信息 → POST /shops，成功后自动进入工作台 */
function openShop() {
  if (!user.value) { router.push({ path: '/login', query: { redirect: '/seller' } }); return; }
  openShopCreate({ onDone: () => { shopTick.value++; loadAll(); } });
}

/* 经营数据 */
const onSale = computed(() => products.value.filter(p => p.onSale !== false).length);
const offSale = computed(() => products.value.filter(p => p.onSale === false).length);
const orderTotal = computed(() => orderList.value.length);
const pendingShip = computed(() => orderList.value.filter(o => o.status === 'paid').length);
const pendingPay = computed(() => orderList.value.filter(o => o.status === 'pending').length);
/* 累计收入：排除已取消与未付款的订单 */
const revenue = computed(() => orderList.value
  .filter(o => o.status !== 'canceled' && o.status !== 'pending')
  .reduce((sum, o) => sum + Number(o.total || 0), 0));

const recentOrders = computed(() => orderList.value
  .slice()
  .sort((a, b) => (b.createTime || 0) - (a.createTime || 0))
  .slice(0, 5));

const STATUS_TEXT = { pending: '待付款', paid: '待发货', shipped: '待收货', done: '已完成', canceled: '已取消' };

/* 待办事项：只在确有需要处理的事情时才出现 */
const todos = computed(() => {
  const list = [];
  if (pendingShip.value) list.push({ icon: '▣', text: `${pendingShip.value} 笔订单等待发货`, hint: '及时发货能提升买家体验', href: '#/seller/orders', action: '去发货' });
  if (offSale.value) list.push({ icon: '◈', text: `${offSale.value} 件商品已下架`, hint: '重新上架后买家才能看到', href: '#/seller/products', action: '去上架' });
  if (pendingPay.value) list.push({ icon: '◴', text: `${pendingPay.value} 笔订单买家还未付款`, hint: '付款后即可安排发货', href: '#/seller/orders', action: '查看' });
  return list;
});

const shopHref = computed(() => '#/shop/' + encodeURIComponent(me.value ? me.value.shopName : ''));

/* 金额格式化：整数不带小数，其余保留两位 */
function money(n) {
  const v = Number(n || 0);
  return Number.isInteger(v) ? String(v) : v.toFixed(2);
}

async function logout() {
  if (!await confirmDialog('退出登录', '确定退出当前账号吗？（店铺仍绑定在这个账号上）', '退出', true)) return;
  QM_STORE.user.logout();
  toast('已退出登录');
  router.replace('/home');
}

/* =========================================================
   店铺信息管理：店铺头像（上传 OSS）+ 店铺简介
   保存走 PUT /shops/profile（后端见 docs/后端商品管理代码与教程.md Step 2(c)），
   成功后写入本地覆盖（store.seller.saveProfile），工作台 / 店铺页 / 详情页即时生效
   ========================================================= */
let editModal = null;      // 当前编辑弹窗句柄
const editAvatar = ref('');    // 弹窗内当前头像（OSS 地址或 emoji）
const editName = ref('');      // 弹窗内当前店铺名
const editIntro = ref('');     // 弹窗内当前简介
let pendingAvatar = '';        // 本次新上传的头像地址（未保存前生效）

function openShopEdit() {
  const m = me.value;
  if (!m) return;
  editAvatar.value = m.avatar || '';
  editName.value = m.shopName || '';
  editIntro.value = m.shopIntro || '';
  pendingAvatar = '';
  editModal = modal(`
    <div class="seller-edit">
      <div class="se-head">
        <span class="se-avatar">${avatarHtml(editAvatar.value, 'lg', m.color)}</span>
        <div class="se-head-info">
          <b>${esc(m.shopName)}</b>
          <p>店铺评分 ${esc(m.score !== undefined ? m.score : '—')} · 粉丝 ${sales(m.fans || 0)} · 开店 ${esc(m.founded || '—')}</p>
        </div>
      </div>
      <div class="se-avatar-actions">
        <button class="btn btn-plain btn-sm" id="sePick">更换头像</button>
        <span class="se-tip">jpg / png，≤10MB，上传后保存到 OSS</span>
      </div>
      <label class="se-label" for="seName">店铺名称</label>
      <input id="seName" class="se-name" maxlength="20" placeholder="2-20 个字，修改后全站同步显示" value="${esc(editName.value)}" />
      <label class="se-label" for="seIntro">店铺简介</label>
      <textarea id="seIntro" class="se-intro" rows="4" maxlength="120" placeholder="介绍一下你的店铺（120 字以内）">${esc(editIntro.value)}</textarea>
      <p class="se-count"><span id="seCount">${editIntro.value.length}</span>/120</p>
      <div class="modal-actions">
        <button class="btn btn-plain" data-close>取消</button>
        <button class="btn btn-primary" id="seSave">保存</button>
      </div>
    </div>`, { wide: true });

  /* 更换头像：隐藏 file input 触发选择 */
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = 'image/*';
  input.hidden = true;
  input.onchange = () => pickAvatar(input);
  editModal.root.querySelector('#sePick').onclick = () => input.click();
  /* 简介字数统计 */
  const introEl = editModal.root.querySelector('#seIntro');
  const countEl = editModal.root.querySelector('#seCount');
  introEl.oninput = () => { countEl.textContent = introEl.value.length; };
  editModal.root.querySelector('#seSave').onclick = () => {
    const nameEl = editModal.root.querySelector('#seName');
    saveShopEdit(introEl, nameEl);
  };
}

/* 头像上传 → OSS（POST /shops/avatar），成功后即时预览，保存时才落库 */
async function pickAvatar(input) {
  const file = input.files && input.files[0];
  input.value = '';
  if (!file) return;
  if (file.size > 10 * 1024 * 1024) { toast('图片不能超过 10MB', 'error'); return; }
  if (!/^image\//.test(file.type)) { toast('请选择 jpg / png 等图片文件', 'error'); return; }
  try {
    toast('正在上传头像…');
    const data = await QM_API.seller.uploadShopAvatar(file);
    const url = data && data.url;
    if (!url) throw new Error('上传未返回图片地址');
    pendingAvatar = url;
    const box = editModal.root.querySelector('.se-avatar');
    box.innerHTML = avatarHtml(url, 'lg', me.value.color);
    toast('头像已上传，点击「保存」后生效', 'success');
  } catch (e) {
    toast('头像上传失败：' + e.message, 'error');
  }
}

/* 保存店铺资料 → PUT /shops/profile（body { name, avatar, intro }，intro=店铺简介）。
   成功后把后端返回的店铺档案写入本地档案（rememberShop），工作台 / 店铺主页 / 商品卡片即时生效；
   接口失败（未实现 / 校验不通过）只提示错误，不写本地 —— 与商品管理的 strict 策略一致 */
async function saveShopEdit(introEl, nameEl) {
  const name = (nameEl ? nameEl.value : editName.value).trim();
  const intro = introEl.value.trim();
  if (!name) { toast('店铺名称不能为空', 'error'); return; }
  if (name.length > 20) { toast('店铺名称不能超过 20 个字', 'error'); return; }
  if (nameConflict(name)) { toast('该店铺名称已被占用，请换一个', 'error'); return; }
  if (intro.length > 120) { toast('店铺简介不能超过 120 字', 'error'); return; }
  const avatar = pendingAvatar || editAvatar.value;
  try {
    const shop = await QM_API.seller.updateShopProfile({ name, avatar, intro });
    QM_STORE.rememberShop(Object.assign({}, shop || {}, {
      id: me.value.id, name, avatar, shopIntro: intro
    }));
    toast('店铺信息已保存', 'success');
    if (editModal) { editModal.close(); editModal = null; }
  } catch (e) {
    toast('保存失败：' + e.message, 'error');
  }
}

/* 店铺名重名校验：本地只做「已拉取到的店铺档案」的预检，最终以后端为准
   （后端 /shops/profile 应自行查重并返回 code=0 + msg="店铺名称已被占用"） */
function nameConflict(name) {
  if (!name || name === me.value.shopName) return false;
  for (const [id, ov] of Object.entries(QM_STORE.state.shopProfile || {})) {
    if (ov && ov.name === name && id !== me.value.id) return true;
  }
  return false;
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 我的店铺</div>
        <h1>我的店铺 <small>MY SHOP</small></h1>
      </div>
      <a class="btn btn-plain" href="#/home">返回商城</a>
    </div>

    <!-- 已开店：店铺工作台 -->
    <template v-if="me">
      <div class="seller-cover" :style="{ background: 'linear-gradient(120deg, ' + me.color + ', #ff5000)' }">
        <span class="member-avatar big seller-avatar">
          <img v-if="isImage(me.avatar)" class="seller-avatar-img" :src="me.avatar" alt="" />
          <template v-else>{{ me.avatar || me.ownerName.slice(0, 1) }}</template>
        </span>
        <div>
          <h1>{{ me.shopName }}</h1>
          <p>卖家账号 @{{ me.username }}</p>
        </div>
        <div class="seller-meta">
          <button class="btn seller-edit-btn" @click="openShopEdit">店铺信息管理</button>
          <a class="btn seller-shop-link" :href="shopHref">查看店铺主页</a>
          <button class="btn seller-logout" @click="logout">退出登录</button>
        </div>
      </div>

      <div class="seller-tip">💡 店铺绑定在当前用户账号 <b>@{{ me.username }}</b> 下：你既能以买家身份下单，也能在这里打理店铺，全程只有这一个账号。</div>

      <!-- 经营数据 -->
      <div class="seller-stats">
        <div class="stat-card"><b>{{ onSale }}</b><span>在售商品</span></div>
        <div class="stat-card"><b>{{ pendingShip }}</b><span>待发货订单</span></div>
        <div class="stat-card"><b>{{ orderTotal }}</b><span>全部订单</span></div>
        <div class="stat-card"><b>¥{{ money(revenue) }}</b><span>累计收入</span></div>
      </div>

      <!-- 待办事项 + 快捷管理 -->
      <div class="seller-cols">
        <section class="seller-panel">
          <header class="seller-panel-head">
            <h3>待办事项</h3>
            <small>{{ todos.length ? todos.length + ' 项待处理' : '暂无待处理' }}</small>
          </header>
          <ul v-if="todos.length" class="todo-list">
            <li v-for="t in todos" :key="t.text">
              <span class="todo-icon">{{ t.icon }}</span>
              <span class="todo-text"><b>{{ t.text }}</b><small>{{ t.hint }}</small></span>
              <a class="todo-btn" :href="t.href">{{ t.action }} ›</a>
            </li>
          </ul>
          <div v-else class="seller-blank">🎉 没有待处理的订单或商品，一切都很顺利</div>
        </section>

        <section class="seller-panel">
          <header class="seller-panel-head">
            <h3>快捷管理</h3>
            <small>常用操作</small>
          </header>
          <div class="seller-nav-grid">
            <a class="seller-nav-card" href="#/seller/products"><span class="s-icon">◈</span><b>商品管理</b><small>上架 · 编辑 · 新增</small></a>
            <a class="seller-nav-card" href="#/seller/orders"><span class="s-icon">▣</span><b>订单管理</b><small>查看订单 · 发货</small></a>
            <a class="seller-nav-card" :href="shopHref"><span class="s-icon">🏪</span><b>店铺主页</b><small>买家看到的样子</small></a>
            <a class="seller-nav-card" href="#/chat"><span class="s-icon">◌</span><b>买家咨询</b><small>在消息中心回复</small></a>
          </div>
        </section>
      </div>

      <!-- 最近订单 -->
      <section class="seller-panel">
        <header class="seller-panel-head">
          <h3>最近订单</h3>
          <a class="panel-more" href="#/seller/orders">全部订单 ›</a>
        </header>
        <ul v-if="recentOrders.length" class="recent-list">
          <li v-for="o in recentOrders" :key="o.id">
            <span class="r-no">{{ o.orderNo }}</span>
            <span class="r-buyer">{{ (o.address && o.address.name) || '—' }}</span>
            <span class="r-time">{{ timeText(o.createTime) }}</span>
            <span class="o-status" :class="o.status">{{ STATUS_TEXT[o.status] || o.status }}</span>
            <b class="r-total">¥{{ money(o.total) }}</b>
          </li>
        </ul>
        <div v-else class="seller-blank">还没有订单，买家下单后会自动出现在这里</div>
      </section>
    </template>

    <!-- 已登录但未开店：直接开店（填写店铺基本信息） -->
    <div v-else-if="user" class="empty-state">
      <div class="empty-icon">🏪</div>
      <h3>你还没有店铺</h3>
      <p>当前账号「{{ user.nickname }}」名下还没有店铺。填写店铺名称与简介即可开通，开店后即可上架商品、管理订单。</p>
      <button class="btn btn-primary" @click="openShop">立即开店</button>
      <a class="btn btn-plain" href="#/home">先逛逛</a>
    </div>

    <!-- 未登录 -->
    <div v-else class="empty-state">
      <div class="empty-icon">🔐</div>
      <h3>请先登录</h3>
      <p>全站只有一套用户账号，登录后即可管理自己名下的店铺。</p>
      <a class="btn btn-primary" href="#/login?redirect=%2Fseller">去登录</a>
    </div>
  </div>
</template>
