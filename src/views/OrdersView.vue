<script setup>
/* =========================================================
   青集市 · views/OrdersView.vue —— 我的订单
   数据来源：QM_API.orders（后端接口）：
   · 列表 GET /orders?status=&page=&size=
   · 计数 GET /orders/counts（顶部各页签数量）
   · 取消 / 确认收货 / 提醒发货 / 物流均走接口；「立即支付」跳收银台 /pay
     （支付只有收银台一个入口，本页不再直接调支付接口）
   售后服务：入口在本页，申请与进度在独立售后页完成（#/after-sales，接口 QM_API.afterSales.*）——
   本页只判断每张订单是否已有售后记录，据此把按钮显示成「售后服务」或「售后进度」。
   ========================================================= */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import QM_UI from '../core/ui.js';
import QM_API from '../core/api.js';
import useRouteCompat from '../composables/useRouteCompat.js';

const { esc, price, fullTime, artStyle, artHtml, toast, modal, confirmDialog } = QM_UI;
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
/* 状态文案 + 配色类（cls 对应 pages.css 里 .o-status.xxx 的配色） */
const STATUS_TEXT = {
  pending: { text: '待付款', cls: 'pending' },
  paid: { text: '待发货', cls: 'paid' },
  shipped: { text: '待收货', cls: 'shipped' },
  done: { text: '已完成', cls: 'done' },
  canceled: { text: '已取消', cls: 'canceled' }
};

/* 售后状态文案（后端只回状态码，中文字典由前端维护，与售后页同一口径） */
const AS_STATUS_TEXT = {
  pending: '待商家处理',
  agreed: '待买家寄回',
  returned: '待商家收货',
  refunded: '退款完成',
  exchanged: '换货完成',
  refused: '已拒绝',
  canceled: '已撤销'
};
/* 可申请售后的订单状态（与后端硬校验口径一致：待付款没有钱可退，已取消不成立） */
const AS_APPLICABLE = ['paid', 'shipped', 'done'];
/* 售后单映射：orderId → 售后单（GET /after-sales 一次拉全后本地建索引，
   与售后页共用同一份契约）。接口失败时静默降级为「所有可售后订单都显示『售后服务』」，
   不打扰买家浏览订单 —— 真正点击时再由售后页如实报错 */
const afterSalesByOrder = ref({});
const isAfterSalesProcessing = a => ['pending', 'agreed', 'returned'].includes(a.status);
const afterSaleOf = o => afterSalesByOrder.value[String(o.id)] || null;
async function loadAfterSales() {
  try {
    const d = await QM_API.afterSales.list({ page: 1, size: 100 });
    const map = {};
    (d.list || []).forEach(a => {
      const key = String(a.orderId);
      const cur = map[key];
      /* 同一订单可能有多条（不同商品各申请一次）：优先展示仍「进行中」的那条 */
      if (!cur || (isAfterSalesProcessing(a) && !isAfterSalesProcessing(cur))) map[key] = a;
    });
    afterSalesByOrder.value = map;
  } catch (e) {
    afterSalesByOrder.value = {};
  }
}

/* 底部按钮小工具：kind = plain | primary */
const btn = (action, id, text, kind) =>
  `<button class="btn btn-${kind || 'plain'} btn-sm" data-action="${action}" data-id="${esc(id)}">${text}</button>`;

/**
 * 底部操作区：按订单状态切换
 * · 待付款：取消订单 / 立即支付
 * · 待发货：提醒发货 / 售后服务
 * · 待收货：查看物流 / 售后服务 / 确认收货（店家发货后状态转 shipped，按钮自动从「提醒发货」变「查看物流」）
 * · 已完成：售后服务 / 评价晒单 / 再次购买
 * 已提交过售后的订单，入口变成「售后进度」。
 */
function orderActions(o) {
  const a = [];
  const after = afterSaleOf(o);
  /* 售后入口：有售后记录 → 「售后进度」（点开看时间线）；无记录且订单状态可售后 → 「售后服务」；
     待付款（还没付钱）与已取消订单不显示入口（后端同样硬校验） */
  const afterBtn = after
    ? btn('order-after-sales-detail', o.id, isAfterSalesProcessing(after) ? '售后进度' : '售后记录')
    : (AS_APPLICABLE.includes(o.status) ? btn('order-after-sales', o.id, '售后服务') : '');
  if (o.status === 'pending') {
    a.push(btn('order-cancel', o.id, '取消订单'), btn('order-pay', o.id, '立即支付', 'primary'));
  } else if (o.status === 'paid') {
    /* 催过就换文案：买家一眼看出「已经催过了」，不必反复点（后端另有冷却期兜底）。
       remindCount 由订单列表接口带出（LEFT JOIN seller_reminders） */
    const urged = Number(o.remindCount || 0) > 0;
    a.push(btn('order-remind', o.id, urged ? `已提醒卖家 ×${o.remindCount}` : '提醒发货'), afterBtn);
  } else if (o.status === 'shipped') {
    a.push(btn('order-logistics', o.id, '查看物流'), afterBtn, btn('order-confirm', o.id, '确认收货', 'primary'));
  } else if (o.status === 'done') {
    /* 评价晒单：跳独立评价页并带上本单号（该页自动打开这单的待评价商品弹窗）；
       不再走 #/placeholder 占位页 —— 评价功能已实现（见 docs/评价晒单接口文档.md） */
    a.push(afterBtn, btn('goto-reviews', o.id, '评价晒单'), btn('order-rebuy', o.id, '再次购买'));
  } else {
    a.push(btn('order-rebuy', o.id, '再次购买'));
  }
  return a.join('');
}

function orderCard(o) {
  const st = STATUS_TEXT[o.status] || STATUS_TEXT.pending;
  const after = afterSaleOf(o);
  const items = o.items || [];
  const totalQty = items.reduce((s, i) => s + (i.qty || 0), 0);
  const freight = Number(o.freight || 0);
  const discount = Number(o.discount || 0);
  return `
    <div class="order-card">
      <div class="order-head">
        <div class="oh-left">
          <span class="oh-time">${o.createTime ? fullTime(o.createTime).slice(0, 16) : '—'}</span>
          <span class="oh-no">订单号 ${esc(o.orderNo)}</span>
          ${after ? `<span class="o-tag">售后${esc(AS_STATUS_TEXT[after.status] || '处理中')}</span>` : ''}
        </div>
        <span class="o-status ${st.cls}">${st.text}</span>
      </div>
      <div class="order-body">
        ${items.map(it => {
          const art = (it.img && { img: it.img }) || it.art || null;   // 优先加购时选中的款式图，否则后端随订单下发的商品展示图
          /* 商品图与商品名点击都进「本单的订单详情」；订单详情页里再点商品才跳商品页 */
          return `<div class="oi-row">
            <span class="oi-art" data-action="open-order" data-id="${esc(o.id)}" title="查看订单详情" style="${artStyle(art)}">${artHtml(art)}</span>
            <div class="oi-info">
              <h4 class="ellipsis" data-action="open-order" data-id="${esc(o.id)}" title="查看订单详情">${esc(it.title)}</h4>
              <div class="oi-meta"><span class="oi-sku" title="${esc(it.sku)}">${esc(it.sku)}</span></div>
            </div>
            <div class="oi-right">
              <span class="oi-price">${price(it.price)}</span>
              <span class="oi-qty">×${it.qty}</span>
            </div>
          </div>`;
        }).join('')}
      </div>
      <div class="order-foot">
        <div class="of-sum">
          <span class="of-count">共 ${totalQty} 件商品</span>
          <span class="of-extra">${freight > 0 ? '运费 ' + price(freight) : '包邮'}</span>
          ${discount > 0 ? `<span class="of-extra of-discount">优惠 -${price(discount)}</span>` : ''}
          <span class="of-total">实付 ${price(o.total)}</span>
        </div>
        <div class="o-actions">${orderActions(o)}</div>
      </div>
    </div>`;
}

/* 物流追踪弹窗：轨迹优先取接口返回（GET /orders/{id}/logistics），空则回退订单内嵌轨迹 */
function logisticsModal(o, list) {
  const items = (list && list.length) ? list : (o.logistics || []);
  modal(`
    <div>
      <h3>物流追踪</h3>
      <p class="modal-sub">订单号 ${esc(o.orderNo)} · 承运商：轻集快递</p>
      <div class="logistics">
        ${items.map((l, i) => `
          <div class="logi-item ${i === 0 ? 'latest' : ''}">
            <div><b>${esc(l.text)}</b><small>${new Date(l.time).toLocaleString('zh-CN')}</small></div>
          </div>`).join('') || '<p class="hint">暂无物流信息</p>'}
      </div>
      <div class="modal-actions"><button class="btn btn-plain" data-close>关闭</button></div>
    </div>`, { wide: true });
}

/* 售后服务不再有本页弹窗：申请 / 进度 / 撤销 / 寄回物流全部在独立售后页完成
   （#/after-sales，契约见 docs/售后服务接口文档.md）——本页只保留入口按钮，
   点击时带上 orderId 跳过去，由售后页自动打开该订单的申请弹窗或进度弹窗。
   旧实现把申请写进浏览器存储（提示「本机演示」），店家看不到、后端也没有记录，已删除。 */

/* 当前激活页签（初始来自 route.query.status；原版点击页签仅本地切换、不改 URL） */
const tab = ref(route.value.query.status || '');
/* 各页签订单数（GET /orders/counts） */
const counts = ref({});
/* 当前页签订单列表（接口返回，本地 store 结构） */
const orders = ref([]);
/* 当前页签订单列表 HTML */
const listHtml = ref('');

/* 统一刷新：拉当前页签订单 + 各状态计数，再重渲染 */
async function refresh() {
  try {
    const data = await QM_API.orders.list(tab.value);
    orders.value = (data && data.list) || [];
    counts.value = await QM_API.orders.counts();
  } catch (e) {
    orders.value = [];
    counts.value = {};
  }
  /* 售后单映射（决定每张订单卡显示「售后服务」还是「售后进度」）：
     失败时静默降级，不影响订单列表本身 */
  await loadAfterSales();
  renderList();
}

function renderList() {
  listHtml.value = orders.value.length
    ? orders.value.map(orderCard).join('')
    : `<div class="order-card"><div class="empty-state"><div class="empty-icon">🧾</div><h3>暂无相关订单</h3><p>去首页挑选好物，下单后订单会显示在这里</p><a class="btn btn-primary" href="#/home">去逛逛</a></div></div>`;
}

function switchTab(key) {
  tab.value = key;
  refresh();
}

/* 订单列表事件委托（原版 #orderList.onclick，逻辑一致；操作全部走接口后刷新） */
async function onListClick(e) {
  const t = e.target.closest('[data-action]');
  if (!t) return;
  const id = t.dataset.id;
  /* 订单 id 用字符串比对：接口返回的 id 是数字，而 dataset.id 恒为字符串，
     严格 === 会让「查看物流 / 再次购买 / 售后服务」这类需要整单对象的按钮静默失效 */
  const o = orders.value.find(x => String(x.id) === String(id));
  switch (t.dataset.action) {
    case 'order-pay':
      /* 支付统一在收银台（/pay）完成：这里只把订单号带过去，
         不在列表页直接付款，避免出现第二个支付入口 */
      router.push('/pay?orderId=' + encodeURIComponent(id));
      break;
    case 'order-cancel':
      if (await confirmDialog('取消订单', '确定取消该订单吗？', '取消订单', true)) {
        try { await QM_API.orders.cancel(id); toast('订单已取消'); await refresh(); }
        catch (err) { toast(err.message, 'error'); }
      }
      break;
    case 'order-remind':
      try {
        const r = await QM_API.orders.remind(id);
        toast(`已提醒卖家发货（第 ${(r && r.remindCount) || 1} 次）`);
        await refresh();   // 重拉列表，按钮文案同步变成「已提醒卖家 ×N」
      } catch (err) { toast(err.message, 'error'); }
      break;
    case 'order-confirm':
      if (await confirmDialog('确认收货', '请确认已收到商品，确认后订单完成。', '确认收货')) {
        try { await QM_API.orders.confirm(id); toast('交易完成，感谢您的信任！', 'success'); await refresh(); }
        catch (err) { toast(err.message, 'error'); }
      }
      break;
    case 'order-logistics':
      if (o) {
        try {
          const data = await QM_API.orders.logistics(id);
          logisticsModal(o, (data && data.list) || []);
        } catch (err) { toast(err.message, 'error'); }
      }
      break;
    case 'order-after-sales':
    case 'order-after-sales-detail':
      /* 售后服务统一在独立售后页完成（#/after-sales，契约见 docs/售后服务接口文档.md）：
         带上订单号跳过去，售后页会自动打开该订单的「申请售后」弹窗
         （已有售后记录时则打开该条的进度弹窗） */
      router.push('/after-sales?orderId=' + encodeURIComponent(id));
      break;
    case 'order-rebuy':
      if (o) {
        try {
          for (const it of o.items) await QM_API.cart.add(it.productId, it.sku, it.qty);
          toast('已加入购物车'); router.push('/cart');
        } catch (err) { toast(err.message, 'error'); }
      }
      break;
  }
}

/* 路由 query.status 变化（同一组件复用，如 #/orders ↔ #/orders?status=…）时复位页签并重渲染 */
watch(() => route.value.query.status, v => { tab.value = v || ''; refresh(); });

onMounted(() => { refresh(); });

onBeforeUnmount(() => { /* 无订阅，无需清理 */ });
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
      >{{ t.label }} <b :id="'cnt-' + (t.key || 'all')">{{ counts[t.key || 'all'] || '' }}</b></button>
    </div>
    <div id="orderList" v-html="listHtml" @click="onListClick"></div>
  </div>
</template>
