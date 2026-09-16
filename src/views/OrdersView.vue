<script setup>
/* =========================================================
   青集市 · views/OrdersView.vue —— 我的订单
   移植自 mall-web/js/pages/orders.js（页面结构 / 交互逻辑不变）
   ========================================================= */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import QM_UI from '../core/ui.js';
import QM_MOCK from '../core/mock.js';
import QM_STORE from '../core/store.js';
import useRouteCompat from '../composables/useRouteCompat.js';

const { esc, price, artStyle, artHtml, toast, modal, confirmDialog } = QM_UI;
const route = useRouteCompat();
const router = useRouter();

const TABS = [
  { key: '', label: '全部订单' },
  { key: 'pending', label: '待付款' },
  { key: 'paid', label: '待发货' },
  { key: 'shipped', label: '待收货' },
  { key: 'done', label: '已完成' },
  { key: 'canceled', label: '已取消' }
];
const STATUS_TEXT = {
  pending: { text: '待付款', cls: '' },
  paid: { text: '待发货', cls: '' },
  shipped: { text: '待收货', cls: '' },
  done: { text: '已完成', cls: 'done' },
  canceled: { text: '已取消', cls: 'done' }
};

function orderCard(o) {
  const st = STATUS_TEXT[o.status] || STATUS_TEXT.pending;
  const actions = [];
  if (o.status === 'pending') actions.push(`<button class="btn btn-plain btn-sm" data-action="order-cancel" data-id="${o.id}">取消订单</button>`, `<button class="btn btn-primary btn-sm" data-action="order-pay" data-id="${o.id}">立即支付</button>`);
  if (o.status === 'paid') actions.push(`<button class="btn btn-plain btn-sm" data-action="order-remind" data-id="${o.id}">提醒发货</button>`);
  if (o.status === 'shipped') actions.push(`<button class="btn btn-plain btn-sm" data-action="order-logistics" data-id="${o.id}">查看物流</button>`, `<button class="btn btn-primary btn-sm" data-action="order-confirm" data-id="${o.id}">确认收货</button>`);
  if (o.status === 'done') actions.push(`<button class="btn btn-plain btn-sm" data-action="goto-placeholder" data-id="评价晒单">评价晒单</button>`, `<button class="btn btn-plain btn-sm" data-action="order-rebuy" data-id="${o.id}">再次购买</button>`);
  if (o.status === 'canceled') actions.push(`<button class="btn btn-plain btn-sm" data-action="order-rebuy" data-id="${o.id}">再次购买</button>`);
  return `
    <div class="order-card">
      <div class="order-head">
        <span>${new Date(o.createTime).toLocaleString('zh-CN')} · 订单号 ${esc(o.orderNo)}</span>
        <span class="o-status ${st.cls}">${st.text}</span>
      </div>
      <div class="order-body">
        ${o.items.map(it => {
          const p = QM_MOCK.byId(it.productId);
          return `<div class="oi-row">
            <span class="oi-art" style="${artStyle(p ? p.art : null)}">${artHtml(p ? p.art : null)}</span>
            <div class="oi-info"><h4 class="ellipsis" data-action="open-product" data-id="${esc(it.productId)}">${esc(it.title)}</h4><small>规格：${esc(it.sku)} × ${it.qty}</small></div>
            <span class="oi-price">${price(it.price * it.qty)}</span>
          </div>`;
        }).join('')}
      </div>
      <div class="order-foot">
        <span>共 ${o.items.reduce((s, i) => s + i.qty, 0)} 件商品，实付${price(o.total)}</span>
        <div class="o-actions">${actions.join('')}</div>
      </div>
    </div>`;
}

function logisticsModal(o) {
  modal(`
    <div style="position:relative">
      <button class="modal-close" data-close>×</button>
      <h3>物流追踪</h3>
      <p class="modal-sub">订单号 ${esc(o.orderNo)} · 承运商：轻集快递</p>
      <div class="logistics">
        ${o.logistics.map((l, i) => `
          <div class="logi-item ${i === 0 ? 'latest' : ''}">
            <div><b>${esc(l.text)}</b><small>${new Date(l.time).toLocaleString('zh-CN')}</small></div>
          </div>`).join('')}
      </div>
      <p class="modal-sub" style="margin:14px 0 0">演示数据：后端物流接口（GET /orders/{id}/logistics）落地后展示真实轨迹</p>
    </div>`, { wide: true });
}

/* 当前激活页签（初始来自 route.query.status；原版点击页签仅本地切换、不改 URL） */
const tab = ref(route.value.query.status || '');
/* 各页签订单数（QM_STORE 非响应式，变更后手动重渲染） */
const counts = ref({});
/* 当前页签订单列表 HTML */
const listHtml = ref('');

function renderCounts() {
  const c = {};
  TABS.forEach(t => {
    const n = t.key ? QM_STORE.orders.list(t.key).length : QM_STORE.state.orders.length;
    c[t.key] = n || '';
  });
  counts.value = c;
}

function renderList() {
  const orders = QM_STORE.orders.list(tab.value);
  listHtml.value = orders.length
    ? orders.map(orderCard).join('')
    : `<div class="order-card"><div class="empty-state"><div class="empty-icon">🧾</div><h3>暂无相关订单</h3><p>去首页挑选好物，下单后订单会显示在这里</p><a class="btn btn-primary" href="#/home">去逛逛</a></div></div>`;
}

function switchTab(key) {
  tab.value = key;
  renderList();
}

/* 订单列表事件委托（原版 #orderList.onclick，逻辑一致） */
async function onListClick(e) {
  const t = e.target.closest('[data-action]');
  if (!t) return;
  const id = t.dataset.id;
  const o = QM_STORE.orders.get(id);
  switch (t.dataset.action) {
    case 'order-pay':
      QM_STORE.orders.pay(id); toast('支付成功！卖家将尽快发货', 'success'); renderCounts(); renderList(); break;
    case 'order-cancel':
      if (await confirmDialog('取消订单', '确定取消该订单吗？', '取消订单', true)) { QM_STORE.orders.cancel(id); toast('订单已取消'); renderCounts(); renderList(); }
      break;
    case 'order-remind': toast('已提醒卖家发货～'); break;
    case 'order-confirm':
      if (await confirmDialog('确认收货', '请确认已收到商品，确认后订单完成。', '确认收货')) { QM_STORE.orders.confirm(id); toast('交易完成，感谢您的信任！', 'success'); renderCounts(); renderList(); }
      break;
    case 'order-logistics': if (o) logisticsModal(o); break;
    case 'order-rebuy':
      if (o) o.items.forEach(it => QM_STORE.cart.add(it.productId, it.sku, it.qty));
      toast('已加入购物车'); router.push('/cart');
      break;
  }
}

/* 路由 query.status 变化（同一组件复用，如 #/orders ↔ #/orders?status=…）时复位页签并重渲染 */
watch(() => route.value.query.status, v => { tab.value = v || ''; renderCounts(); renderList(); });

/* 原版每次进入页面全新渲染；此处额外订阅 orders 事件，外部下单/变更时保持同步 */
const offs = [];
onMounted(() => {
  renderCounts();
  renderList();
  offs.push(QM_STORE.on('orders', () => { renderCounts(); renderList(); }));
});

onBeforeUnmount(() => { offs.forEach(off => off()); });
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 我的订单</div>
        <h1>我的订单</h1>
      </div>
    </div>
    <div class="order-tabs" id="orderTabs">
      <button
        v-for="t in TABS"
        :key="t.key"
        :class="{ active: tab === t.key }"
        :data-id="t.key"
        @click="switchTab(t.key)"
      >{{ t.label }} <b :id="'cnt-' + (t.key || 'all')">{{ counts[t.key] || '' }}</b></button>
    </div>
    <div id="orderList" v-html="listHtml" @click="onListClick"></div>
  </div>
</template>
