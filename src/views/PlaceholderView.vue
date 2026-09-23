<script setup>
/* =========================================================
   青集市 · views/PlaceholderView.vue —— 功能预留页（售后服务 / 物流查询 / 平台介绍等）
   移植自 mall-web/js/pages/placeholder.js（页面结构 / 交互逻辑不变）
   内容完全由 route.params[0]（功能名）派生：参数变化时 computed 自动刷新，
   与原版每次挂载时 render(route) 重新取 FEATURES 的效果一致（本页无定时器/订阅，
   无需 onMounted/onBeforeUnmount 清理逻辑）。
   ========================================================= */
import { computed } from 'vue';
import useRouteCompat from '../composables/useRouteCompat.js';

const FEATURES = {
  '卖家入驻': { icon: '🏪', desc: '开店资质提交、审核进度与店铺管理（与买家共用同一用户账号）', apis: [['POST', '/merchant/apply'], ['GET', '/merchant/apply/status']] },
  '网站导航': { icon: '🧭', desc: '平台频道与合作伙伴站点导航', apis: [['GET', '/site/navigation']] },
  /* 售后服务：原「退款售后」入口已并入此处 —— 同一件事不该在个人中心出现两个名字。
     接口规划含规则查询 + 退款申请 + 售后列表 */
  '售后服务': { icon: '📋', desc: '退款申请、退换货规则、售后流程与时效说明', apis: [['GET', '/aftersale/policies'], ['POST', '/aftersale/apply'], ['GET', '/aftersale/list']] },
  '物流查询': { icon: '🚚', desc: '全平台物流轨迹查询（订单页已提供演示轨迹）', apis: [['GET', '/logistics/track/{orderNo}']] },
  /* 平台介绍：内容全部放在前端（纯静态文案），不依赖后端接口 */
  '平台介绍': {
    icon: '🏢',
    desc: '青集市是什么、能做什么',
    frontend: true,
    content: [
      { h: '关于青集市', p: '青集市是一个仿 QQ 商城的课程演示项目：同一个账号既能买东西，也能开店卖东西，覆盖商品浏览、下单、支付、发货、收货与售后的完整链路。' },
      { h: '技术构成', p: '前端 Vue 3 + Vite（单页应用，hash 路由）；后端 Spring Boot + MyBatis + MySQL，接口按 RESTful 风格提供，登录鉴权使用 JWT；聊天通过 WebSocket 实时推送。' },
      { h: '已经能做的事', p: '商品浏览 / 搜索 / 分类筛选、店铺主页、购物车与结算下单、订单与物流、收藏与地址簿、商品与订单独家管理、以及买卖双方的一对一实时聊天。' },
      { h: '说明', p: '本项目仅用于课程演示与学习交流，商品、店铺、订单等数据均来自自建后端数据库，不涉及真实交易。' }
    ]
  },
  '联系卖家': { icon: '💬', desc: '与开店的用户沟通商品、物流与售后', apis: [['POST', '/chat/private'], ['GET', '/chat/history']] },
  '评价晒单': { icon: '⭐', desc: '订单评价、追评与晒图', apis: [['POST', '/orders/{id}/comment'], ['GET', '/orders/{id}/comment']] },
  '浏览足迹': { icon: '👣', desc: '最近浏览商品记录与清空管理', apis: [['GET', '/footprints'], ['DELETE', '/footprints']] },
  '账户设置': { icon: '⚙', desc: '头像昵称、密码修改与账号安全', apis: [['GET', '/users/me'], ['PUT', '/users/me'], ['POST', '/password/change']] }
};

const route = useRouteCompat();

/* 与原版 render 一致：无参数 → '功能开发中'；未收录功能 → 默认占位对象 */
const name = computed(() => route.value.params[0] || '功能开发中');
const feat = computed(() => {
  const f = FEATURES[name.value] || { icon: '🚧', desc: '该模块的界面与接口正在规划中' };
  return Object.assign({ apis: [] }, f);
});
</script>

<template>
  <div class="placeholder-page">
    <div class="placeholder-orb">{{ feat.icon }}</div>
    <span class="s-label">COMING SOON</span>
    <h1>{{ name }} · 功能预留</h1>
    <p>{{ feat.desc }}</p>
    <!-- 纯前端内容（如「平台介绍」）：直接渲染内置文案，不列后端接口 -->
    <div v-if="feat.content" class="placeholder-content">
      <section v-for="c in feat.content" :key="c.h">
        <h4>{{ c.h }}</h4>
        <p>{{ c.p }}</p>
      </section>
    </div>
    <p v-else class="placeholder-sub">页面入口已预留，商城后端接口落地后即可接入</p>
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
