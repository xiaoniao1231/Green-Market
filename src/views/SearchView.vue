<script setup>
/* =========================================================
   青集市 · views/SearchView.vue —— 搜索结果页
   排序栏：综合 / 销量 / 价格 / 评分。
   · 综合：保持后端返回的默认顺序；
   · 销量 / 价格 / 评分：都在已取回的列表上本地排序，点第一下是降序、
     再点一下切升序、如此循环；切到别的维度时重新从降序开始。
   评分取值优先商品级 rating，未下发时回退店铺评分 shop.score（后端 search
   不支持这两个维度，故本地排；一次取 200 条，覆盖课程项目的商品量）。
   ========================================================= */
import { computed, onMounted, ref, watch } from 'vue';
import QM_UI from '../core/ui.js';
import QM_API from '../core/api.js';
import useRouteCompat from '../composables/useRouteCompat.js';

const { productCard, emptyState, toast } = QM_UI;
const route = useRouteCompat();

/* 单次取回条数：本地排序依赖已取回的数据，取 200 条覆盖课程项目的商品量 */
const FETCH_SIZE = 200;

/* 排序维度：default 综合 / sales 销量 / price 价格 / rating 评分；order：desc 降序 / asc 升序 */
const sortKey = ref('default');
const sortOrder = ref('desc');

const list = ref([]);
const total = ref(0);

const num = v => {
  const n = Number(v);
  return Number.isFinite(n) ? n : 0;
};
/* 评分取值：优先商品级 rating（后端补齐后自动生效），没有时回退店铺评分 shop.score；都缺按 0 */
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
  const dir = sortOrder.value === 'asc' ? 1 : -1;
  if (sortKey.value === 'sales') return arr.sort((a, b) => (num(a.sales) - num(b.sales)) * dir);
  if (sortKey.value === 'price') return arr.sort((a, b) => (num(a.price) - num(b.price)) * dir);
  if (sortKey.value === 'rating') return arr.sort((a, b) => (scoreOf(a) - scoreOf(b)) * dir);
  return arr; // 综合：保持后端顺序
});

const cardsHtml = computed(() => (shown.value.length
  ? shown.value.map(p => productCard(p)).join('')
  : emptyState('🔍', '没有找到相关商品', '换个关键词试试，或去首页逛逛推荐好物')));

const q = computed(() => route.value.query.q || '');

async function load() {
  try {
    const data = await QM_API.products.search(q.value, { page: 1, size: FETCH_SIZE });
    list.value = (data && data.list) || [];
    total.value = (data && data.total) || 0;
  } catch (e) {
    list.value = [];
    total.value = 0;
    toast(e.message, 'error');
  }
}

/* 当前排序方向：↓ 高→低（降序）/ ↑ 低→高（升序） */
const dirArrow = computed(() => (sortOrder.value === 'desc' ? '↓' : '↑'));

/* 点排序按钮：同一维度再点切换升降序（降序 → 升序 → 降序…），换维度从降序开始 */
function onSort(key) {
  if (key === 'default') { sortKey.value = 'default'; sortOrder.value = 'desc'; return; }
  if (sortKey.value === key) sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc';
  else { sortKey.value = key; sortOrder.value = 'desc'; }
}

/* 搜索关键词变化（同一组件复用）时重新搜索并复位排序 */
watch(q, () => { sortKey.value = 'default'; sortOrder.value = 'desc'; load(); });
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
      <button :class="{ active: sortKey === 'default' }" data-sort="default" @click="onSort('default')">综合</button>
      <button :class="{ active: sortKey === 'sales' }" data-sort="sales" @click="onSort('sales')">
        销量<i v-if="sortKey === 'sales'" class="sort-dir">{{ dirArrow }}</i>
      </button>
      <button :class="{ active: sortKey === 'price' }" data-sort="price" @click="onSort('price')">
        价格<i v-if="sortKey === 'price'" class="sort-dir">{{ dirArrow }}</i>
      </button>
      <button :class="{ active: sortKey === 'rating' }" data-sort="rating" @click="onSort('rating')"
              title="按商品评分排序：后端下发商品评分时用它，未下发则回退店铺评分">
        评分<i v-if="sortKey === 'rating'" class="sort-dir">{{ dirArrow }}</i>
      </button>
      <span class="sort-spacer"></span>
      <span class="result-count" id="resultCount">共 {{ total }} 件相关商品</span>
    </div>
    <div id="searchResults" class="product-grid large" v-html="cardsHtml"></div>
  </div>
</template>
