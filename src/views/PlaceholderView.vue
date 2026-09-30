<script setup>
/* =========================================================
   青集市 · views/PlaceholderView.vue —— 功能预留页（只收录后端尚未实现的功能）
   这里只保留「售后服务」一个尚未落地的入口（web03 暂无售后接口，前端 #/after-sales
   页面已就绪）；已经上线的功能一律不在此挂占位条目，旧地址由下面的 MOVED 映射
   直接回到真实页面，避免功能做完了页面还写着「功能预留」。
   内容完全由 route.params[0]（功能名）派生，参数变化时 computed 自动刷新。
   ========================================================= */
import { computed, watch } from 'vue';
import { useRouter } from 'vue-router';
import useRouteCompat from '../composables/useRouteCompat.js';

/* 已上线功能的历史入口 → 真实页面（#/placeholder/<功能名> 不再展示占位内容） */
const MOVED = {
  '卖家入驻': '/seller',      // 开店 / 店铺资料：POST /shops、PUT /shops/profile
  '物流查询': '/orders',      // 物流轨迹：GET /orders/{orderId}/logistics（订单详情内展示）
  '联系卖家': '/chat'         // 私聊消息：POST /messages/private 等
};

const FEATURES = {
  /* 售后服务：前端页面（#/after-sales）已就绪，但 web03 尚未提供售后接口，故保留占位 */
  '售后服务': { icon: '📋', desc: '退款申请、退换货规则、售后流程与时效说明', apis: [['GET', '/aftersale/policies'], ['POST', '/aftersale/apply'], ['GET', '/aftersale/list']] },
  /* 平台介绍：内容全部放在前端（纯静态文案），不依赖后端接口，属于已上线页面 */
  '平台介绍': {
    icon: '🏢',
    desc: '青集市是什么、能做什么',
    content: [
      { h: '关于青集市', p: '青集市是一个仿 QQ 商城的课程演示项目：同一个账号既能买东西，也能开店卖东西，覆盖商品浏览、下单、支付、发货、收货与售后的完整链路。' },
      { h: '技术构成', p: '前端 Vue 3 + Vite（单页应用，hash 路由）；后端 Spring Boot + MyBatis + MySQL，接口按 RESTful 风格提供，登录鉴权使用 JWT；聊天通过 WebSocket 实时推送。' },
      { h: '已经能做的事', p: '商品浏览 / 搜索 / 分类筛选、店铺主页、购物车与结算下单、订单与物流、收藏与地址簿、商品与订单独家管理、以及买卖双方的一对一实时聊天。' },
      { h: '说明', p: '本项目仅用于课程演示与学习交流，商品、店铺、订单等数据均来自自建后端数据库，不涉及真实交易。' }
    ]
  }
};

const route = useRouteCompat();
const router = useRouter();

/* 与原版 render 一致：无参数 → '功能开发中'；未收录功能 → 默认占位对象 */
const name = computed(() => route.value.params[0] || '功能开发中');
const feat = computed(() => {
  const f = FEATURES[name.value] || { icon: '🚧', desc: '该模块的界面与接口正在规划中' };
  return Object.assign({ apis: [] }, f);
});

/* 命中已上线功能的历史地址时直接回真实页面（放在这里而不是路由表，便于与 MOVED 一起维护） */
watch(name, v => {
  const to = MOVED[v];
  /* replace 返回的 Promise 在导航被守卫改写（如未登录跳登录页）时可能 reject，忽略即可 */
  if (to) router.replace(to).catch(() => {});
}, { immediate: true });
</script>

<template>
  <div class="placeholder-page">
    <div class="placeholder-orb">{{ feat.icon }}</div>
    <!-- 已上线的静态页（如「平台介绍」）不再标注「功能预留 / COMING SOON」 -->
    <span v-if="!feat.content" class="s-label">COMING SOON</span>
    <h1>{{ name }}<template v-if="!feat.content"> · 功能预留</template></h1>
    <p>{{ feat.desc }}</p>
    <!-- 纯前端内容（如「平台介绍」）：直接渲染内置文案，不列后端接口 -->
    <div v-if="feat.content" class="placeholder-content">
      <section v-for="c in feat.content" :key="c.h">
        <h4>{{ c.h }}</h4>
        <p>{{ c.p }}</p>
      </section>
    </div>
    <p v-else class="placeholder-sub">页面入口已预留，功能即将上线</p>
    <div v-if="!feat.content" class="placeholder-api">
      <h4>📄 规划接口（详见《商城前端接口文档》）</h4>
      <ul v-if="feat.apis.length">
        <li v-for="a in feat.apis" :key="a[0] + ' ' + a[1]"><code>{{ a[0] }}</code>{{ a[1] }}</li>
      </ul>
      <ul v-else><li>接口规划中，将补充到接口文档</li></ul>
    </div>
    <div class="placeholder-actions">
      <a class="btn btn-primary btn-lg" href="#/home">返回首页</a>
      <a class="btn btn-plain btn-lg" href="#/chat">联系卖家</a>
    </div>
  </div>
</template>
