<script setup>
/* =========================================================
   青集市 · views/ShopView.vue —— 店铺主页（店铺归属于某个用户账号）
   · 展示店铺：店铺头像（点击查看店铺基本信息）、店名、卖家名、
     简介、评分、粉丝、关注按钮、联系卖家（即与开这家店的用户聊天）
   · 商品列表：该店铺全部商品，支持排序（综合 / 销量 / 价格）
   ========================================================= */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import QM_UI from '../core/ui.js';
import QM_MOCK from '../core/mock.js';
import QM_STORE from '../core/store.js';
import useRouteCompat from '../composables/useRouteCompat.js';

const { esc, sales, productCard, toast, modal } = QM_UI;
const route = useRouteCompat();
const router = useRouter();

const shopName = computed(() => route.value.params[0] || '');

/* 页面状态：loading 加载中 / missing 店铺不存在 / ready 已就绪 */
const phase = ref('loading');
const shop = ref(null);       // 店铺信息（shopServices 条目 + shopName/score）
const sort = ref('default');  // default 综合 / sales 销量 / priceAsc 价格↑ / priceDesc 价格↓

const goods = computed(() => {
  if (!shop.value) return [];
  const list = QM_MOCK.products.filter(p => p.shop.name === shop.value.shopName);
  const copy = list.slice();
  if (sort.value === 'sales') copy.sort((a, b) => b.sales - a.sales);
  else if (sort.value === 'priceAsc') copy.sort((a, b) => a.price - b.price);
  else if (sort.value === 'priceDesc') copy.sort((a, b) => b.price - a.price);
  return copy;
});
const goodsHtml = computed(() => goods.value.map(p => productCard(p)).join(''));

/* 店铺平均分：商品评分均值（无商品时取 4.8） */
const avgScore = computed(() => {
  const list = QM_MOCK.products.filter(p => p.shop.name === shopName.value);
  if (!list.length) return '4.8';
  return (list.reduce((s, p) => s + p.shop.score, 0) / list.length).toFixed(1);
});

/* 关注店铺状态 */
const favTick = ref(0);
const faved = computed(() => { favTick.value; return QM_STORE.shopFav.has(shopName.value); });
let offShopFav = null;

function load() {
  phase.value = 'loading';
  shop.value = null;
  sort.value = 'default';
  const hit = QM_MOCK.shopServices[shopName.value];
  if (!hit) { phase.value = 'missing'; return; }
  shop.value = Object.assign({ shopName: shopName.value, score: Number(avgScore.value) }, hit);
  phase.value = 'ready';
}

/* 点击店铺头像查看店铺基本信息 */
function openShopInfo() {
  const s = shop.value;
  if (!s) return;
  modal(`
    <div>
      <div style="display:flex;align-items:center;gap:14px;margin-bottom:16px">
        <span class="shop-avatar lg" style="background:${s.color}">${s.avatar || '店'}</span>
        <div>
          <h3 style="margin-bottom:2px">${esc(s.shopName)}</h3>
          <p class="modal-sub" style="margin-bottom:0">卖家：${esc(s.owner || '—')}</p>
        </div>
      </div>
      <p class="shop-info-intro">${esc(s.shopIntro || s.intro || '')}</p>
      <div class="shop-info-grid">
        <div><b>${s.score || '—'}</b><span>店铺评分</span></div>
        <div><b>${sales(s.fans || 0)}</b><span>粉丝</span></div>
        <div><b>${s.founded || '—'}</b><span>开店时间</span></div>
        <div><b>${sales(goods.value.reduce((n, p) => n + p.sales, 0))}</b><span>累计销量</span></div>
      </div>
      <div class="modal-actions" style="margin-top:16px">
        <button class="btn btn-primary" data-action="goto-chat" data-id="${esc(s.userId || s.id)}">联系卖家</button>
        <button class="btn btn-plain" data-close>关闭</button>
      </div>
    </div>`, { wide: true });
}

function requireLogin() {
  if (QM_STORE.state.user) return true;
  toast('请先登录后再继续', 'error');
  router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } });
  return false;
}
function toggleFav() {
  if (!requireLogin()) return;
  const favedNow = QM_STORE.shopFav.toggle(shopName.value);
  toast(favedNow ? '已关注店铺 ♥' : '已取消关注', favedNow ? 'success' : '');
}

watch(shopName, load);
onMounted(() => {
  offShopFav = QM_STORE.on('shopFavs', () => { favTick.value++; });
  load();
});
onBeforeUnmount(() => { if (offShopFav) { offShopFav(); offShopFav = null; } });
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 店铺主页</div>
        <h1>{{ shop ? shop.shopName : '店铺' }}</h1>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-if="phase === 'loading'" class="empty-state">
      <div class="empty-icon">⏳</div>
      <h3>正在加载店铺…</h3>
    </div>

    <!-- 店铺不存在 -->
    <div v-else-if="phase === 'missing'" class="empty-state">
      <div class="empty-icon">🏪</div>
      <h3>店铺不存在</h3>
      <p>该店铺已关闭或链接失效</p>
      <a class="btn btn-primary" href="#/home">返回首页</a>
    </div>

    <template v-else>
      <!-- 店铺封面 -->
      <div class="shop-cover" :style="{ background: 'linear-gradient(120deg, ' + shop.color + ', #ff5000)' }">
        <button class="shop-avatar lg ring" :title="'点击查看「' + shop.shopName + '」基本信息'" @click="openShopInfo">
          {{ shop.avatar || '店' }}
        </button>
        <div class="shop-cover-main">
          <h1>{{ shop.shopName }}</h1>
          <p class="shop-owner">卖家：{{ shop.owner }}</p>
          <p class="shop-intro">{{ shop.shopIntro }}</p>
          <div class="shop-metrics">
            <span><b>{{ shop.score }}</b>店铺评分</span>
            <span><b>{{ sales(shop.fans) }}</b>粉丝</span>
            <span><b>{{ shop.founded }}</b>开店</span>
            <span><b>{{ goods.length }}</b>在售商品</span>
          </div>
        </div>
        <div class="shop-cover-actions">
          <button class="btn" :class="faved ? 'fav-on' : ''" @click="toggleFav">{{ faved ? '♥ 已关注' : '♡ 关注店铺' }}</button>
          <button class="btn btn-plain" data-action="goto-chat" :data-id="shop.userId">◌ 联系卖家</button>
        </div>
      </div>

      <!-- 商品区 -->
      <div class="shop-goods">
        <div class="shop-goods-head">
          <h3>全部商品 <small>· {{ goods.length }} 件</small></h3>
          <div class="shop-sorts">
            <button :class="{ active: sort === 'default' }" @click="sort = 'default'">综合</button>
            <button :class="{ active: sort === 'sales' }" @click="sort = 'sales'">销量</button>
            <button :class="{ active: sort === 'priceAsc' }" @click="sort = 'priceAsc'">价格↑</button>
            <button :class="{ active: sort === 'priceDesc' }" @click="sort = 'priceDesc'">价格↓</button>
          </div>
        </div>
        <div class="product-grid" v-html="goodsHtml"></div>
        <div v-if="!goods.length" class="empty-state">
          <div class="empty-icon">🛍️</div>
          <h3>该店铺暂无商品</h3>
          <p>卖家正在上新中，敬请期待</p>
        </div>
      </div>
    </template>
  </div>
</template>
