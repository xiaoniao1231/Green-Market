<script setup>
/* =========================================================
   青集市 · views/SellerOrdersView.vue —— 我的店铺 · 订单管理
   订单数据全部来自后端接口（strict，契约见 docs/店家中心商品管理接口文档.md 2.12 / 2.13）：
   · GET  /seller/orders               本店铺订单列表（含买家、明细、金额、状态）
   · PUT  /seller/orders/{id}/ship     发货（物流轨迹由后端生成）
   后端未实现（404 / 网络不可达）时提示失败 + 空态，不回退本地演示订单。
   ========================================================= */
import { computed, onActivated, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import QM_UI from '../core/ui.js';
import QM_API from '../core/api.js';
import QM_STORE from '../core/store.js';
import useRouteCompat from '../composables/useRouteCompat.js';

const { toast, fullTime, price, artStyle, artHtml, confirmDialog, openShopCreate } = QM_UI;
const router = useRouter();
const route = useRouteCompat();

/* store 不是响应式对象：用 shopTick 版本号驱动「当前店铺」重算（开店 / 档案更新后自增） */
const shopTick = ref(0);
const svc = computed(() => { shopTick.value; return QM_STORE.seller.current(); });
const shopId = computed(() => (svc.value ? svc.value.id : ''));
const list = ref([]);
const loading = ref(false);

const STATUS_TEXT = { pending: '待付款', paid: '待发货', shipped: '待收货', done: '已完成', canceled: '已取消' };

/* 状态筛选：进页面时读地址里的 ?status=（工作台「待发货订单」卡就带它跳进来），
   之后可点顶部页签切换；筛选只作用于已拉取的列表，不再向后端发请求 */
const FILTERS = [
  { key: '', label: '全部' },
  { key: 'pending', label: '待付款' },
  { key: 'paid', label: '待发货' },
  { key: 'shipped', label: '待收货' },
  { key: 'done', label: '已完成' },
  { key: 'canceled', label: '已取消' }
];
const filter = ref(typeof route.value.query.status === 'string' ? route.value.query.status : '');
/* 「被催发货」= 买家催过 且 订单仍在待发货（发货后状态转 shipped，角标自然消失）。
   后端在订单列表里带出 remindCount / lastRemindTime（LEFT JOIN seller_reminders） */
const isUrged = o => o.status === 'paid' && Number(o.remindCount || 0) > 0;
const urgedCount = computed(() => list.value.filter(isUrged).length);
/* 被催的订单置顶：店家一进页面就知道该先处理谁；同组内按最近催发货时间倒序（催得越急越靠前） */
const filtered = computed(() => {
  const base = filter.value ? list.value.filter(o => o.status === filter.value) : list.value.slice();
  return base.sort((a, b) => {
    const ua = isUrged(a) ? 1 : 0;
    const ub = isUrged(b) ? 1 : 0;
    if (ua !== ub) return ub - ua;
    if (ua && ub) return Number(b.lastRemindTime || 0) - Number(a.lastRemindTime || 0);
    return 0;
  });
});
const statusCounts = computed(() => {
  const c = {};
  list.value.forEach(o => { c[o.status] = (c[o.status] || 0) + 1; });
  return c;
});
const countOf = key => (key ? (statusCounts.value[key] || 0) : list.value.length);
function setFilter(key) { filter.value = key; }

async function refresh() {
  if (!shopId.value) { list.value = []; return; }
  loading.value = true;
  try {
    const data = await QM_API.seller.orders({ page: 1, size: 100 });
    list.value = (data && data.list) || [];
  } catch (e) {
    list.value = [];
    toast('订单加载失败：' + e.message, 'error');
  } finally {
    loading.value = false;
  }
}

/* 发货：PUT /seller/orders/{id}/ship（幂等由后端保证），成功后重新拉取列表 */
async function ship(o) {
  if (!await confirmDialog('确认发货', '确认对订单 ' + (o.orderNo || o.id) + ' 发货吗？', '发货', false)) return;
  try {
    await QM_API.seller.shipOrder(o.id);
    toast('已发货', 'success');
    refresh();
  } catch (e) {
    toast('发货失败：' + e.message, 'error');
  }
}

/* ---------- 订单卡展示辅助 ---------- */
/* 金额格式化：接口给的是 BigDecimal 转来的数字，统一保留两位小数 */
const money = n => Number(n || 0);
const fmt = n => money(n).toFixed(2);
/* 本店条目总件数（跨店混单时 items 只含本店商品，件数也只统计本店） */
const qtyOf = o => (o.items || []).reduce((s, i) => s + (Number(i.qty) || 0), 0);
/* 收货信息三要素：姓名 / 电话 / 完整地址（后端以 JSON 快照返回，缺字段时兜底显示 —） */
function buyer(o) {
  const a = o.address || {};
  return {
    name: a.name || '—',
    phone: a.phone || '—',
    addr: [a.region, a.detail].filter(Boolean).join(' ') || '—'
  };
}
/* 最新一条物流轨迹（已发货订单在卡片里直接露出，不用另开弹窗） */
function latestLogi(o) {
  const l = o.logistics;
  return (l && l.length) ? l[0] : null;
}

/* 未开店账号可直接在本页开店 */
function openShop() {
  if (!QM_STORE.state.user) { router.push({ path: '/login', query: { redirect: '/seller/orders' } }); return; }
  openShopCreate({ onDone: () => { shopTick.value++; refresh(); } });
}

let offShopProfile = null;
let offFrame = null;
onMounted(() => {
  offShopProfile = QM_STORE.on('shopProfile', () => { shopTick.value++; });
  /* 实时通道：App.vue 的全局订阅收到 SELLER_REMIND 后广播 sellerRemind，本页据此重拉列表
     （提示 toast 由全局通道统一弹出，这里不重复弹）。
     不在线时收不到这条事件也没关系 —— 提醒已落库，刷新页面照样能看到角标 */
  offFrame = QM_STORE.on('sellerRemind', () => { refresh(); });
  refresh();
});
onActivated(refresh);
onBeforeUnmount(() => {
  if (offShopProfile) { offShopProfile(); offShopProfile = null; }
  if (offFrame) { offFrame(); offFrame = null; }
});
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 我的店铺 / 订单管理</div>
        <h1>订单管理 <small>ORDERS</small></h1>
      </div>
      <a class="btn btn-plain" href="#/seller">返回我的店铺</a>
    </div>

    <template v-if="svc">
      <div class="seller-tip">💡 当前店铺：<b>{{ svc.shopName }}</b>，共 {{ list.length }} 笔订单。待发货订单可点「立即发货」，物流轨迹由后端生成；订单已按下单店铺拆分，每笔订单只含本店商品。</div>

      <!-- 催发货汇总：有被催订单时才出现（列表已把被催的置顶） -->
      <div v-if="urgedCount" class="seller-tip urge">
        🔔 有 <b>{{ urgedCount }}</b> 笔订单买家已提醒发货，已置顶显示 —— 建议优先处理。
      </div>

      <!-- 状态筛选：从工作台「待发货订单」卡进来时会自动选中对应状态 -->
      <div class="tabs seller-order-tabs">
        <button v-for="f in FILTERS" :key="f.key" :class="{ active: filter === f.key }" @click="setFilter(f.key)">
          {{ f.label }}（{{ countOf(f.key) }}）
        </button>
      </div>

      <div v-if="loading" class="empty-state">
        <div class="empty-icon">⏳</div>
        <h3>正在加载订单…</h3>
      </div>

      <template v-else>
        <div v-for="o in filtered" :key="o.id" class="seller-order-row" :class="'st-' + (o.status || 'pending')">
          <div class="seller-order-head">
            <span class="so-no">{{ o.orderNo || o.id }}</span>
            <!-- 催发货角标：仅「待发货 + 被催过」的订单显示；鼠标悬停给出次数与最近时间 -->
            <span v-if="isUrged(o)" class="so-urge"
                  :title="'买家已提醒 ' + o.remindCount + ' 次' + (o.lastRemindTime ? '，最近一次 ' + fullTime(o.lastRemindTime).slice(0, 16) : '')">
              🔔 催发货 ×{{ o.remindCount }}
            </span>
            <span class="o-time">{{ o.createTime ? fullTime(o.createTime).slice(0, 16) : '—' }}</span>
            <span class="so-qty">本店 {{ qtyOf(o) }} 件</span>
            <span class="o-status" :class="o.status">{{ STATUS_TEXT[o.status] || o.status }}</span>
          </div>

          <div class="seller-order-body">
            <!-- 收货信息：姓名 / 电话 / 完整地址 / 支付方式，买家备注有才显示 -->
            <div class="so-buyer">
              <div class="so-field"><span>收货人</span><b>{{ buyer(o).name }} · {{ buyer(o).phone }}</b></div>
              <div class="so-field so-addr"><span>收货地址</span><b>{{ buyer(o).addr }}</b></div>
              <div v-if="o.payMethod" class="so-field"><span>支付方式</span><b>{{ o.payMethod }}</b></div>
              <div v-if="o.remark" class="so-field so-addr"><span>买家备注</span><b>{{ o.remark }}</b></div>
            </div>

            <!-- 商品明细：带款式图 + 规格 + 单价 × 数量 -->
            <div class="so-items">
              <div v-for="(it, i) in (o.items || [])" :key="i" class="so-item">
                <span class="so-art" :style="artStyle(it.art)"><span v-html="artHtml(it.art)"></span></span>
                <div class="so-item-info">
                  <h4>{{ it.title }}</h4>
                  <small>{{ it.sku || '默认' }}</small>
                </div>
                <div class="so-item-price">
                  <span v-html="price(it.price)"></span>
                  <small>×{{ it.qty }}</small>
                </div>
              </div>
            </div>

            <!-- 已发货订单直接露出最新一条物流轨迹 -->
            <div v-if="latestLogi(o)" class="so-logi">
              <span>最新物流</span>
              <b>{{ latestLogi(o).text }}</b>
              <small>{{ latestLogi(o).time }}</small>
            </div>
          </div>

          <div class="seller-order-foot">
            <div class="so-sum">
              <span>商品金额 <b>¥{{ fmt(o.goodsAmount) }}</b></span>
              <span>运费 <b>{{ money(o.freight) > 0 ? '¥' + fmt(o.freight) : '包邮' }}</b></span>
              <span v-if="money(o.discount) > 0" class="so-discount">优惠 <b>-¥{{ fmt(o.discount) }}</b></span>
              <span class="so-total">实付 <b>¥{{ fmt(o.total) }}</b></span>
            </div>
            <div class="o-actions">
              <template v-if="o.status === 'paid'">
                <button class="btn btn-primary" @click="ship(o)">立即发货</button>
              </template>
              <template v-else-if="o.status === 'shipped'">
                <span class="so-wait">已发货，等待买家确认收货</span>
              </template>
              <template v-else-if="o.status === 'pending'">
                <span class="so-wait">等待买家付款</span>
              </template>
              <template v-else>
                <span class="so-wait">该订单已{{ STATUS_TEXT[o.status] || '结束' }}</span>
              </template>
            </div>
          </div>
        </div>

        <div v-if="!filtered.length" class="seller-order-row" style="text-align:center;color:var(--text-3);padding:28px 18px">
          {{ filter ? '没有「' + (STATUS_TEXT[filter] || filter) + '」的订单' : '暂无订单，买家下单后会自动出现在这里' }}
        </div>
      </template>
    </template>

    <!-- 已登录但未开店：可直接开店 -->
    <div v-else-if="QM_STORE.state.user" class="empty-state">
      <div class="empty-icon">🏪</div>
      <h3>你还没有店铺</h3>
      <p>当前账号「{{ QM_STORE.state.user.nickname }}」名下还没有店铺。先开通店铺，即可管理订单。</p>
      <button class="btn btn-primary" @click="openShop">立即开店</button>
      <a class="btn btn-plain" href="#/home">先逛逛</a>
    </div>

    <!-- 未登录 -->
    <div v-else class="empty-state">
      <div class="empty-icon">🔐</div>
      <h3>请先登录</h3>
      <p>全站只有一套用户账号，登录后即可管理自己店铺的订单。</p>
      <a class="btn btn-primary" href="#/login?redirect=%2Fseller%2Forders">去登录</a>
    </div>
  </div>
</template>
