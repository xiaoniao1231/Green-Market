<script setup>
/* =========================================================
   青集市 · views/FootprintsView.vue —— 我的浏览足迹（#/footprints）
   数据来源：QM_API.footprints.list()（严格走后端，不做本地离线回退）；
   写入由商品详情页加载成功后调用 POST /footprints 上报（见 DetailView.vue），本页只负责「看」。

   本页固定 range=all&page=1&size=200 一次取满（每账号足迹上限 200 条），
   「全部 / 今天 / 近 7 天 / 近 30 天」的时间筛选在**本地**完成（切换 chips 零请求）。
   分组与筛选的纯函数集中在 core/footprintTime.js，可单测：scripts/test-footprints.mjs。

   商品卡只展示商品信息本身：日期由分组标题给出，卡内不显示时间与浏览次数。
   ========================================================= */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import QM_UI from '../core/ui.js';
import QM_API from '../core/api.js';
import { RANGE_OPTIONS, filterByRange, groupByDay } from '../core/footprintTime.js';

const { productCard, toast } = QM_UI;

const range = ref('all');
const footList = ref([]);        // 全量足迹（≤ 200 条）
const loading = ref(false);      // 请求中：chips 禁用，防连点与防重入
const loaded = ref(false);       // 首次请求已结束：此前渲染「加载中」，避免闪一下空态
const failed = ref(false);       // 首次请求失败：渲染失败态而不是「还没有浏览记录」
const nowTick = ref(Date.now()); // 每 60 秒推进一次：跨零点后「今天 / 昨天」与筛选边界自动重算

const filteredList = computed(() => filterByRange(footList.value, range.value, nowTick.value));
const groups = computed(() => groupByDay(filteredList.value, nowTick.value));
const total = computed(() => filteredList.value.length);

/* 分组区块：日期标题（当日件数）+ 当日商品网格；卡内不显示时间 / 浏览次数 */
const listHtml = computed(() => {
  if (!loaded.value) return '<div class="empty-state"><div class="empty-icon">👣</div><h3>正在加载…</h3></div>';
  if (failed.value) return '<div class="empty-state"><div class="empty-icon">⚠️</div><h3>浏览足迹加载失败</h3><p>请稍后重试</p><a class="btn btn-primary" href="#/home">去逛逛</a></div>';
  if (!groups.value.length) {
    /* 空态：只有「筛选后为空」才补一句说明；「全部」为空即从未浏览过，不再赘述 */
    const cur = RANGE_OPTIONS.find(r => r.key === range.value) || RANGE_OPTIONS[0];
    const tip = range.value === 'all' ? '' : `<p>「${cur.label}」范围内没有记录</p>`;
    return `<div class="empty-state"><div class="empty-icon">👣</div><h3>还没有浏览记录</h3>${tip}<a class="btn btn-primary" href="#/home">去逛逛</a></div>`;
  }
  return groups.value.map(g => `
    <section class="fp-group">
      <div class="fp-group-head"><b>${g.label}</b><small>${g.items.length} 件</small></div>
      <div class="product-grid large">${g.items.map(p => productCard(p, null, '', { hideQuickCart: true })).join('')}</div>
    </section>`).join('');
});

async function loadFootprints() {
  if (loading.value) return;
  loading.value = true;
  try {
    const data = await QM_API.footprints.list();
    footList.value = (data && data.list) || [];
    failed.value = false;
  } catch (e) {
    footList.value = [];
    failed.value = true;
    toast((e && e.message) || '浏览足迹加载失败', 'error');
  } finally {
    loading.value = false;
    loaded.value = true;   // 成功 / 失败都算「已结束」，加载态不会与空态并存
  }
}

/* 切换时间筛选：本地过滤，不重新请求（全量已在手，切换零延迟） */
function setRange(key) {
  if (range.value === key) return;
  range.value = key;
}

let dayTimer = null;
onMounted(() => {
  loadFootprints();
  /* 跨零点自愈：分组标题与筛选边界都依赖「当前自然日」，页面长时间挂着过午夜后
     computed 不会自动重算，这里每分钟推进一次 nowTick（60 秒粒度足够）。 */
  dayTimer = setInterval(() => { nowTick.value = Date.now(); }, 60000);
});
onBeforeUnmount(() => { if (dayTimer) clearInterval(dayTimer); });
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 我的足迹</div>
        <h1>浏览足迹 <small id="fpCount">共 {{ total }} 件</small></h1>
      </div>
    </div>
    <div class="fav-toolbar">
      <div class="fp-chips">
        <button
          v-for="r in RANGE_OPTIONS"
          :key="r.key"
          type="button"
          class="fp-chip"
          :class="{ active: range === r.key }"
          :data-range="r.key"
          :disabled="loading"
          @click="setRange(r.key)"
        >{{ r.label }}</button>
      </div>
    </div>
    <div id="fpList" v-html="listHtml"></div>
  </div>
</template>
