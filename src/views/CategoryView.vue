<script setup>
/* =========================================================
   青集市 · views/CategoryView.vue —— 商品分类页
   移植自 mall-web/js/pages/category.js（页面结构 / 交互逻辑不变）
   ========================================================= */
import { computed, onMounted, ref, watch } from 'vue';
import QM_UI from '../core/ui.js';
import { CATEGORIES } from '../core/catalog.js';
import QM_API from '../core/api.js';
import useRouteCompat from '../composables/useRouteCompat.js';

const { esc, productCard, emptyState, toast } = QM_UI;
const route = useRouteCompat();

const sort = ref('default');
const list = ref([]);
const total = ref(0);

const catId = computed(() => route.value.params[0] || '全部');
const cat = computed(() => CATEGORIES.find(c => c.id === catId.value));

const cardsHtml = computed(() => list.value.length
  ? list.value.map(p => productCard(p)).join('')
  : emptyState('📦', '该分类暂无商品', '商品正在上架中，敬请期待'));

async function load() {
  try {
    const opts = sort.value === 'default' ? {} : { sort: sort.value };
    const data = catId.value === '全部'
      ? await QM_API.products.list(Object.assign({ page: 1, size: 40 }, opts))
      : await QM_API.products.list(Object.assign({ page: 1, size: 40, category: catId.value }, opts));
    /* 后端返回 {code:1,data:null} 时 data 为 null：直接取 data.list 会抛 TypeError 导致整页空白 */
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

/* 切换分类（同一组件复用）时重新加载并复位排序 */
watch(catId, () => { sort.value = 'default'; load(); });
onMounted(load);
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 商品分类 <b>/ {{ catId }}</b></div>
        <h1>{{ catId === '全部' ? '全部好物' : catId }}</h1>
      </div>
    </div>
    <div class="category-layout">
      <aside class="cat-side">
        <h3>全部分类</h3>
        <button :class="{ active: catId === '全部' }" data-action="goto-category" data-id="全部">▦ 全部好物</button>
        <button
          v-for="c in CATEGORIES"
          :key="c.id"
          :class="{ active: c.id === catId }"
          data-action="goto-category"
          :data-id="c.id"
        >
          <span>{{ c.icon }}</span>{{ c.id }}
        </button>
      </aside>
      <div class="cat-main">
        <div class="cat-banner">
          <p>青集市 · 发现日常的美好</p>
          <h1>{{ cat ? cat.id : '全部好物' }}</h1>
          <span>{{ cat ? cat.subs.join(' · ') : '精选品质商品，每一天都有新发现' }}</span>
        </div>
        <div class="sort-bar" id="sortBar">
          <button :class="{ active: sort === 'default' }" data-sort="default" @click="setSort('default')">综合</button>
          <button :class="{ active: sort === 'sales' }" data-sort="sales" @click="setSort('sales')">销量</button>
          <button :class="{ active: sort === 'priceAsc' }" data-sort="priceAsc" @click="setSort('priceAsc')">价格 ↑</button>
          <button :class="{ active: sort === 'priceDesc' }" data-sort="priceDesc" @click="setSort('priceDesc')">价格 ↓</button>
          <span class="sort-spacer"></span>
          <span class="result-count" id="resultCount">共 {{ total }} 件商品</span>
        </div>
        <div id="catProducts" class="product-grid large" v-html="cardsHtml"></div>
      </div>
    </div>
  </div>
</template>
