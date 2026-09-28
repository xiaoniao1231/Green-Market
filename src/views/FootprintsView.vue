<script setup>
/* =========================================================
   青集市 · views/FootprintsView.vue —— 我的浏览足迹（#/footprints）
   数据来源：QM_API.footprints.list()（严格走后端，不做本地离线回退）。
   契约见 docs/历史足迹接口文档.md；足迹由商品详情页浏览时静默上报
   （DetailView.vue → QM_API.footprints.record，失败不影响浏览）。
   商品卡由 productCard() 生成（点击卡片进详情，data-action 走 App.vue 全局代理），
   卡片底部追加「删除足迹」（单条删除），页头提供「清空足迹」（二次确认）。
   同一商品只保留一条足迹，重复浏览只累加次数并刷新时间 —— 列表按最近浏览倒序。
   ========================================================= */
import { computed, onMounted, ref } from 'vue';
import QM_UI from '../core/ui.js';
import QM_API from '../core/api.js';

const { productCard, toast, confirmDialog } = QM_UI;

const footList = ref([]);

/* 浏览时间友好化：刚刚 / N 分钟前 / N 小时前 / 昨天 HH:mm / MM-DD HH:mm / yyyy-MM-dd HH:mm。
   足迹的价值就在「最近看过什么」，相对时间比绝对时间更直观（悬停标题仍给出完整时间）。 */
function relTime(ts) {
  if (!ts) return '时间未知';
  const d = new Date(ts);
  const pad = v => String(v).padStart(2, '0');
  const hm = pad(d.getHours()) + ':' + pad(d.getMinutes());
  const diff = Date.now() - ts;
  if (diff < 60e3) return '刚刚';
  if (diff < 3600e3) return Math.floor(diff / 60e3) + ' 分钟前';
  if (diff < 86400e3) return Math.floor(diff / 3600e3) + ' 小时前';
  const todayStart = new Date(); todayStart.setHours(0, 0, 0, 0);
  if (ts >= todayStart.getTime() - 86400e3) return '昨天 ' + hm;
  if (d.getFullYear() === new Date().getFullYear()) return (d.getMonth() + 1) + '-' + d.getDate() + ' ' + hm;
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + hm;
}

/* 卡片底部的时间行（productCard 的 extra 插槽，渲染在 .pc-info 之后） */
const cardExtra = p => `<div class="fp-time" title="最近浏览：${(p.browseTime ? new Date(p.browseTime).toLocaleString() : '未知')}">👣 最近浏览 ${relTime(p.browseTime)}<small>${p.browseCount > 1 ? ' · 看过 ' + p.browseCount + ' 次' : ''}</small></div>`;

/* 店铺行里的操作按钮：单条删除足迹（与收藏页「取消收藏」同一位置 / 同一交互） */
const cardActions = p => `<span class="btn btn-plain" data-action="remove-footprint" data-id="${p.id}">删除</span>`;

/* 有足迹 → 商品卡；没有 → 空状态（去逛逛）。
   商品卡隐藏「＋购物车」快捷按钮：足迹页的主操作是「回看 / 删除」，
   加购需点进详情页确认款式（与收藏页一致）。 */
const listHtml = computed(() => footList.value.length
  ? footList.value.map(p => productCard(p, cardExtra(p), cardActions(p), { hideQuickCart: true })).join('')
  : '<div class="empty-state"><div class="empty-icon">👣</div><h3>还没有浏览记录</h3><p>你浏览过的商品会自动出现在这里，方便随时回看</p><a class="btn btn-primary" href="#/home">去逛逛</a></div>');

/* 拉列表：失败如实提示并保持空态（足迹是服务端数据，不能静默假装「没有足迹」） */
let loadingList = false;
async function renderList() {
  if (loadingList) return;
  loadingList = true;
  try {
    const data = await QM_API.footprints.list(1, 100);
    footList.value = (data && data.list) || [];
  } catch (e) {
    footList.value = [];
    toast((e && e.message) || '浏览足迹加载失败', 'error');
  } finally {
    loadingList = false;
  }
}

async function clearAll() {
  if (await confirmDialog('清空足迹', '确定清空全部浏览足迹吗？清空后无法恢复。', '清空', true)) {
    try {
      await QM_API.footprints.clear();
      toast('浏览足迹已清空');
    } catch (e) {
      toast((e && e.message) || '清空足迹失败，请稍后重试', 'error');
    }
    renderList();
  }
}

onMounted(renderList);
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 我的足迹</div>
        <h1>浏览足迹 <small id="fpCount">共 {{ footList.length }} 件</small></h1>
      </div>
      <button class="btn btn-plain" id="fpClear" v-show="footList.length" @click="clearAll">清空足迹</button>
    </div>
    <div class="fav-toolbar">
      <span class="pill pill-orange">👣 最近浏览</span>
      <small class="fp-tip">同一商品重复浏览只保留一条记录，并刷新为最近浏览时间</small>
    </div>
    <div id="fpList" class="product-grid large" v-html="listHtml"></div>
  </div>
</template>
