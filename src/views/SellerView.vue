<script setup>
/* =========================================================
   青集市 · views/SellerView.vue —— 我的店铺（工作台）
   店铺绑定在当前登录用户账号上（user.shopId，见 mock.shopOwners）：
   · 全站只有一个用户账号，开店不会新增账号，也没有独立的店家身份；
   · 顶部展示当前店铺与账号，可查看店铺主页 / 退出登录；
   · 经营数据、待办事项、快捷管理、最近订单都在这一页呈现
   ========================================================= */
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import QM_UI from '../core/ui.js';
import QM_STORE from '../core/store.js';

const { confirmDialog, toast, timeText } = QM_UI;
const router = useRouter();

/* 当前账号名下的店铺（未开店返回 null） */
const me = computed(() => QM_STORE.seller.current());
const shopId = computed(() => (me.value ? me.value.id : ''));
const user = computed(() => QM_STORE.state.user);

/* 商品与订单（store 非响应式，页面重挂载时取一次即可） */
const products = computed(() => (shopId.value ? QM_STORE.seller.products(shopId.value) : []));
const orderList = computed(() => (shopId.value ? QM_STORE.seller.orders(shopId.value) : []));

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
  .sort((a, b) => b.createTime - a.createTime)
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
        <span class="member-avatar big">{{ me.ownerName.slice(0, 1) }}</span>
        <div>
          <h1>{{ me.shopName }}</h1>
          <p>卖家账号 @{{ me.username }}</p>
        </div>
        <div class="seller-meta">
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
            <a class="seller-nav-card" href="#/seller/products"><span class="s-icon">◈</span><b>商品管理</b><small>上架 / 下架 / 改价</small></a>
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
            <span class="r-buyer">{{ o.address.name }}</span>
            <span class="r-time">{{ timeText(o.createTime) }}</span>
            <span class="o-status" :class="o.status">{{ STATUS_TEXT[o.status] || o.status }}</span>
            <b class="r-total">¥{{ money(o.total) }}</b>
          </li>
        </ul>
        <div v-else class="seller-blank">还没有订单，可以去店铺主页看看商品的展示效果</div>
      </section>
    </template>

    <!-- 已登录但未开店 -->
    <div v-else-if="user" class="empty-state">
      <div class="empty-icon">🏪</div>
      <h3>你还没有店铺</h3>
      <p>当前账号「{{ user.nickname }}」名下还没有店铺。使用已开店的演示账号登录（如 owner001 / 123456）即可体验我的店铺。</p>
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
