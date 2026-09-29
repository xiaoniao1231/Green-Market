<script setup>
/* =========================================================
   青集市 · views/FootprintsView.vue —— 我的浏览足迹（#/footprints）
   数据来源：QM_API.footprints.list(range)（严格走后端，不做本地离线回退）。
   契约见 docs/历史足迹接口文档.md：本模块**只有查询接口**，足迹的写入（记录浏览）
   不在前端契约内 —— 由后端在商品详情查询链路里顺带落库，前端只负责「看」。

   页面两个能力：
   ① 按天分组：同一天浏览过的商品聚成一个区块，标题为「今天 · 周X / 昨天 · 周X /
      M月D日 · 周X」，组内按最近浏览时间倒序（接口已倒序，这里只切段、不重排）；
   ② 时间筛选：全部 / 今天 / 近 7 天 / 近 30 天四个 chips，切换时带 range 重新请求
      （时间边界由服务端计算，前端不传时间戳）。
   商品卡复用 productCard()，卡片底部显示「🕒 HH:mm · 看过 N 次」。
   ========================================================= */
import { computed, onMounted, ref } from 'vue';
import QM_UI from '../core/ui.js';
import QM_API from '../core/api.js';

const { productCard, toast } = QM_UI;

/* 时间筛选枚举：与后端 GET /footprints?range= 一一对应（见接口文档 2.1.2）。
   week = 最近 7 个自然日（含今天）；month = 最近 30 个自然日（含今天）。 */
const RANGES = [
  { key: 'all', label: '全部' },
  { key: 'today', label: '今天' },
  { key: 'week', label: '近 7 天' },
  { key: 'month', label: '近 30 天' }
];

const range = ref('all');
const footList = ref([]);
const total = ref(0);
const loading = ref(false);

const pad = v => String(v).padStart(2, '0');
const WEEK_CN = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

/* 分组键：yyyy-MM-dd（浏览器本地时区）；browseTime 缺失时归入 unknown 组 */
function dayKey(ts) {
  if (!ts) return 'unknown';
  const d = new Date(ts);
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
}

/* 分组标题：今天 · 周X / 昨天 · 周X / M月D日 · 周X / yyyy年M月D日 · 周X */
function dayLabel(key) {
  if (key === 'unknown') return '时间未知';
  const parts = key.split('-').map(Number);
  const y = parts[0], m = parts[1], d = parts[2];
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const that = new Date(y, m - 1, d);
  const diffDays = Math.round((today.getTime() - that.getTime()) / 86400e3);
  const w = WEEK_CN[that.getDay()];
  if (diffDays === 0) return '今天 · ' + w;
  if (diffDays === 1) return '昨天 · ' + w;
  if (y === today.getFullYear()) return m + '月' + d + '日 · ' + w;
  return y + '年' + m + '月' + d + '日 · ' + w;
}

/* 按天分组：接口按 browseTime 倒序返回，Map 保持插入顺序 → 组间「最近的日期在前」，
   组内顺序也不变（只切段、不重排）。 */
const groups = computed(() => {
  const map = new Map();
  footList.value.forEach(p => {
    const k = dayKey(p.browseTime);
    if (!map.has(k)) map.set(k, []);
    map.get(k).push(p);
  });
  return Array.from(map, entry => ({ key: entry[0], label: dayLabel(entry[0]), items: entry[1] }));
});

/* 卡片底部的时刻行（productCard 的 extra 插槽）：分组标题已给出日期，这里只补具体时刻与浏览次数 */
function timeText(ts) {
  if (!ts) return '时间未知';
  const d = new Date(ts);
  return pad(d.getHours()) + ':' + pad(d.getMinutes());
}
const cardExtra = p => `<div class="fp-time">🕒 ${timeText(p.browseTime)}${p.browseCount > 1 ? '<small> · 看过 ' + p.browseCount + ' 次</small>' : ''}</div>`;

/* 空态：文案随当前筛选变化，避免用户误以为「一条足迹都没有」 */
const emptyHtml = computed(() => {
  const cur = RANGES.find(r => r.key === range.value) || RANGES[0];
  const tip = range.value === 'all'
    ? '你浏览过的商品会自动出现在这里，方便随时回看'
    : '「' + cur.label + '」范围内还没有浏览记录，换个时间范围试试';
  return `<div class="empty-state"><div class="empty-icon">👣</div><h3>还没有浏览记录</h3><p>${tip}</p><a class="btn btn-primary" href="#/home">去逛逛</a></div>`;
});

/* 分组区块：日期标题（左描边 + 当日件数）+ 该日商品网格 */
const listHtml = computed(() => groups.value.length
  ? groups.value.map(g => `
    <section class="fp-group">
      <div class="fp-group-head"><b>${g.label}</b><small>${g.items.length} 件</small></div>
      <div class="product-grid large">${g.items.map(p => productCard(p, cardExtra(p), '', { hideQuickCart: true })).join('')}</div>
    </section>`).join('')
  : emptyHtml.value);

/* 拉列表：失败如实提示并保持空态（足迹是服务端数据，不能静默假装「没有足迹」）。
   固定 page=1&size=200：每账号足迹上限就是 200 条，一次取满即可，无需分页 UI。 */
let loadingList = false;
async function renderList() {
  if (loadingList) return;
  loadingList = true;
  loading.value = true;
  try {
    const data = await QM_API.footprints.list(range.value, 1, 200);
    footList.value = (data && data.list) || [];
    total.value = (data && data.total) || footList.value.length;
  } catch (e) {
    footList.value = [];
    total.value = 0;
    toast((e && e.message) || '浏览足迹加载失败', 'error');
  } finally {
    loadingList = false;
    loading.value = false;
  }
}

/* 切换时间筛选：带新的 range 重新请求（不做本地过滤 —— 以服务端筛选结果与 total 为准） */
function setRange(key) {
  if (range.value === key) return;
  range.value = key;
  footList.value = [];
  renderList();
}

onMounted(renderList);
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 我的足迹</div>
        <h1>浏览足迹 <small id="fpCount">共 {{ total }} 件</small></h1>
      </div>
    </div>
    <div class="fav-toolbar fp-filter">
      <span class="pill pill-orange">👣 按天查看</span>
      <div class="fp-chips">
        <button
          v-for="r in RANGES"
          :key="r.key"
          type="button"
          class="fp-chip"
          :class="{ active: range === r.key }"
          :data-range="r.key"
          :disabled="loading"
          @click="setRange(r.key)"
        >{{ r.label }}</button>
      </div>
      <small class="fp-tip">同一商品重复浏览只保留一条记录，并刷新为最近浏览时间</small>
    </div>
    <div id="fpList" v-html="listHtml"></div>
  </div>
</template>
