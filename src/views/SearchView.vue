<script setup>
/* =========================================================
   青集市 · views/SearchView.vue —— 搜索结果页
   移植自 mall-web/js/pages/search.js（页面结构 / 交互逻辑不变）
   ========================================================= */
import { computed, onMounted, ref, watch } from 'vue';
import QM_UI from '../core/ui.js';
import QM_API from '../core/api.js';
import useRouteCompat from '../composables/useRouteCompat.js';

const { esc, productCard, emptyState } = QM_UI;
const route = useRouteCompat();

const sort = ref('default');
const list = ref([]);
const total = ref(0);

const q = computed(() => route.value.query.q || '');

const cardsHtml = computed(() => list.value.length
  ? list.value.map(p => productCard(p)).join('')
  : emptyState('🔍', '没有找到相关商品', '换个关键词试试，或去首页逛逛推荐好物'));

async function load() {
  const data = await QM_API.products.search(q.value, Object.assign({ page: 1, size: 40 }, sort.value === 'default' ? {} : { sort: sort.value }));
  /* 后端返回 {code:1,data:null} 时 data.list 会抛 TypeError，导致搜索结果页整页空白 */
  list.value = (data && data.list) || [];
  total.value = (data && data.total) || 0;
}

function setSort(key) {
  sort.value = key;
  load();
}

/* 搜索关键词变化（同一组件复用）时重新搜索并复位排序 */
watch(q, () => { sort.value = 'default'; load(); });
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
      <span class="pill pill-orange">包邮</span>
      <span class="pill pill-red">限时折扣</span>
      <span class="pill pill-green">次日达</span>
      <span class="pill pill-gray">筛选条件即将开放</span>
    </div>
    <div id="searchResults" class="product-grid large" v-html="cardsHtml"></div>
  </div>
</template>
