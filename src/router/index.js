import { createRouter, createWebHashHistory } from 'vue-router';

import HomeView from '../views/HomeView.vue';
import CategoryView from '../views/CategoryView.vue';
import SearchView from '../views/SearchView.vue';
import DetailView from '../views/DetailView.vue';
import CartView from '../views/CartView.vue';
import OrdersView from '../views/OrdersView.vue';
import ProfileView from '../views/ProfileView.vue';
import FavoritesView from '../views/FavoritesView.vue';
import ChatView from '../views/ChatView.vue';
import PlaceholderView from '../views/PlaceholderView.vue';
import LoginView from '../views/LoginView.vue';
import SellerView from '../views/SellerView.vue';
import SellerProductsView from '../views/SellerProductsView.vue';
import SellerOrdersView from '../views/SellerOrdersView.vue';
import ShopView from '../views/ShopView.vue';
import QM_STORE from '../core/store.js';

/* 与原版 mall-web 一致的路由表（Hash 模式）：
   #/home  #/category/:catId  #/search?q=  #/detail/:id  #/cart  #/orders?status=
   #/profile  #/favorites  #/chat?peer=  #/placeholder/:feature
   新增 #/login 独立登录/注册页、#/shop/:name 店铺主页；涉及用户数据或下单的页面需登录后才能访问。 */
const routes = [
  { path: '/', redirect: '/home' },
  { path: '/home', name: 'home', component: HomeView },
  { path: '/category/:catId?', name: 'category', component: CategoryView },
  { path: '/search', name: 'search', component: SearchView },
  { path: '/login', name: 'login', component: LoginView },
  { path: '/detail/:id', name: 'detail', component: DetailView },
  { path: '/cart', name: 'cart', component: CartView },
  { path: '/orders', name: 'orders', component: OrdersView },
  { path: '/profile', name: 'profile', component: ProfileView },
  { path: '/favorites', name: 'favorites', component: FavoritesView },
  { path: '/chat', name: 'chat', component: ChatView },
  { path: '/shop/:name', name: 'shop', component: ShopView },
  { path: '/seller', name: 'seller', component: SellerView },
  { path: '/seller/products', name: 'seller-products', component: SellerProductsView },
  { path: '/seller/orders', name: 'seller-orders', component: SellerOrdersView },
  { path: '/placeholder/:feature', name: 'placeholder', component: PlaceholderView },
  { path: '/:pathMatch(.*)*', redirect: '/home' }
];

/* 需登录才能访问的页面：商品详情（点击商品）、店铺主页、购物车、订单、个人中心、收藏、消息中心、
   我的店铺（店铺绑定在当前用户账号下，开店后仍是这一个账号，无需独立店家账号；消息统一在消息中心处理）。
   未登录访问时重定向到登录页，并带上 redirect 参数，登录成功后回跳原目标。 */
const AUTH_REQUIRED = ['detail', 'shop', 'cart', 'orders', 'profile', 'favorites', 'chat', 'seller', 'seller-products', 'seller-orders'];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior() { return { top: 0 }; }
});

/* 全局登录守卫：QM_STORE.load() 已在 main.js 挂载前执行，此处可直接读取登录态 */
router.beforeEach(to => {
  const user = QM_STORE.state.user;

  /* 已登录用户访问登录页：直接回跳目标（默认首页） */
  if (to.name === 'login' && user) {
    const redirect = typeof to.query.redirect === 'string' && to.query.redirect.startsWith('/')
      ? to.query.redirect : '/home';
    return { path: redirect, replace: true };
  }

  /* 未登录访问需登录页面：先去登录，登录后回跳
     （我的店铺与买家页面共用同一用户登录态，不存在独立店家账号） */
  if (AUTH_REQUIRED.includes(to.name) && !user) {
    return { path: '/login', query: { redirect: to.fullPath }, replace: true };
  }
});

export default router;
