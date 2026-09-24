<script setup>
/* =========================================================
   青集市 · views/OrdersView.vue —— 我的订单
   数据来源：QM_API.orders（后端接口）：
   · 列表 GET /orders?status=&page=&size=
   · 计数 GET /orders/counts（顶部各页签数量）
   · 支付 / 取消 / 确认收货 / 提醒发货 / 物流均走接口
   售后服务：入口在本页，申请记录存本机浏览器（QM_STORE.afterSales）——
   后端售后接口尚未实现，因此只做前端记录与进度回显，不产生伪造的「已提交」假象。
   ========================================================= */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import QM_UI from '../core/ui.js';
import QM_API from '../core/api.js';
import QM_STORE from '../core/store.js';
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

/* 售后类型 / 原因字典（售后接口后端未实现，申请只记在本机浏览器，见 store.js afterSales） */
const AFTER_SALES_TYPES = [
  { key: 'refund', label: '仅退款' },
  { key: 'return', label: '退货退款' },
  { key: 'exchange', label: '换货' }
];
const AFTER_SALES_REASONS = ['不想要了', '商品质量问题', '发错货 / 少件', '与商品描述不符', '快递损坏或丢失', '其他原因'];

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
  const after = QM_STORE.afterSales.get(o.id);
  const afterBtn = after
    ? btn('order-after-sales-detail', o.id, '售后进度')
    : btn('order-after-sales', o.id, '售后服务');
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
    a.push(afterBtn, btn('goto-placeholder', '评价晒单'), btn('order-rebuy', o.id, '再次购买'));
  } else {
    a.push(btn('order-rebuy', o.id, '再次购买'));
  }
  return a.join('');
}

function orderCard(o) {
  const st = STATUS_TEXT[o.status] || STATUS_TEXT.pending;
  const after = QM_STORE.afterSales.get(o.id);
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
          ${after ? `<span class="o-tag">售后${esc(after.statusText || '处理中')}</span>` : ''}
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
      <p class="modal-sub" style="margin:14px 0 0">物流轨迹来自订单接口（GET /orders/{id}/logistics）</p>
      <div class="modal-actions"><button class="btn btn-plain" data-close>关闭</button></div>
    </div>`, { wide: true });
}

/**
 * 售后服务弹窗（申请表单：类型 / 原因 / 说明）。
 * 说明：后端售后接口尚未实现（本轮约定只改前端），提交后记录在
 * QM_STORE.state.afterSales（本机浏览器），订单卡据此显示「售后待处理」。
 */
function afterSalesModal(o) {
  const exist = QM_STORE.afterSales.get(o.id);
  if (exist) { afterSalesDetailModal(o, exist); return; }
  const m = modal(`
    <div>
      <h3>申请售后</h3>
      <p class="modal-sub">订单号 ${esc(o.orderNo)} · ${esc((o.items && o.items[0] && o.items[0].title) || '')}</p>
      <div class="form-row">
        <label>售后类型</label>
        <div class="tag-chips" id="asTypes">
          ${AFTER_SALES_TYPES.map((t, i) => `<button type="button" class="tag-chip${i === 0 ? ' active' : ''}" data-type="${t.key}">${t.label}</button>`).join('')}
        </div>
      </div>
      <div class="form-row">
        <label>售后原因</label>
        <select id="asReason">${AFTER_SALES_REASONS.map(r => `<option value="${esc(r)}">${esc(r)}</option>`).join('')}</select>
      </div>
      <div class="form-row">
        <label>补充说明</label>
        <textarea id="asRemark" rows="3" maxlength="200" placeholder="选填，最多 200 字"></textarea>
      </div>
      <p class="hint">演示说明：后端售后接口尚未实现，申请记录保存在本机浏览器，不会同步给卖家。</p>
      <div class="modal-actions">
        <button class="btn btn-plain" data-close>再想想</button>
        <button class="btn btn-primary" id="asSubmit">提交申请</button>
      </div>
    </div>`, { wide: true });

  let type = AFTER_SALES_TYPES[0].key;
  m.root.querySelectorAll('#asTypes .tag-chip').forEach(el => {
    el.onclick = () => {
      m.root.querySelectorAll('#asTypes .tag-chip').forEach(x => x.classList.toggle('active', x === el));
      type = el.dataset.type;
    };
  });
  m.root.querySelector('#asSubmit').onclick = () => {
    const typeText = (AFTER_SALES_TYPES.find(t => t.key === type) || {}).label || '仅退款';
    QM_STORE.afterSales.submit(o.id, {
      type,
      typeText,
      reason: m.root.querySelector('#asReason').value,
      remark: m.root.querySelector('#asRemark').value.trim()
    });
    m.close();
    toast('售后申请已提交（本机演示）', 'success');
    refresh();
  };
}

/* 售后进度弹窗：已提交过售后时，入口按钮显示「售后进度」，点开看申请内容与处理状态 */
function afterSalesDetailModal(o, rec) {
  modal(`
    <div>
      <h3>售后进度</h3>
      <p class="modal-sub">订单号 ${esc(o.orderNo)} · 申请时间 ${fullTime(rec.createTime).slice(0, 16)}</p>
      <div class="as-detail">
        <div><span>售后类型</span><b>${esc(rec.typeText)}</b></div>
        <div><span>售后原因</span><b>${esc(rec.reason)}</b></div>
        ${rec.remark ? `<div><span>补充说明</span><b>${esc(rec.remark)}</b></div>` : ''}
        <div><span>处理状态</span><b class="as-status">${esc(rec.statusText || '待处理')}</b></div>
      </div>
      <p class="hint">演示说明：后端售后接口尚未实现，该记录仅保存在本机浏览器，刷新页面仍在、换设备不同步。</p>
      <div class="modal-actions"><button class="btn btn-plain" data-close>关闭</button></div>
    </div>`, { wide: true });
}

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
      try { await QM_API.orders.pay(id); toast('支付成功！卖家将尽快发货', 'success'); await refresh(); }
      catch (err) { toast(err.message, 'error'); }
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
      if (o) afterSalesModal(o);
      break;
    case 'order-after-sales-detail':
      if (o) {
        const rec = QM_STORE.afterSales.get(o.id);
        if (rec) afterSalesDetailModal(o, rec);
      }
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
