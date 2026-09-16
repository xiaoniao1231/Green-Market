<script setup>
/* =========================================================
   青集市 · views/SellerProductsView.vue —— 我的店铺 · 商品管理（演示）
   本店铺商品列表：支持上架 / 下架、修改售价
   状态存于本地 store.sellerProducts（后端 + 数据库待接入）
   ========================================================= */
import { computed, onMounted, ref } from 'vue';
import QM_UI from '../core/ui.js';
import QM_STORE from '../core/store.js';

const { artStyle, artHtml, toast } = QM_UI;

const shopId = computed(() => (QM_STORE.seller.current() ? QM_STORE.seller.current().id : ''));
const svc = computed(() => QM_STORE.seller.current());

/* 商品列表（合并上下架 / 改价状态），手动刷新（store 非响应式） */
const list = ref([]);

function refresh() {
  if (!shopId.value) return;
  list.value = QM_STORE.seller.products(shopId.value);
}

function toggle(p) {
  QM_STORE.seller.setProduct(p.id, { onSale: p.onSale === false ? true : false });
  refresh();
  toast(p.onSale === false ? '已上架' : '已下架', 'success');
}

function onPrice(p, e) {
  const v = Math.round(Number(e.target.value));
  if (!v || v < 1 || v > 99999) { e.target.value = p.price; return toast('请输入有效价格', 'error'); }
  QM_STORE.seller.setProduct(p.id, { price: v });
  e.target.value = v;
  refresh();
  toast('价格已更新', 'success');
}

onMounted(() => { refresh(); });
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 我的店铺 / 商品管理</div>
        <h1>商品管理 <small>PRODUCTS</small></h1>
      </div>
      <a class="btn btn-plain" href="#/seller">返回我的店铺</a>
    </div>

    <template v-if="svc">
      <div class="seller-tip">💡 当前店铺：<b>{{ svc.shopName }}</b>，共 {{ list.length }} 件商品。修改会保存在本地演示数据中，后端与数据库接入后可持久化。</div>

      <div v-for="p in list" :key="p.id" class="seller-product-row">
        <span class="p-art" :style="artStyle(p.art)" v-html="artHtml(p.art)"></span>
        <div class="p-info">
          <h5>{{ p.title }}</h5>
          <small>销量 {{ p.sales }} · 库存 {{ p.stock }} · {{ p.category }} {{ p.sub }}</small>
        </div>
        <input class="p-price" type="number" :value="p.price" @change="onPrice(p, $event)" title="点击修改价格" />
        <span class="p-status" :class="p.onSale === false ? 'off' : 'on'">{{ p.onSale === false ? '已下架' : '在售' }}</span>
        <button class="btn btn-plain" @click="toggle(p)">{{ p.onSale === false ? '上架' : '下架' }}</button>
      </div>
    </template>

    <!-- 已登录但未开店 -->
    <div v-else-if="QM_STORE.state.user" class="empty-state">
      <div class="empty-icon">🏪</div>
      <h3>你还没有店铺</h3>
      <p>当前账号「{{ QM_STORE.state.user.nickname }}」名下还没有店铺。使用已开店的演示账号登录（如 owner001 / 123456）即可管理商品。</p>
      <a class="btn btn-plain" href="#/home">先逛逛</a>
    </div>

    <!-- 未登录 -->
    <div v-else class="empty-state">
      <div class="empty-icon">🔐</div>
      <h3>请先登录</h3>
      <p>全站只有一套用户账号，登录后即可管理自己店铺的商品。</p>
      <a class="btn btn-primary" href="#/login?redirect=%2Fseller%2Fproducts">去登录</a>
    </div>
  </div>
</template>
