/* =========================================================
   青集市 · core/viewRefresh.js —— 视图刷新桥
   App.vue 挂载时注册 refresh 函数（给 <router-view> 更换 key 强制重挂载
   当前页面组件），供 ui.js 登录成功后调用，等价原版 QM_ROUTER.refresh()。
   ========================================================= */
let refreshFn = null;

export function registerViewRefresh(fn) { refreshFn = fn; }

export function refreshView() { if (refreshFn) refreshFn(); }
