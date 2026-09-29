<script setup>
/* =========================================================
   青集市 · views/SearchView.vue —— 搜索结果页
   排序栏（综合 / 销量 / 价格 ↑↓）走后端 /products/search 的 sort 参数；
   下面两个排序按钮在**已取回的列表**上本地排：
   · 按销量   → sales 降序；
   · 按商品评分 → 优先商品级 rating 降序，未下发时回退 shop.score 降序。
     说明：products 表目前没有商品级 rating 列，项目里唯一的评分数据是 shops.score
     （店铺评分 = 该店全部商品评分的平均值，随商品对象的 shop.score 下发）；
     后端补齐 rating 后前端无需再改，会自动优先用它。后端 search 不支持按评分排序，
     因此这个维度只能在本地排。
   一次取回的条数不能太少，否则本地排序的样本不全。
   ========================================================= */
import { computed, onMounted, ref, watch } from 'vue';
import QM_UI from '../core/ui.js';
import QM_API from '../core/api.js';
import useRouteCompat from '../composables/useRouteCompat.js';

const { productCard, emptyState, toast } = QM_UI;
const route = useRouteCompat();

/* 单次取回条数：本地排序依赖已取回的数据，取 200 条覆盖课程项目的商品量 */
const FETCH_SIZE = 200;

const sort = ref('default');
const list = ref([]);
const total = ref(0);

/* 本地排序维度：'' 不排（保持后端顺序）/ 'sales' 按销量 / 'rating' 按商品评分 */
const rankBy = ref('');

const num = v => {
  const n = Number(v);
  return Number.isFinite(n) ? n : 0;
};
/* 评分取值：优先商品级 rating（后端补齐后自动生效，前端无需再改），
   没有时回退店铺评分 shop.score（该店全部商品评分的平均值）；都缺按 0，排最后 */
const scoreOf = p => {
  const r = p.rating;
  if (r !== undefined && r !== null && r !== '') {
    const n = Number(r);
    if (Number.isFinite(n)) return n;
  }
  return num(p.shop && p.shop.score);
};

const shown = computed(() => {
  const arr = list.value.slice();
  if (rankBy.value === 'sales') return arr.sort((a, b) => num(b.sales) - num(a.sales));
  if (rankBy.value === 'rating') return arr.sort((a, b) => scoreOf(b) - scoreOf(a));
  return arr;
});

const cardsHtml = computed(() => (shown.value.length
  ? shown.value.map(p => productCard(p)).join('')
  : emptyState('🔍', '没有找到相关商品', '换个关键词试试，或去首页逛逛推荐好物')));

const q = computed(() => route.value.query.q || '');

async function load() {
  try {
    const data = await QM_API.products.search(q.value, Object.assign({ page: 1, size: FETCH_SIZE }, sort.value === 'default' ? {} : { sort: sort.value }));
    /* 后端返回 {code:1,data:null} 时 data.list 会抛 TypeError，导致搜索结果页整页空白 */
    list.value = (data && data.list) || [];
    total.value = (data && data.total) || 0;
  } catch (e) {
    list.value = [];
    total.value = 0;
    toast(e.message, 'error');
  }
}

function setSort(key) {
  sort.value = key;
  load();
}

/* 点击排序按钮：再点一次取消（回到后端返回的顺序） */
function toggleRank(key) {
  rankBy.value = rankBy.value === key ? '' : key;
}

/* 搜索关键词变化（同一组件复用）时重新搜索并复位排序 */
watch(q, () => { sort.value = 'default'; rankBy.value = ''; load(); });
onMounted(load);
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 搜索</div>
        <h1>“{{ q }}” 的搜索结果</h1>
      </div>
    </div>
    <div class="sort-bar" id="sortBar">
      <button :class="{ active: sort === 'default' }" data-sort="default" @click="setSort('default')">综合</button>
      <button :class="{ active: sort === 'sales' }" data-sort="sales" @click="setSort('sales')">销量</button>
      <button :class="{ active: sort === 'priceAsc' }" data-sort="priceAsc" @click="setSort('priceAsc')">价格 ↑</button>
      <button :class="{ active: sort === 'priceDesc' }" data-sort="priceDesc" @click="setSort('priceDesc')">价格 ↓</button>
      <span class="sort-spacer"></span>
      <span class="result-count" id="resultCount">共 {{ total }} 件相关商品</span>
    </div>
    <div class="filter-chips">
      <button type="button" class="pill pill-orange" :class="{ active: rankBy === 'sales' }" @click="toggleRank('sales')">按销量</button>
      <button type="button" class="pill pill-green" :class="{ active: rankBy === 'rating' }" @click="toggleRank('rating')"
              title="按商品评分排序：后端下发商品评分时用它，未下发则回退店铺评分">按商品评分</button>
    </div>
    <div id="searchResults" class="product-grid large" v-html="cardsHtml"></div>
  </div>
</template>
