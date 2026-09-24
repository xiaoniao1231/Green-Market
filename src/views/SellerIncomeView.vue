<script setup>
/* =========================================================
   青集市 · views/SellerIncomeView.vue —— 我的店铺 · 收入明细（#/seller/income）
   数据：GET /seller/orders（本店订单），在前端按工作台同口径汇总 ——
   「已结算」= 已付款及之后的状态（paid / shipped / done），排除未付款与已取消。
   入口：工作台「累计收入」卡片。
   ========================================================= */
import { computed, onMounted, ref } from 'vue';
import QM_UI from '../core/ui.js';
import QM_API from '../core/api.js';
import QM_STORE from '../core/store.js';

const { toast, fullTime, price } = QM_UI;

const shopTick = ref(0);
const svc = computed(() => { shopTick.value; return QM_STORE.seller.current(); });
const shopId = computed(() => (svc.value ? svc.value.id : ''));
const loading = ref(false);
const list = ref([]);

/* 已结算订单：排除「待付款」与「已取消」（与工作台的累计收入口径一致） */
const settled = computed(() => list.value.filter(o => o.status !== 'canceled' && o.status !== 'pending'));
const revenue = computed(() => settled.value.reduce((s, o) => s + Number(o.total || 0), 0));
const orderCount = computed(() => settled.value.length);
const avgPerOrder = computed(() => (orderCount.value ? revenue.value / orderCount.value : 0));

const fmt = n => Number(n || 0).toFixed(2);
const qtyOf = o => (o.items || []).reduce((s, i) => s + (Number(i.qty) || 0), 0);
const buyerName = o => ((o.address && o.address.name) || '—');
/* 结算时间：优先完成时间，其次发货 / 付款 / 下单时间 */
const settleTime = o => o.finishTime || o.shipTime || o.payTime || o.createTime;
const sorted = computed(() => settled.value.slice().sort((a, b) => (settleTime(b) || 0) - (settleTime(a) || 0)));

async function refresh() {
  if (!shopId.value) { list.value = []; return; }
  loading.value = true;
  try {
    const data = await QM_API.seller.orders({ page: 1, size: 200 });
    list.value = (data && data.list) || [];
  } catch (e) {
    list.value = [];
    toast('收入数据加载失败：' + e.message, 'error');
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
        <h1>收入明细 <small>INCOME</small></h1>
      </div>
      <a class="btn btn-plain" href="#/seller">返回我的店铺</a>
    </div>

    <template v-if="svc">
      <div class="seller-tip">💡 收入口径：只统计<b>已付款及之后</b>的订单（待发货 / 待收货 / 已完成），排除未付款与已取消的订单。</div>

      <div class="seller-stats income-stats">
        <div class="stat-card"><b>¥{{ fmt(revenue) }}</b><span>累计收入</span></div>
        <div class="stat-card"><b>{{ orderCount }}</b><span>已结算订单</span></div>
        <div class="stat-card"><b>¥{{ fmt(avgPerOrder) }}</b><span>平均每单</span></div>
      </div>

      <div v-if="loading" class="empty-state">
        <div class="empty-icon">⏳</div>
        <h3>正在加载收入数据…</h3>
      </div>

      <div v-else-if="!sorted.length" class="empty-state">
        <div class="empty-icon">💰</div>
        <h3>还没有收入记录</h3>
        <p>买家付款后，订单金额会统计在这里</p>
        <a class="btn btn-primary" href="#/seller/orders">去看看订单</a>
      </div>

      <div v-else class="income-panel">
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
              <td class="ta-right income-amount" v-html="price(o.total)"></td>
              <td class="income-time">{{ settleTime(o) ? fullTime(settleTime(o)) : '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
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
