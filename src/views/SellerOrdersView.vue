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

const { toast, timeText, confirmDialog, openShopCreate } = QM_UI;
const router = useRouter();

/* store 不是响应式对象：用 shopTick 版本号驱动「当前店铺」重算（开店 / 档案更新后自增） */
const shopTick = ref(0);
const svc = computed(() => { shopTick.value; return QM_STORE.seller.current(); });
const shopId = computed(() => (svc.value ? svc.value.id : ''));
const list = ref([]);
const loading = ref(false);

const STATUS_TEXT = { pending: '待付款', paid: '待发货', shipped: '待收货', done: '已完成', canceled: '已取消' };

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

function itemsText(o) {
  return (o.items || []).map(it => it.title + (it.sku ? '（' + it.sku + '）' : '') + ' ×' + it.qty).join('；');
}

/* 未开店账号可直接在本页开店 */
function openShop() {
  if (!QM_STORE.state.user) { router.push({ path: '/login', query: { redirect: '/seller/orders' } }); return; }
  openShopCreate({ onDone: () => { shopTick.value++; refresh(); } });
}

let offShopProfile = null;
onMounted(() => {
  offShopProfile = QM_STORE.on('shopProfile', () => { shopTick.value++; });
  refresh();
});
onActivated(refresh);
onBeforeUnmount(() => { if (offShopProfile) { offShopProfile(); offShopProfile = null; } });
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
      <div class="seller-tip">💡 当前店铺：<b>{{ svc.shopName }}</b>，共 {{ list.length }} 笔订单。待发货订单可点击「发货」，物流轨迹由后端生成。</div>

      <div v-if="loading" class="empty-state">
        <div class="empty-icon">⏳</div>
        <h3>正在加载订单…</h3>
      </div>

      <template v-else>
        <div v-for="o in list" :key="o.id" class="seller-order-row">
          <div class="seller-order-head">
            <b>{{ o.orderNo || o.id }}</b>
            <span class="o-time">{{ timeText(o.createTime) }}</span>
            <span class="o-status" :class="o.status">{{ STATUS_TEXT[o.status] || o.status }}</span>
          </div>
          <div class="seller-order-body">
            <div v-if="o.address">买家：{{ o.address.name }} · {{ o.address.phone }}<br />{{ o.address.region }} {{ o.address.detail }}</div>
            <div v-else>买家：—</div>
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
