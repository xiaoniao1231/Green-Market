<script setup>
/* =========================================================
   青集市 · views/CategoryView.vue —— 商品分类页
   移植自 mall-web/js/pages/category.js（页面结构 / 交互逻辑不变）
   ========================================================= */
import { computed, onMounted, ref, watch } from 'vue';
import QM_UI from '../core/ui.js';
import { CATEGORIES, bySub } from '../core/catalog.js';
import QM_API from '../core/api.js';
import useRouteCompat from '../composables/useRouteCompat.js';

const { esc, productCard, emptyState, toast } = QM_UI;
const route = useRouteCompat();

const sort = ref('default');
/* 初始子类目：支持从首页左侧分类栏的二级标签带 ?sub= 跳入（没有该参数即「全部」） */
const sub = ref(route.value.query.sub || '全部');
const list = ref([]);
const total = ref(0);

const catId = computed(() => route.value.params[0] || '全部');
const cat = computed(() => CATEGORIES.find(c => c.id === catId.value));
const subs = computed(() => bySub(catId.value));

/* 侧栏一级分类的展开状态：二级类目收在一级下面，点右侧箭头收放（可多开）。
   默认只展开当前所在的分类，避免一进页面就是一条全展开的长列表 */
const openCats = ref(catId.value === '全部' ? [] : [catId.value]);
const isOpen = id => openCats.value.includes(id);
function toggleCat(id) {
  openCats.value = isOpen(id) ? openCats.value.filter(x => x !== id) : openCats.value.concat(id);
}
/* 点一级分类跳转时顺手把它展开（收起状态下点分类，子类目跟着出来） */
function expandCat(id) {
  if (!isOpen(id)) openCats.value = openCats.value.concat(id);
}

const cardsHtml = computed(() => list.value.length
  ? list.value.map(p => productCard(p)).join('')
  : emptyState('📦', '该分类暂无商品', '商品正在上架中，敬请期待'));

async function load() {
  try {
    const opts = sort.value === 'default' ? {} : { sort: sort.value };
    if (catId.value !== '全部' && sub.value !== '全部') {
      Object.assign(opts, { category: catId.value, sub: sub.value });
    } else if (catId.value !== '全部') {
      Object.assign(opts, { category: catId.value });
    }
    const data = await QM_API.products.list(Object.assign({ page: 1, size: 40 }, opts));
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

function setSub(s) {
  sub.value = s;
  load();
}

function goToSub(cat, s) {
  /* 用带 ?sub= 的地址跳转：刷新页面 / 浏览器前进后退 / 从首页二级标签跳入都能还原选中项。
     load() 交给下面的 watch 统一触发，避免同一次跳转请求两次 */
  window.location.hash = '#/category/' + encodeURIComponent(cat) + '?sub=' + encodeURIComponent(s);
}

/* 分类或子类目变化时重新加载（同一组件复用）：
   · 换分类 → 复位排序，子类目取地址里的 ?sub=（没有则「全部」）
   · 同分类内点子类目 → 只换子类目，保留当前排序 */
watch([catId, () => route.value.query.sub], ([c, s], [prevCat]) => {
  if (c !== prevCat) {
    sort.value = 'default';
    /* 换分类时展开状态跟着走：只留新分类展开，避免旧分类的子类目一直挂在列表里 */
    openCats.value = c === '全部' ? [] : [c];
  }
  sub.value = s || '全部';
  load();
});
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
        <div
          v-for="c in CATEGORIES"
          :key="c.id"
          class="cat-side-item"
          :class="{ active: c.id === catId, open: isOpen(c.id) }"
        >
          <div class="cat-side-row">
            <button data-action="goto-category" :data-id="c.id" @click="expandCat(c.id)">
              <span>{{ c.icon }}</span>{{ c.id }}
            </button>
            <button
              v-if="c.subs && c.subs.length"
              class="cat-side-toggle"
              :class="{ open: isOpen(c.id) }"
              :aria-expanded="isOpen(c.id)"
              :title="isOpen(c.id) ? '收起「' + c.id + '」的子类目' : '展开「' + c.id + '」的子类目'"
              @click.stop="toggleCat(c.id)"
            >▾</button>
          </div>
          <div v-if="c.subs && c.subs.length && isOpen(c.id)" class="cat-side-sub">
            <button
              v-for="s in c.subs"
              :key="s"
              :class="{ active: c.id === catId && sub === s }"
              @click="goToSub(c.id, s)"
            >{{ s }}</button>
          </div>
        </div>
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
