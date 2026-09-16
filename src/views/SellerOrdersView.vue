<script setup>
/* =========================================================
   青集市 · views/SellerOrdersView.vue —— 我的店铺 · 订单管理（演示）
   展示本店铺商品产生的订单：待发货订单可一键发货（写入本地演示数据）
   ========================================================= */
import { computed, onMounted, ref } from 'vue';
import QM_UI from '../core/ui.js';
import QM_STORE from '../core/store.js';

const { toast, timeText } = QM_UI;

const shopId = computed(() => (QM_STORE.seller.current() ? QM_STORE.seller.current().id : ''));
const svc = computed(() => QM_STORE.seller.current());
const list = ref([]);

const STATUS_TEXT = { pending: '待付款', paid: '待发货', shipped: '待收货', done: '已完成', canceled: '已取消' };

function refresh() {
  if (!shopId.value) return;
  list.value = QM_STORE.seller.orders(shopId.value);
}

function ship(o) {
  QM_STORE.orders.ship(o.id);
  refresh();
  toast('已发货，物流轨迹已生成', 'success');
}

function itemsText(o) {
  return o.items.map(it => it.title + (it.sku ? '（' + it.sku + '）' : '') + ' ×' + it.qty).join('；');
}

onMounted(() => { refresh(); });
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
      <div class="seller-tip">💡 当前店铺：<b>{{ svc.shopName }}</b>，共 {{ list.length }} 笔订单（含演示种子订单）。待发货订单可点击「发货」生成物流轨迹。</div>

      <div v-for="o in list" :key="o.id" class="seller-order-row">
        <div class="seller-order-head">
          <b>{{ o.orderNo }}</b>
          <span class="o-time">{{ timeText(o.createTime) }}</span>
          <span class="o-status" :class="o.status">{{ STATUS_TEXT[o.status] || o.status }}</span>
        </div>
        <div class="seller-order-body">
          <div>买家：{{ o.address.name }} · {{ o.address.phone }}<br />{{ o.address.region }} {{ o.address.detail }}</div>
          <div style="margin-top:6px">{{ itemsText(o) }}</div>
        </div>
        <div class="seller-order-foot">
          <span class="o-total">实付 <b>¥{{ o.total }}</b>（含优惠 {{ o.discount ? '¥' + o.discount : '无' }}）</span>
          <div class="o-actions">
            <template v-if="o.status === 'paid'">
              <button class="btn btn-primary" @click="ship(o)">立即发货</button>
            </template>
            <template v-else-if="o.status === 'shipped'">
              <a class="btn btn-plain" href="#/orders">等待买家确认收货</a>
            </template>
          </div>
        </div>
      </div>

      <div v-if="!list.length" class="seller-order-row" style="text-align:center;color:var(--text-3)">暂无订单，买家下单后会自动出现在这里</div>
    </template>

    <!-- 已登录但未开店 -->
    <div v-else-if="QM_STORE.state.user" class="empty-state">
      <div class="empty-icon">🏪</div>
      <h3>你还没有店铺</h3>
      <p>当前账号「{{ QM_STORE.state.user.nickname }}」名下还没有店铺。使用已开店的演示账号登录（如 owner001 / 123456）即可管理订单。</p>
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
