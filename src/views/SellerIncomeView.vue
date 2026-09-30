<script setup>
/* =========================================================
   青集市 · views/SellerIncomeView.vue —— 我的店铺 · 收入明细（#/seller/income）
   数据：GET /seller/orders（本店订单）+ GET /seller/after-sales?status=refunded（本店已完成退款的售后单），
   在前端按工作台同口径汇总：
   · 累计收入 = 已结算订单金额（paid / shipped / done，排除待付款与已取消）；
   · 已退款   = 售后单终态 refunded 的 refundAmount 之和（换货 exchanged 不涉及退款，金额为 0）；
   · 累计到账 = 累计收入 − 已退款，这才是店家真正到手的钱。
   退款以售后单为凭据（单号 / 订单号 / 金额 / 办结时间），下方「退款记录」可逐笔对账。
   入口：工作台「累计到账」卡片。
   ========================================================= */
import { computed, onMounted, ref } from 'vue';
import QM_UI from '../core/ui.js';
import QM_API from '../core/api.js';
import QM_STORE from '../core/store.js';

const { toast, fullTime } = QM_UI;

const shopTick = ref(0);
const svc = computed(() => { shopTick.value; return QM_STORE.seller.current(); });
const shopId = computed(() => (svc.value ? svc.value.id : ''));
const loading = ref(false);
const orders = ref([]);
const refunds = ref([]);

/* 已结算订单：排除「待付款」与「已取消」（与工作台的累计收入口径一致） */
const settled = computed(() => orders.value.filter(o => o.status !== 'canceled' && o.status !== 'pending'));
const revenue = computed(() => settled.value.reduce((s, o) => s + Number(o.total || 0), 0));
const orderCount = computed(() => settled.value.length);

/* 已完成退款：售后单终态 refunded（换货 finished 为 exchanged，不退钱） */
const refundedTotal = computed(() => refunds.value.reduce((s, a) => s + Number(a.refundAmount || 0), 0));
/* 累计到账：扣掉退款后的净额 */
const netRevenue = computed(() => Math.max(0, revenue.value - refundedTotal.value));

const fmt = n => Number(n || 0).toFixed(2);
const qtyOf = o => (o.items || []).reduce((s, i) => s + (Number(i.qty) || 0), 0);
const buyerName = o => ((o.address && o.address.name) || '—');
/* 结算时间：优先完成时间，其次发货 / 付款 / 下单时间 */
const settleTime = o => o.finishTime || o.shipTime || o.payTime || o.createTime;
const sorted = computed(() => settled.value.slice().sort((a, b) => (settleTime(b) || 0) - (settleTime(a) || 0)));
/* 退款记录：按办结时间倒序（finishTime 是 'yyyy-MM-dd HH:mm:ss'，可直接比较字符串） */
const sortedRefunds = computed(() => refunds.value.slice()
  .sort((a, b) => String(b.finishTime || '').localeCompare(String(a.finishTime || ''))));
const refundBuyer = a => ((a.buyer && a.buyer.nickname) || '—');

async function refresh() {
  if (!shopId.value) { orders.value = []; refunds.value = []; return; }
  loading.value = true;
  try {
    const data = await QM_API.seller.orders({ page: 1, size: 200 });
    orders.value = (data && data.list) || [];
  } catch (e) {
    orders.value = [];
    toast('收入数据加载失败：' + e.message, 'error');
  }
  /* 退款单：失败时只是少扣退款，不影响收入主体展示，因此不弹错 */
  try {
    const data = await QM_API.seller.afterSales({ page: 1, size: 200, status: 'refunded' });
    refunds.value = (data && data.list) || [];
  } catch (e) {
    refunds.value = [];
  } finally {
    loading.value = false;
  }
}

onMounted(refresh);
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 我的店铺 / 收入明细</div>
        <h1>收入明细</h1>
      </div>
      <a class="btn btn-plain" href="#/seller">返回我的店铺</a>
    </div>

    <template v-if="svc">
      <div class="seller-stats income-stats">
        <div class="stat-card"><b>¥{{ fmt(netRevenue) }}</b><span>累计到账</span></div>
        <div class="stat-card"><b>¥{{ fmt(revenue) }}</b><span>累计收入</span></div>
        <div class="stat-card"><b>¥{{ fmt(refundedTotal) }}</b><span>已退款</span></div>
        <div class="stat-card"><b>{{ orderCount }}</b><span>已结算订单</span></div>
      </div>

      <div v-if="loading" class="empty-state">
        <div class="empty-icon">⏳</div>
        <h3>正在加载收入数据…</h3>
      </div>

      <div v-else-if="!sorted.length && !sortedRefunds.length" class="empty-state">
        <div class="empty-icon">💰</div>
        <h3>还没有收入记录</h3>
        <p>买家付款后，订单金额会统计在这里</p>
        <a class="btn btn-primary" href="#/seller/orders">去看看订单</a>
      </div>

      <template v-else>
        <div v-if="sorted.length" class="income-panel">
          <div class="income-title">收入流水</div>
          <table class="income-table">
            <thead>
              <tr>
                <th>订单号</th>
                <th>买家</th>
                <th>本店件数</th>
                <th class="ta-right">金额</th>
                <th>结算时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="o in sorted" :key="o.id">
                <td class="income-no">{{ o.orderNo || o.id }}</td>
                <td>{{ buyerName(o) }}</td>
                <td>{{ qtyOf(o) }} 件</td>
                <td class="ta-right income-amount">¥{{ fmt(o.total) }}</td>
                <td class="income-time">{{ settleTime(o) ? fullTime(settleTime(o)) : '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="sortedRefunds.length" class="income-panel">
          <div class="income-title">退款记录</div>
          <table class="income-table">
            <thead>
              <tr>
                <th>售后单号</th>
                <th>订单号</th>
                <th>买家</th>
                <th class="ta-right">退款金额</th>
                <th>办结时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="a in sortedRefunds" :key="a.id">
                <td class="income-no">{{ a.afterNo || a.id }}</td>
                <td class="income-no">{{ a.orderNo || a.orderId }}</td>
                <td>{{ refundBuyer(a) }}</td>
                <td class="ta-right income-refund">− ¥{{ fmt(a.refundAmount) }}</td>
                <td class="income-time">{{ a.finishTime ? fullTime(a.finishTime) : '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </template>

    <div v-else-if="QM_STORE.state.user" class="empty-state">
      <div class="empty-icon">🏪</div>
      <h3>你还没有店铺</h3>
      <p>开通店铺并卖出商品后，这里会显示收入明细。</p>
      <a class="btn btn-primary" href="#/seller">去开店</a>
    </div>

    <div v-else class="empty-state">
      <div class="empty-icon">🔐</div>
      <h3>请先登录</h3>
      <p>登录后即可查看自己店铺的收入明细。</p>
      <a class="btn btn-primary" href="#/login?redirect=%2Fseller%2Fincome">去登录</a>
    </div>
  </div>
</template>
