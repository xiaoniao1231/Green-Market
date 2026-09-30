<script setup>
/* =========================================================
   玉子市场 · views/PayView.vue —— 收银台（独立支付页 /pay）
   ---------------------------------------------------------
   支付从确认订单流程里独立出来：
   · /checkout 确认订单页：只负责收货地址 / 按店铺选优惠券 / 备注并提交订单；
   · /pay 收银台（本页）：只负责付款 —— 核对金额、确认支付。
   三个入口：
   ① 确认订单页提交订单后跳本页：一次下单可能按店铺拆成多笔子订单，
      这里合成一个付款批次，点一次「确认支付」把本次下单全部付清；
   ② 订单列表 / 订单详情的「立即支付」跳 /pay?orderId=…（单笔付款）；
   ③ 直接打开 /pay（无参数）：列出账号下全部待付款订单，按支付单号分组。
   待付上下文（本次下单的订单 id）通过 sessionStorage(qm_v2_pay) 在页面间传递，
   刷新本页不丢；上下文失效时回落到「待付款订单列表」重新定位，不会白屏。
   购物车在提交订单时就已经把本次结算的商品移出（后端下单事务里完成），
   所以本页只负责付款，不再需要清购物车。
   优惠券在确认订单页整单选一张，下单时由后端按店铺拆单后分摊到各子订单，
   本页只按订单展示抵扣结果，不提供选券入口。
   ========================================================= */
import { onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import QM_API from '../core/api.js';
import QM_STORE from '../core/store.js';
import QM_UI from '../core/ui.js';
import useRouteCompat from '../composables/useRouteCompat.js';

const { price, fullTime, artStyle, artHtml, toast } = QM_UI;
const route = useRouteCompat();
const router = useRouter();

/* 待支付上下文：{ orderIds: [], createdAt } —— 确认订单页提交成功后写入 */
const PAY_CTX_KEY = 'qm_v2_pay';

const STATUS_TEXT = { pending: '待付款', paid: '已付款', shipped: '待收货', done: '已完成', canceled: '已取消' };

const phase = ref('loading');   // loading / ready / done / settled / empty
const batches = ref([]);        // 付款批次：[{ key, payNoList, orders, qty, goods, freight, discount, total, payMethod }]
const payingKey = ref('');      // 正在支付的批次 key（防重复提交）
const doneInfo = ref(null);     // 支付成功信息 { total, count }
const settledOrder = ref(null); // 指定订单已不在待付款名单时的状态提示

const fmt = n => (Number.isInteger(Number(n)) ? String(Number(n)) : Number(n).toFixed(2));
const itemArt = it => (it.img && { img: it.img }) || it.art || null;

/* ---------- 金额 ---------- */
/* 单笔订单金额：以后端下发的快照为准（goodsAmount / freight / discount / total），
   缺字段时按条目价格 × 数量兜底，避免老数据展示成 0 */
function amountOf(o) {
  const items = o.items || [];
  const goods = Number(o.goodsAmount) || items.reduce((s, i) => s + Number(i.price || 0) * Number(i.qty || 0), 0);
  const freight = Number(o.freight) || 0;
  const discount = Number(o.discount) || 0;
  const total = (o.total === undefined || o.total === null) ? goods + freight - discount : Number(o.total);
  return { goods, freight, discount, total, qty: items.reduce((s, i) => s + Number(i.qty || 0), 0) };
}

/* 付款批次：一个批次里的订单点一次「确认支付」全部付清。
   mode 决定付款口径：
   · session —— 确认订单页的一次下单（可能按店铺拆成多笔）：同 payNo 的子订单由后端
     /orders/pay 一次付清，没有 payNo 的历史订单逐笔支付；
   · batch   —— 直接打开收银台时的同 payNo 分组：口径同上；
   · order   —— 订单列表 / 详情的「立即支付」：只付这一笔（与订单页原行为一致，
                不会把同一支付单号下的其他子订单一起付掉）。 */
function toBatch(orders, key, mode) {
  const agg = orders.reduce((a, o) => {
    const x = amountOf(o);
    a.qty += x.qty; a.goods += x.goods; a.freight += x.freight;
    a.discount += x.discount; a.total += x.total;
    return a;
  }, { qty: 0, goods: 0, freight: 0, discount: 0, total: 0 });
  return Object.assign({ key, mode, orders }, agg, {
    payNoList: [...new Set(orders.map(o => o.payNo).filter(Boolean))],
    payMethod: (orders[0] && orders[0].payMethod) || '支付宝'
  });
}

/* mode='session'：给定的订单合成一个批次（确认订单页的一次下单）；
   其余：按支付单号分组，各自成批（订单列表入口是单笔，直接打开收银台是待付清单） */
function toBatches(list, mode) {
  if (mode === 'session') return [toBatch(list, 'session', 'session')];
  const map = new Map();
  list.forEach(o => {
    const key = o.payNo ? 'p:' + o.payNo : 'o:' + o.id;
    if (!map.has(key)) map.set(key, []);
    map.get(key).push(o);
  });
  return [...map.entries()].map(([key, orders]) => toBatch(orders, key, mode));
}

/* ---------- 待支付上下文 ---------- */
function readCtx() {
  try {
    const raw = sessionStorage.getItem(PAY_CTX_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (e) { return null; }
}
function clearCtx() {
  try { sessionStorage.removeItem(PAY_CTX_KEY); } catch (e) { /* 存储不可用时忽略 */ }
}

/* 指定订单已不在待付款名单（已付 / 已取消 / 不存在）：查它当前状态，给出明确提示 */
async function findSettled(orderId) {
  let o = QM_STORE.state.orders.find(x => String(x.id) === String(orderId)) || null;
  if (!o) {
    try { o = await QM_API.orders.get(orderId); } catch (e) { o = null; }
  }
  if (!o) return null;
  return Object.assign({}, o, { statusText: STATUS_TEXT[o.status] || '状态未知' });
}

/* ---------- 初始化：定位本次要支付的订单 ---------- */
async function init() {
  phase.value = 'loading';
  doneInfo.value = null;
  settledOrder.value = null;
  payingKey.value = '';

  const q = route.value.query || {};
  const wantOrderId = String(q.orderId || '');
  const ctx = readCtx() || {};
  const ctxIds = (ctx.orderIds || []).map(String);

  /* 待付款订单：先用本地缓存（下单 / 列表接口写入），缓存覆盖不到目标时再拉服务端列表 */
  let pending = QM_STORE.state.orders.filter(o => o.status === 'pending');
  const covers = list => {
    if (wantOrderId) return list.some(o => String(o.id) === wantOrderId);
    if (ctxIds.length) return ctxIds.every(id => list.some(o => String(o.id) === id));
    return list.length > 0;
  };
  if (!covers(pending)) {
    try {
      const data = await QM_API.orders.list('pending');
      pending = ((data && data.list) || []).filter(o => o && o.status === 'pending');
    } catch (e) { /* 后端不可达：沿用本地缓存，页面不因此空白 */ }
  }

  /* 入口②：订单列表 / 详情的「立即支付」→ 只付这一笔 */
  if (wantOrderId) {
    const hit = pending.filter(o => String(o.id) === wantOrderId);
    if (hit.length) { batches.value = toBatches(hit, 'order'); phase.value = 'ready'; return; }
    settledOrder.value = await findSettled(wantOrderId);
    clearCtx();
    phase.value = settledOrder.value ? 'settled' : 'empty';
    return;
  }

  /* 入口①：确认订单页提交后的一次下单（可能按店铺拆成多笔）→ 合成一个付款批次 */
  if (ctxIds.length) {
    const hit = pending.filter(o => ctxIds.includes(String(o.id)));
    if (hit.length) { batches.value = toBatches(hit, 'session'); phase.value = 'ready'; return; }
    clearCtx();
  }

  /* 入口③：直接打开收银台 → 全部待付款订单，按支付单号分组 */
  batches.value = toBatches(pending, 'batch');
  phase.value = batches.value.length ? 'ready' : 'empty';
}

/* ---------- 支付 ---------- */
async function pay(b) {
  if (payingKey.value) return;
  payingKey.value = b.key;
  try {
    if (b.mode === 'order') {
      /* 单一笔订单付款：与订单页原来的「立即支付」同口径，不会连带同批其他子订单 */
      for (const o of b.orders) await QM_API.orders.pay(o.id);
    } else {
      /* 一次下单（或同支付单号分组）：同 payNo 一次付清，没有 payNo 的历史订单逐笔付 */
      for (const pn of b.payNoList) await QM_API.orders.payBatch(pn);
      for (const o of b.orders.filter(x => !x.payNo)) await QM_API.orders.pay(o.id);
    }
  } catch (e) {
    payingKey.value = '';
    toast((e && e.message) || '支付失败，请稍后重试', 'error');
    await init();   // 重新以服务端为准定位待付款订单（失败 / 重复支付后状态可能已变）
    return;
  }

  /* 支付成功：购物车在下单时已移出本次商品，这里只清理待支付上下文 */
  clearCtx();

  doneInfo.value = { total: b.total, count: b.orders.length };
  payingKey.value = '';
  phase.value = 'done';
  toast(b.orders.length > 1 ? `支付成功！${b.orders.length} 笔订单已付清` : '支付成功！卖家将尽快发货', 'success');
}

/* 收银台里不需要支付方式的二次选择：方式在提交订单时就写进订单了，
   这里只回显，避免出现「选了却写不进订单」的假交互 */
function goOrders() { router.push('/orders'); }

onMounted(init);
/* 同一组件内 query 变化（如连续点不同订单的「立即支付」）时重新定位 */
watch(() => JSON.stringify(route.value.query), () => { init(); });
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 我的订单 / 收银台</div>
        <h1>收银台</h1>
      </div>
      <a class="btn btn-plain" href="#/orders">我的订单 ›</a>
    </div>

    <p v-if="phase === 'loading'" class="hint" style="padding:24px 0">正在准备支付信息…</p>

    <!-- 支付成功 -->
    <section v-else-if="phase === 'done'" class="ck-card">
      <div class="pay-done">
        <div class="pay-done-icon">✅</div>
        <h3>支付成功</h3>
        <p>已支付 <b v-html="price(doneInfo.total)"></b>，共 {{ doneInfo.count }} 笔订单，卖家将尽快为你发货。</p>
        <div class="pay-done-actions">
          <button class="btn btn-primary" @click="goOrders">查看我的订单</button>
          <a class="btn btn-plain" href="#/home">继续购物</a>
        </div>
      </div>
    </section>

    <!-- 指定订单已不在待付款名单 -->
    <div v-else-if="phase === 'settled'" class="empty-state" style="padding:60px 0">
      <div class="empty-icon">✅</div>
      <h3>该订单无需支付</h3>
      <p>{{ settledOrder.statusText }}（订单号 {{ settledOrder.orderNo }}）</p>
      <a class="btn btn-primary" href="#/orders">返回我的订单</a>
    </div>

    <!-- 没有待付款订单 -->
    <div v-else-if="phase === 'empty'" class="empty-state" style="padding:60px 0">
      <div class="empty-icon">💳</div>
      <h3>没有待支付的订单</h3>
      <a class="btn btn-primary" href="#/cart">去购物车</a>
    </div>

    <!-- 待付款批次 -->
    <template v-else>
      <section v-for="b in batches" :key="b.key" class="ck-card">
        <div class="ck-card-title">
          <span class="ck-shop-name">待支付订单</span>
          <small>{{ b.orders.length }} 笔 · 共 {{ b.qty }} 件商品</small>
        </div>

        <!-- 每笔订单：商品明细 + 本单金额（优惠券在确认订单页按店铺选择，这里只展示抵扣结果） -->
        <div v-for="o in b.orders" :key="o.id" class="pay-order">
          <div class="pay-order-head">
            <b>订单号 {{ o.orderNo }}</b>
            <small>{{ o.createTime ? fullTime(o.createTime).slice(0, 16) : '' }}</small>
          </div>
          <div class="ck-items">
            <div v-for="(it, idx) in (o.items || [])" :key="idx" class="ck-item">
              <span class="ck-art" :style="artStyle(itemArt(it))" v-html="artHtml(itemArt(it))"></span>
              <div class="ck-item-info">
                <h4>{{ it.title }}</h4>
                <small>{{ it.sku }} · ×{{ it.qty }}</small>
              </div>
              <b v-html="price(Number(it.price || 0) * Number(it.qty || 0))"></b>
            </div>
          </div>
          <div class="ck-g-sum">
            <div class="checkout-item"><span>商品金额</span><span v-html="price(amountOf(o).goods)"></span></div>
            <div class="checkout-item">
              <span>运费</span>
              <span v-html="amountOf(o).freight ? price(amountOf(o).freight) : '包邮'"></span>
            </div>
            <div v-if="amountOf(o).discount > 0" class="checkout-item">
              <span>优惠券抵扣</span>
              <span class="pay-minus">− <span v-html="price(amountOf(o).discount)"></span></span>
            </div>
          </div>
        </div>

        <!-- 本批合计 + 支付 -->
        <div class="ck-g-sum pay-batch-foot">
          <div v-if="b.orders.length > 1" class="hint" style="margin:0 0 8px">
            🧾 本批共 {{ b.orders.length }} 笔订单
          </div>
          <div class="checkout-item"><span>商品金额</span><span v-html="price(b.goods)"></span></div>
          <div class="checkout-item">
            <span>运费</span>
            <span v-html="b.freight ? price(b.freight) : '包邮'"></span>
          </div>
          <div class="checkout-item">
            <span>优惠券</span>
            <span v-html="b.discount ? '− ¥' + fmt(b.discount) : '− ¥0.00'"></span>
          </div>
          <div class="checkout-item pay-method-row"><span>支付方式</span><b>{{ b.payMethod }}</b></div>
          <div class="checkout-item" style="font-size:15px">
            <span><b>应付总额</b></span>
            <b v-html="price(b.total)" style="color:var(--accent)"></b>
          </div>
          <button class="btn btn-primary btn-lg" style="width:100%;margin-top:14px"
                  :disabled="!!payingKey" @click="pay(b)">
            {{ payingKey === b.key ? '支付中…' : '确认支付' }}
          </button>
        </div>
      </section>
    </template>
  </div>
</template>
