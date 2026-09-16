<script setup>
/* =========================================================
   青集市 · views/FavoritesView.vue —— 我的收藏
   移植自 mall-web/js/pages/favorites.js（页面结构 / 交互逻辑不变）
   数据来源：QM_API.favorites.list()（后端实现后为真实数据；
   未实现时 api.js 自动回退本地演示数据，页面无感切换）。
   商品卡由 productCard() 生成（data-action 走 App.vue 全局代理），
   页面自身实现「清空收藏」（confirmDialog 确认后调接口清空并重渲染）。
   ========================================================= */
import { computed, onMounted, ref } from 'vue';
import QM_UI from '../core/ui.js';
import QM_API from '../core/api.js';

const { productCard, toast, confirmDialog } = QM_UI;

const favList = ref([]);

/* #favList 内容：有收藏 → 商品卡；无收藏 → 原版空状态（♡，含去逛逛链接 #/home） */
const listHtml = computed(() => favList.value.length
  ? favList.value.map(p => productCard(p)).join('')
  : '<div class="empty-state"><div class="empty-icon">♡</div><h3>还没有收藏的商品</h3><p>点击商品详情页的「收藏」按钮，喜欢的商品会出现在这里</p><a class="btn btn-primary" href="#/home">去逛逛</a></div>');

/* 原版 renderList()：按接口返回重渲染列表 / 计数（接口失败时兜底本地收藏） */
async function renderList() {
  try {
    const data = await QM_API.favorites.list();
    favList.value = (data && data.list) || [];
  } catch (e) {
    favList.value = [];
  }
}

async function clearAll() {
  if (await confirmDialog('清空收藏', '确定清空所有收藏商品吗？', '清空', true)) {
    await QM_API.favorites.clear();
    toast('收藏已清空');
    renderList();
  }
}

onMounted(renderList);
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 我的收藏</div>
        <h1>我的收藏 <small id="favCount">共 {{ favList.length }} 件</small></h1>
      </div>
      <button class="btn btn-plain" id="favClear" v-show="favList.length" @click="clearAll">清空收藏</button>
    </div>
    <div class="fav-toolbar">
      <span class="pill pill-orange">收藏夹（本地演示）</span>
      <span class="pill pill-gray">后端 /favorites 接口落地后自动切换真实数据</span>
    </div>
    <div id="favList" class="product-grid large" v-html="listHtml"></div>
  </div>
</template>
