<script setup>
/* =========================================================
   青集市 · views/FavoritesView.vue —— 我的收藏
   移植自 mall-web/js/pages/favorites.js（页面结构 / 交互逻辑不变）
   数据来源：QM_API.favorites.list()（严格走后端，不做本地离线回退）。
   商品卡由 productCard() 生成（data-action 走 App.vue 全局代理），
   页面自身实现「清空收藏」（confirmDialog 确认后调接口清空并重渲染）。
   每张商品卡底部追加「取消收藏」按钮：正常 / 已下架 / 已删除商品都可
   单条取消（已删除商品点击卡片会跳详情但详情页商品不存在，取消只能走这里）；
   订阅 favorites 事件，取消收藏 / 清空后自动刷新列表。
   ========================================================= */
import { computed, onMounted, onUnmounted, ref } from 'vue';
import QM_UI from '../core/ui.js';
import QM_API from '../core/api.js';
import QM_STORE from '../core/store.js';

const { productCard, toast, confirmDialog, esc } = QM_UI;

const favList = ref([]);
let offFav = null;

/* 商品卡店铺行的操作按钮：单条取消收藏（与「＋购物车」同一行；
   对已删除 / 已下架商品同样可用，此时加购按钮隐藏、取消收藏仍保留） */
const cardActions = p => `<span class="btn btn-plain" data-action="remove-fav" data-id="${esc(p.id)}">取消收藏</span>`;

/* #favList 内容：有收藏 → 商品卡；无收藏 → 原版空状态（♡，含去逛逛链接 #/home）。
   收藏页只保留「取消收藏」：hideQuickCart 让收藏页商品卡不渲染「＋购物车」按钮，
   加购需点商品卡进详情页操作 */
const listHtml = computed(() => favList.value.length
  ? favList.value.map(p => productCard(p, null, cardActions(p), { hideQuickCart: true })).join('')
  : '<div class="empty-state"><div class="empty-icon">♡</div><h3>还没有收藏的商品</h3><p>点击商品详情页的「收藏」按钮，喜欢的商品会出现在这里</p><a class="btn btn-primary" href="#/home">去逛逛</a></div>');

/* 原版 renderList()：按接口返回重渲染列表 / 计数。
   接口失败时如实提示并保持空态 —— 收藏是服务端数据，不能静默假装「没有收藏」。
   注意：api.js 的 favorites.list() 成功后也会 emit('favorites')（通知其他组件同步收藏数），
   而本页订阅了该事件 → 不加保护会形成「拉列表 → emit → 再拉列表」的无限循环。
   用 loadingList 防重入：请求挂起期间再次触发直接忽略（切断循环），
   请求结束后的触发（如 remove 成功）正常刷新列表。 */
let loadingList = false;
async function renderList() {
  if (loadingList) return;
  loadingList = true;
  try {
    const data = await QM_API.favorites.list();
    favList.value = (data && data.list) || [];
  } catch (e) {
    favList.value = [];
    toast((e && e.message) || '收藏列表加载失败', 'error');
  } finally {
    loadingList = false;
  }
}

async function clearAll() {
  if (await confirmDialog('清空收藏', '确定清空所有收藏商品吗？', '清空', true)) {
    try {
      await QM_API.favorites.clear();
      toast('收藏已清空');
    } catch (e) {
      toast((e && e.message) || '清空收藏失败，请稍后重试', 'error');
    }
    renderList();
  }
}

onMounted(() => {
  renderList();
  /* api.js 的 add / remove / clear 成功后会 emit('favorites')，这里订阅自动刷新 */
  offFav = QM_STORE.on('favorites', renderList);
});
onUnmounted(() => { if (offFav) offFav(); });
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
      <span class="pill pill-orange">收藏夹</span>
    </div>
    <div id="favList" class="product-grid large" v-html="listHtml"></div>
  </div>
</template>
