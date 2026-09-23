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
import QM_API from '../core/api.js';
import QM_STORE from '../core/store.js';
import useRouteCompat from '../composables/useRouteCompat.js';

const { esc, sales, productCard, toast, modal, avatarHtml, isImage } = QM_UI;
const route = useRouteCompat();
const router = useRouter();

const shopName = computed(() => route.value.params[0] || '');
const shopId = ref('');       // 当前店铺标识（关注 / 取关接口用它定位店铺）

/* 页面状态：loading 加载中 / missing 店铺不存在 / ready 已就绪 */
const phase = ref('loading');
const shop = ref(null);       // 店铺信息（后端店铺档案 + shopName/score）
const sort = ref('default');  // default 综合 / sales 销量 / priceAsc 价格↑ / priceDesc 价格↓

/* 店铺商品：全部来自后端（GET /products?shopId=xxx）；
   本地已无演示商品，接口未实现或该店暂无商品时显示空态 */
const rawGoods = ref([]);
const goodsLoaded = ref(false);   // 是否成功从后端取到商品（区分「暂无商品」与「接口失败」）

const goods = computed(() => {
  const copy = rawGoods.value.slice();
  if (sort.value === 'sales') copy.sort((a, b) => b.sales - a.sales);
  else if (sort.value === 'priceAsc') copy.sort((a, b) => a.price - b.price);
  else if (sort.value === 'priceDesc') copy.sort((a, b) => b.price - a.price);
  return copy;
});
const goodsHtml = computed(() => goods.value.map(p => productCard(p)).join(''));

/* 店铺平均分：当前商品评分的均值（无商品时取 4.8） */
const avgScore = computed(() => {
  const list = rawGoods.value;
  if (!list.length) return '4.8';
  return (list.reduce((s, p) => s + Number((p.shop && p.shop.score) || 0), 0) / list.length).toFixed(1);
});

async function loadGoods(shopIdValue) {
  try {
    const data = await QM_API.products.list({ shopId: shopIdValue, page: 1, size: 100 });
    rawGoods.value = (data && data.list) || [];
    goodsLoaded.value = true;
  } catch (e) {
    rawGoods.value = [];
    goodsLoaded.value = false;
  }
}

/* 关注店铺状态 */
const favTick = ref(0);
const faved = computed(() => { favTick.value; return QM_STORE.shopFav.has(shopName.value); });
let offShopFav = null;

async function load() {
  phase.value = 'loading';
  shop.value = null;
  shopId.value = '';
  sort.value = 'default';
  rawGoods.value = [];
  /* 店铺定位：优先用地址里的 ?id=（商品 / 购物车 / 收藏等入口都带上了后端下发的店铺标识），
     没有该参数时再退回本地档案里的「店名 → shopId」映射 */
  const sid = route.value.query.id || QM_STORE.shopIdOf(shopName.value);
  if (!sid) { phase.value = 'missing'; return; }
  shopId.value = sid;
  let hit = QM_STORE.shopService(shopName.value) || { id: sid, shopName: shopName.value };
  /* 店铺档案接口优先（GET /shops/{id}）：成功后写入本地档案，店名 / 头像 / 简介 / 评分随之更新。
     合并时直接采用返回对象，不再只依赖本地「店名 → shopId」索引，避免索引缺失时字段丢失 */
  try {
    const remote = await QM_API.shops.get(sid);
    if (remote) {
      const saved = QM_STORE.rememberShop(Object.assign({}, remote, { id: remote.id || remote.shopId || remote.shop_id || sid })) || remote;
      hit = Object.assign({}, hit, saved, { id: sid, shopName: saved.name || remote.name || shopName.value });
      /* 关注态以服务端下发为准（本地 shopFavs 只是镜像）：否则换设备 / 清缓存 / 新标签页后
         已关注的店铺会显示成「未关注」，粉丝数却不为 0 */
      if (typeof remote.followed === 'boolean') QM_STORE.shopFav.set(shopName.value, remote.followed);
    }
  } catch (e) {
    /* 后端店铺档案接口不可达：沿用本地已有档案 */
  }
  await loadGoods(sid);
  shop.value = Object.assign({ shopName: QM_STORE.displayShopName(shopName.value), score: Number(avgScore.value) }, hit);
  phase.value = 'ready';
}

/* 点击店铺头像查看店铺基本信息 */
function openShopInfo() {
  const s = shop.value;
  if (!s) return;
  modal(`
    <div>
      <div style="display:flex;align-items:center;gap:14px;margin-bottom:16px">
        ${avatarHtml(s.avatar, 'lg', s.color)}
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
/* 关注 / 取消关注店铺：真正写库的是后端 POST / DELETE /shops/{shopId}/follow，
   本地 shopFavs 只是关注态镜像；接口返回的最新粉丝数用 applyFans 写回页面与本地档案 */
const favBusy = ref(false);
async function toggleFav() {
  if (!requireLogin()) return;
  if (favBusy.value) return;
  const sid = shopId.value || (shop.value && (shop.value.id || shop.value.shopId)) || '';
  if (!sid) { toast('店铺信息缺失，请刷新页面后重试', 'error'); return; }
  const wasFaved = QM_STORE.shopFav.has(shopName.value);
  favBusy.value = true;
  try {
    const res = wasFaved ? await QM_API.shops.unfollow(sid) : await QM_API.shops.follow(sid);
    QM_STORE.shopFav.set(shopName.value, !wasFaved);
    applyFans(res && res.fans);
    toast(wasFaved ? '已取消关注' : '已关注店铺 ♥', wasFaved ? '' : 'success');
  } catch (e) {
    toast((e && e.message) || '操作失败，请稍后重试', 'error');
  } finally {
    favBusy.value = false;
  }
}

/* 把接口返回的最新粉丝数写回本地档案 + 当前页面（店铺主页 / 详情页 / 店家中心口径一致） */
function applyFans(fans) {
  if (fans === undefined || fans === null) return;
  const sid = shopId.value || (shop.value && (shop.value.id || shop.value.shopId)) || '';
  if (sid) QM_STORE.rememberShop({ id: sid, fans });
  if (shop.value) shop.value = Object.assign({}, shop.value, { fans });
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
          <img v-if="isImage(shop.avatar)" class="avatar-img" :src="shop.avatar" alt="" />
          <template v-else>{{ shop.avatar || '店' }}</template>
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
          <button class="btn" :class="faved ? 'fav-on' : ''" :disabled="favBusy" @click="toggleFav">{{ faved ? '♥ 已关注' : '♡ 关注店铺' }}</button>
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
        <div v-if="!goodsLoaded" class="hint" style="margin:0 0 10px">商品加载失败：接口暂时不可达，请稍后重试。</div>
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
