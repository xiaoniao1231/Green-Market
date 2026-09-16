import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import App from './App.vue';
import router from './router/index.js';
import QM_STORE from './core/store.js';
import QM_API from './core/api.js';
import QM_CFG from './core/config.js';

// 页面样式（原 mall-web 六份 CSS 原样引入，保证视觉与交互不变）
import './assets/css/base.css';
import './assets/css/layout.css';
import './assets/css/components.css';
import './assets/css/home.css';
import './assets/css/pages.css';
import './assets/css/chat.css';
import './assets/css/seller.css';
import './assets/css/shop.css';

/* 原版 app.js 在启动时 QM_STORE.load()。这里提前到挂载前执行：
   子页面组件（首页等）在 App 的 onMounted 之前就会 setup 并读取 QM_STORE.state，
   必须先保证 state 已初始化（App.vue 的 onMounted 里不再重复 load）。 */
QM_STORE.load();

const app = createApp(App);
app.use(router);
app.use(ElementPlus);
app.mount('#app');

/* 调试钩子：仅在开发模式或地址栏显式带 ?debug=1 时启用，便于在浏览器控制台与
   自动化脚本（scripts/test-chat-e2e.mjs）中直接调用接口、检查本地状态。
   · ?debug=1        暴露 window.__QM__（api/store/router/app）
   · ?debug=1&api=…  额外把接口基址切到指定后端（如 http://localhost:8081），
                     用于在不动 nginx 反代的前提下定向验证某个后端实例。
   正常访问（无 debug 参数）不会挂载任何全局对象、也不会改写接口基址。 */
try {
  const params = new URLSearchParams(location.search);
  const debugOn = import.meta.env.DEV || params.get('debug') === '1';
  if (debugOn) {
    const apiOverride = params.get('api');
    if (apiOverride) QM_CFG.API_BASE = apiOverride;
    window.__QM__ = { api: QM_API, store: QM_STORE, router, app, cfg: QM_CFG };
  }
} catch (e) { /* 忽略：调试钩子失败不影响正常使用 */ }
