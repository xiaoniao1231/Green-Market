<script setup>
/* =========================================================
   青集市 · views/PlaceholderView.vue —— 功能预留页（直播/会员中心/卖家入驻等）
   移植自 mall-web/js/pages/placeholder.js（页面结构 / 交互逻辑不变）
   内容完全由 route.params[0]（功能名）派生：参数变化时 computed 自动刷新，
   与原版每次挂载时 render(route) 重新取 FEATURES 的效果一致（本页无定时器/订阅，
   无需 onMounted/onBeforeUnmount 清理逻辑）。
   ========================================================= */
import { computed } from 'vue';
import useRouteCompat from '../composables/useRouteCompat.js';

const FEATURES = {
  '直播': { icon: '📺', desc: '直播间列表、主播推荐、弹幕互动与边看边买', apis: [['GET', '/live/rooms'], ['GET', '/live/rooms/{id}'], ['POST', '/live/rooms/{id}/follow']] },
  '会员中心': { icon: '👑', desc: '会员等级、积分、专属折扣与生日礼包', apis: [['GET', '/member/info'], ['GET', '/member/points'], ['POST', '/member/benefits/receive']] },
  '卖家入驻': { icon: '🏪', desc: '开店资质提交、审核进度与店铺管理（与买家共用同一用户账号）', apis: [['POST', '/merchant/apply'], ['GET', '/merchant/apply/status']] },
  '网站导航': { icon: '🧭', desc: '平台频道与合作伙伴站点导航', apis: [['GET', '/site/navigation']] },
  '新手帮助': { icon: '❓', desc: '购物流程、支付方式与常见问题帮助中心', apis: [['GET', '/help/faqs'], ['GET', '/help/articles/{id}']] },
  '品质保障': { icon: '🛡️', desc: '正品承诺、质检报告与赔付规则', apis: [['GET', '/quality/guarantee']] },
  '售后政策': { icon: '📋', desc: '退换货规则、售后流程与时效说明', apis: [['GET', '/aftersale/policies']] },
  '物流查询': { icon: '🚚', desc: '全平台物流轨迹查询（订单页已提供演示轨迹）', apis: [['GET', '/logistics/track/{orderNo}']] },
  '发票说明': { icon: '🧾', desc: '电子发票开具、抬头管理与下载', apis: [['GET', '/invoices'], ['POST', '/invoices/apply']] },
  '平台介绍': { icon: '🏢', desc: '平台介绍、发展历程与加入我们', apis: [['GET', '/site/about']] },
  '联系卖家': { icon: '💬', desc: '与开店的用户沟通商品、物流与售后', apis: [['POST', '/chat/private'], ['GET', '/chat/history']] },
  '评价晒单': { icon: '⭐', desc: '订单评价、追评与晒图', apis: [['POST', '/orders/{id}/comment'], ['GET', '/orders/{id}/comment']] },
  '浏览足迹': { icon: '👣', desc: '最近浏览商品记录与清空管理', apis: [['GET', '/footprints'], ['DELETE', '/footprints']] },
  '账户设置': { icon: '⚙', desc: '头像昵称、密码修改与账号安全', apis: [['GET', '/users/me'], ['PUT', '/users/me'], ['POST', '/password/change']] },
  '退款售后': { icon: '↩', desc: '退款申请、售后进度与赔付处理', apis: [['POST', '/aftersale/apply'], ['GET', '/aftersale/list']] }
};

const route = useRouteCompat();

/* 与原版 render 一致：无参数 → '功能开发中'；未收录功能 → 默认占位对象 */
const name = computed(() => route.value.params[0] || '功能开发中');
const feat = computed(() => FEATURES[name.value] || { icon: '🚧', desc: '该模块的界面与接口正在规划中', apis: [] });
</script>

<template>
  <div class="placeholder-page">
    <div class="placeholder-orb">{{ feat.icon }}</div>
    <span class="s-label">COMING SOON</span>
    <h1>{{ name }} · 功能预留</h1>
    <p>{{ feat.desc }}</p>
    <p class="placeholder-sub">页面入口已预留，商城后端接口落地后即可接入</p>
    <div class="placeholder-api">
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
