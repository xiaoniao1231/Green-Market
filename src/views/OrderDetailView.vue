<script setup>
/* =========================================================
   玉子市场 · views/OrderDetailView.vue —— 订单详情页（#/order/:id）
   数据来源：QM_API.orders.get(id)（GET /orders/{id}）；物流优先取订单内嵌轨迹，
   为空时再调 GET /orders/{id}/logistics。取消 / 提醒发货 / 确认收货 / 再次购买
   与订单列表页同一套接口，成功后重新拉取本页数据；未付款订单的「立即支付」
   跳收银台 /pay（支付只有那一个入口）。
   入口：我的订单里点击商品图或商品名 → 本页。
   ========================================================= */
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import QM_UI from '../core/ui.js';
import QM_API from '../core/api.js';
import useRouteCompat from '../composables/useRouteCompat.js';

const { esc, price, fullTime, artStyle, artHtml, toast, confirmDialog } = QM_UI;
const route = useRouteCompat();
const router = useRouter();

/* 状态文案 + 配色类（与订单列表页同一口径，cls 对应 pages.css 的 .o-status.xxx） */
const STATUS_TEXT = {
  pending: { text: '待付款', cls: 'pending', hint: '订单已提交，等待付款' },
  paid: { text: '待发货', cls: 'paid', hint: '已付款，卖家正在准备发货' },
  shipped: { text: '待收货', cls: 'shipped', hint: '卖家已发货，请留意物流' },
  done: { text: '已完成', cls: 'done', hint: '交易已完成，感谢您的信任' },
  canceled: { text: '已取消', cls: 'canceled', hint: '订单已取消' }
};

const orderId = computed(() => route.value.params[0] || '');
const phase = ref('loading');     // loading / missing / ready
const order = ref(null);
const logistics = ref([]);
const afterSales = ref([]);       // 本单的售后单（从 GET /after-sales 全量里本地筛出）
const acting = ref(false);

/* 可申请售后的订单状态（与后端硬校验口径一致：待付款没有钱可退，已取消不成立） */
const AS_APPLICABLE = ['paid', 'shipped', 'done'];
const AS_PROCESSING = ['pending', 'agreed', 'returned'];
const canApplyAfterSales = computed(() => AS_APPLICABLE.includes((order.value || {}).status));
/* 本单的售后记录：售后粒度 = 订单（下单已按店铺拆单，一个订单 = 一个店铺），
   所以一个订单至多一条售后单，不再按「商品 + 款式」逐条匹配 */
function afterSaleOfOrder() {
  return afterSales.value[0] || null;
}
/* 售后入口文案：无记录 → 售后服务；处理中 → 售后进度；已了结 → 售后记录 */
function afterSaleBtnText() {
  const a = afterSaleOfOrder();
  if (!a) return '售后服务';
  return AS_PROCESSING.includes(a.status) ? '售后进度' : '售后记录';
}
/* 跳售后页（订单级）：只带 orderId —— 售后页在用户点击后才按订单打开申请 / 进度弹窗 */
function openAfterSales() {
  router.push('/after-sales?orderId=' + encodeURIComponent(orderId.value));
}

const st = computed(() => STATUS_TEXT[(order.value || {}).status] || STATUS_TEXT.pending);
const items = computed(() => (order.value && order.value.items) || []);
const addr = computed(() => (order.value && order.value.address) || {});
const totalQty = computed(() => items.value.reduce((s, i) => s + (i.qty || 0), 0));
const goodsAmount = computed(() => Number((order.value && order.value.goodsAmount) || 0));
const freight = computed(() => Number((order.value && order.value.freight) || 0));
const discount = computed(() => Number((order.value && order.value.discount) || 0));

/* 底部操作：按订单状态给可用动作（与列表页一致）；
   可申请售后的状态额外给一个「售后服务」入口（进售后页后按订单 / 店铺申请） */
const actions = computed(() => {
  const s = (order.value || {}).status;
  const asBtn = { action: 'order-after-sales', text: afterSaleBtnText() };
  if (s === 'pending') return [{ action: 'order-pay', text: '立即支付', kind: 'primary' }, { action: 'order-cancel', text: '取消订单' }];
  if (s === 'paid') {
    /* 催过就换文案（remindCount 由订单详情接口带出），买家一眼看出已经催过了 */
    const n = Number((order.value || {}).remindCount || 0);
    return [{ action: 'order-remind', text: n > 0 ? `已提醒卖家 ×${n}` : '提醒发货' }, asBtn];
  }
  if (s === 'shipped') return [{ action: 'order-logistics', text: '刷新物流' }, asBtn, { action: 'order-confirm', text: '确认收货', kind: 'primary' }];
  /* 已完成：可评价晒单（跳 #/reviews?orderId=，评价页自动打开本单的评价弹窗） */
  if (s === 'done') return [{ action: 'order-review', text: '评价晒单', kind: 'primary' }, asBtn, { action: 'order-rebuy', text: '再次购买' }];
  return [{ action: 'order-rebuy', text: '再次购买' }];
});

async function load() {
  phase.value = 'loading';
  order.value = null;
  logistics.value = [];
  afterSales.value = [];
  try {
    const o = await QM_API.orders.get(orderId.value);
    if (!o || !o.id) { phase.value = 'missing'; return; }
    order.value = o;
    logistics.value = (o.logistics || []).slice();
    phase.value = 'ready';
    /* 本单售后记录（决定底部「售后服务」入口显示成「售后进度」还是「售后记录」）：
       用「我的全部售后单」接口一次拿全量，再本地筛出本单的 —— 与订单列表页同一份数据源，
       后端因此不需要「按订单查售后」的接口；接口不可用时静默降级为空，不影响订单详情本身 */
    try {
      const all = await QM_API.afterSales.list();
      afterSales.value = all.filter(a => String(a.orderId) === String(orderId.value));
    } catch (e) { afterSales.value = []; }
    /* 订单内嵌轨迹为空时再单独拉一次物流接口（后端发货时写入） */
    if (!logistics.value.length) {
      try {
        const d = await QM_API.orders.logistics(orderId.value);
        logistics.value = ((d && d.list) || []).slice();
      } catch (e) { /* 物流接口不可用时保持空轨迹 */ }
    }
  } catch (e) {
    phase.value = 'missing';
    toast(e.message || '订单加载失败', 'error');
  }
}

async function onAction(a) {
  if (acting.value) return;
  const id = orderId.value;
  if (a.action === 'order-pay') {
    /* 支付统一在收银台（/pay）完成：带着订单号跳过去，不在详情页直接付款 */
    router.push('/pay?orderId=' + encodeURIComponent(id));
    return;
  } else if (a.action === 'order-cancel') {
    if (await confirmDialog('取消订单', '确定取消该订单吗？', '取消订单', true)) {
      acting.value = true;
      try { await QM_API.orders.cancel(id); toast('订单已取消'); await load(); }
      catch (e) { toast(e.message, 'error'); } finally { acting.value = false; }
    }
  } else if (a.action === 'order-remind') {
    try {
      const r = await QM_API.orders.remind(id);
      toast(`已提醒卖家发货（第 ${(r && r.remindCount) || 1} 次）`);
      await load();   // 重载详情，按钮文案同步变成「已提醒卖家 ×N」
    }
    catch (e) { toast(e.message, 'error'); }
  } else if (a.action === 'order-confirm') {
    if (await confirmDialog('确认收货', '请确认已收到商品，确认后订单完成。', '确认收货')) {
      acting.value = true;
      try { await QM_API.orders.confirm(id); toast('交易完成，感谢您的信任！', 'success'); await load(); }
      catch (e) { toast(e.message, 'error'); } finally { acting.value = false; }
    }
  } else if (a.action === 'order-logistics') {
    await load();
    toast(logistics.value.length ? '物流信息已刷新' : '暂无物流信息，卖家发货后可查看');
  } else if (a.action === 'order-review') {
    /* 评价晒单统一在独立评价页完成（星级 / 内容 / 晒图 / 追评），本页只负责跳转 */
    router.push('/reviews?orderId=' + encodeURIComponent(id));
  } else if (a.action === 'order-after-sales') {
    /* 售后服务统一在独立售后页完成：只带订单号过去（售后按订单 / 店铺申请），
       没有售后记录时由售后页直接弹出申请窗口，已有记录则不弹窗 */
    router.push('/after-sales?orderId=' + encodeURIComponent(id));
  } else if (a.action === 'order-rebuy') {
    try {
      for (const it of items.value) await QM_API.cart.add(it.productId, it.sku, it.qty, it.price);
      toast('已加入购物车');
      router.push('/cart');
    } catch (e) { toast(e.message, 'error'); }
  }
}

onMounted(load);
watch(orderId, () => load());
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb"><a href="#/orders">我的订单</a> / 订单详情</div>
        <h1>订单详情</h1>
      </div>
      <a class="btn btn-plain" href="#/orders">← 返回订单列表</a>
    </div>

    <div v-if="phase === 'loading'" class="order-card">
      <div class="empty-state"><div class="empty-icon">⏳</div><h3>订单加载中…</h3></div>
    </div>
    <div v-else-if="phase === 'missing'" class="order-card">
      <div class="empty-state">
        <div class="empty-icon">🧾</div>
        <h3>订单不存在</h3>
        <p>该订单可能已被删除，或链接失效</p>
        <a class="btn btn-primary" href="#/orders">返回订单列表</a>
      </div>
    </div>

    <div v-else class="order-card order-detail-card">
      <div class="order-head">
        <div class="oh-left">
          <span class="oh-time">{{ order.createTime ? fullTime(order.createTime).slice(0, 16) : '—' }}</span>
          <span class="oh-no">订单号 {{ order.orderNo }}</span>
        </div>
        <span class="o-status" :class="st.cls">{{ st.text }}</span>
      </div>

      <p class="od-hint">{{ st.hint }}</p>

      <section class="od-section">
        <h3>收货信息</h3>
        <p class="od-line"><b>{{ addr.name }}</b> {{ addr.phone }}</p>
        <p class="od-line od-addr">{{ addr.region }} {{ addr.detail }}</p>
      </section>

      <section class="od-section">
        <h3>商品清单 <small>共 {{ totalQty }} 件</small></h3>
        <div v-for="(it, i) in items" :key="i" class="oi-row">
          <span class="oi-art" data-action="open-product" :data-id="it.productId" title="查看商品详情"
                :style="artStyle((it.img && { img: it.img }) || it.art)"><span v-html="artHtml((it.img && { img: it.img }) || it.art)"></span></span>
          <div class="oi-info">
            <h4 class="ellipsis" data-action="open-product" :data-id="it.productId" :title="it.title">{{ it.title }}</h4>
            <div class="oi-meta"><span class="oi-sku" :title="it.sku">{{ it.sku }}</span></div>
          </div>
          <div class="oi-right">
            <span class="oi-price" v-html="price(it.price)"></span>
            <span class="oi-qty">×{{ it.qty }}</span>
          </div>
        </div>
      </section>

      <section class="od-section">
        <h3>金额明细</h3>
        <div class="checkout-item"><span>商品金额</span><span v-html="price(goodsAmount)"></span></div>
        <div class="checkout-item"><span>运费</span><span v-html="freight > 0 ? price(freight) : '包邮'"></span></div>
        <div v-if="discount > 0" class="checkout-item"><span>优惠</span><span v-html="'-' + price(discount)"></span></div>
        <div class="checkout-item od-total"><span>实付金额</span><b v-html="price(order.total)"></b></div>
      </section>

      <section class="od-section">
        <h3>订单信息</h3>
        <div class="od-grid">
          <div><span>支付方式</span><b>{{ order.payMethod || '—' }}</b></div>
          <div><span>下单时间</span><b>{{ order.createTime ? fullTime(order.createTime) : '—' }}</b></div>
          <div><span>付款时间</span><b>{{ order.payTime ? fullTime(order.payTime) : '—' }}</b></div>
          <div><span>发货时间</span><b>{{ order.shipTime ? fullTime(order.shipTime) : '—' }}</b></div>
          <div><span>完成时间</span><b>{{ order.finishTime ? fullTime(order.finishTime) : '—' }}</b></div>
          <div><span>订单备注</span><b>{{ order.remark || '—' }}</b></div>
        </div>
      </section>

      <section class="od-section">
        <h3>物流轨迹</h3>
        <ul v-if="logistics.length" class="od-timeline">
          <li v-for="(l, i) in logistics" :key="i">
            <span class="od-dot" :class="{ first: i === 0 }"></span>
            <div>
              <p>{{ l.text }}</p>
              <small>{{ l.time ? fullTime(l.time) : '' }}</small>
            </div>
          </li>
        </ul>
        <p v-else class="od-empty">暂无物流信息{{ order.status === 'shipped' ? '，物流数据同步中' : '，卖家发货后可查看' }}</p>
      </section>

      <div class="order-foot">
        <div class="of-sum">
          <span class="of-count">共 {{ totalQty }} 件商品</span>
          <span class="of-total">实付 <span v-html="price(order.total)"></span></span>
        </div>
        <div class="o-actions">
          <button v-for="a in actions" :key="a.action"
                  class="btn btn-sm" :class="a.kind === 'primary' ? 'btn-primary' : 'btn-plain'"
                  :disabled="acting" @click="onAction(a)">{{ a.text }}</button>
        </div>
      </div>
    </div>
  </div>
</template>
