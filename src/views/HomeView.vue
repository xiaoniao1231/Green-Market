<script setup>
/* =========================================================
   青集市 · views/HomeView.vue —— 首页
   移植自 mall-web/js/pages/home.js（页面结构 / 交互逻辑不变）
   ========================================================= */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import QM_UI from '../core/ui.js';
import { CATEGORIES } from '../core/catalog.js';
import QM_API from '../core/api.js';
import QM_STORE from '../core/store.js';
import useRouteCompat from '../composables/useRouteCompat.js';

const { esc, price, artStyle, artHtml, productCard, emptyState } = QM_UI;
const route = useRouteCompat();

/* ---------- 静态数据（原 home.js 模块级常量） ---------- */
const SLIDES = ref([
  { tag: '数码科技', title: '科技好物<br/>焕新来袭', sub: '手机、耳机、智能穿戴，品质生活', art: '📱', g: ['#667eea', '#764ba2'], link: '数码科技' },
  { tag: '家居生活', title: '精致家居<br/>美好生活', sub: '收纳、家纺、厨房用品，提升幸福感', art: '🏠', g: ['#f093fb', '#f5576c'], link: '家居生活' },
  { tag: '服饰鞋包', title: '潮流服饰<br/>时尚穿搭', sub: '女装、男装、鞋靴箱包，穿出个性', art: '👗', g: ['#4facfe', '#00f2fe'], link: '服饰鞋包' },
  { tag: '美妆个护', title: '美妆护肤<br/>遇见更美', sub: '护肤、彩妆、香水，绽放你的美', art: '💄', g: ['#fa709a', '#fee140'], link: '美妆个护' },
  { tag: '美食饮品', title: '美味佳肴<br/>舌尖享受', sub: '休闲零食、咖啡茶饮，满足味蕾', art: '🍜', g: ['#ff9a56', '#ff5000'], link: '美食饮品' },
  { tag: '运动户外', title: '运动健身<br/>活力无限', sub: '健身器材、露营装备，释放活力', art: '⚽', g: ['#43e97b', '#38f9d7'], link: '运动户外' },
  { tag: '图书文具', title: '书香墨韵<br/>知识相伴', sub: '文具、手账、小说文学，充实自己', art: '📚', g: ['#8b5e3c', '#c9a06c'], link: '图书文具' },
  { tag: '宠物花植', title: '萌宠相伴<br/>治愈生活', sub: '猫狗主粮、绿植盆栽，温暖日常', art: '🐱', g: ['#a8edea', '#fed6e3'], link: '宠物花植' }
]);

const REC_CATS = ['全部'].concat(CATEGORIES.slice(0, 5).map(c => c.id));

/* 左侧分类栏的二级类目：跳到分类页并带上 ?sub=，由分类页选中对应子类目。
   注意：模板里原本就调用了 goToSub，但本组件从未定义它 —— 点击只会抛
   ReferenceError（二级标签"点不动"），这里补上真正的跳转实现 */
function goToSub(catId, subName) {
  window.location.hash = '#/category/' + encodeURIComponent(catId) + '?sub=' + encodeURIComponent(subName);
}

/* ---------- 定时器（原版 timers 数组，unmount 统一清理） ---------- */
const timers = [];

/* ---------- 右侧用户卡（登录态 / 收藏数，随页面重挂载刷新，原版不订阅 store） ---------- */
function storeUser() { return (QM_STORE.state && QM_STORE.state.user) || null; }
function storeFavCount() { return ((QM_STORE.state && QM_STORE.state.favorites) || []).length; }
/* App.vue 在自身 onMounted 里才调用 QM_STORE.load()；首页（默认路由）可能先于它挂载，
   此处按原版启动顺序兜底加载一次（幂等，不影响后续逻辑）。 */
if (!QM_STORE.state) QM_STORE.load();
const user = ref(storeUser());
const favCount = ref(storeFavCount());
/* 未读消息数：与会话中心同一数据源（store.contacts 的 unread 汇总），
   由 App.vue 登录后同步 + 实时消息累加，这里只负责显示 */
const msgUnread = ref(QM_STORE.chat.unreadTotal());
function syncMember() {
  user.value = storeUser();
  favCount.value = storeFavCount();
  msgUnread.value = QM_STORE.chat.unreadTotal();
}
/* store.state 不是响应式对象，靠这些事件驱动会员卡数字重算 */
const memberOffs = [
  QM_STORE.on('favorites', syncMember),
  QM_STORE.on('chat', syncMember),
  QM_STORE.on('user', syncMember)
];

/* ---------- 轮播 ---------- */
const idx = ref(0);
function go(i) { idx.value = (i + SLIDES.value.length) % SLIDES.value.length; }

/* ---------- 限时秒杀 ---------- */
const flashReady = ref(false);
const flashCardsHtml = ref('');
const hh = ref('00');
const mm = ref('00');
const ss = ref('00');
const flashPct = ref(0);

function flashCard(p) {
  /* 折扣显示：原价与秒杀价之比（299/459 → 6.5 折）。
     原实现为 Math.round((1 - price/original) * 100)，算的是「降价百分比」，
     会把 6.5 折显示成「35 折」。 */
  const discount = p.original ? (p.price / p.original * 10) : 10;
  const discountText = (Math.round(discount * 10) / 10).toFixed(1).replace(/\.0$/, '');
  return `
    <div class="product-card" data-action="open-product" data-id="${esc(p.id)}">
      <div class="pc-art" style="${artStyle(p.art)}">
        <span class="pc-tag">${discountText}折</span>${artHtml(p.art)}
      </div>
      <div class="pc-info">
        <h3 class="ellipsis-2">${esc(p.title)}</h3>
        <div class="pc-price-row">${price(p.price)}<del>${price(p.original)}</del></div>
        <div class="pc-meta"><span>已抢 ${Math.round(p.sales / 20)} 件</span><span>仅剩 ${Math.min(p.stock, 60)} 件</span></div>
      </div>
    </div>`;
}

/* 秒杀结束时间：后端 /home/flash 返回 endTime 时以其为准，否则用本地计时（2 小时一轮）。
   存 sessionStorage —— 读写都要 try/catch，浏览器禁用存储时 setItem 会直接抛异常。 */
const FLASH_KEY = 'qm_v2_flash_end';
const FLASH_ROUND_MS = 2 * 3600e3;
function flashEndLocal() {
  let end = 0;
  try { end = Number(sessionStorage.getItem(FLASH_KEY) || 0); } catch (e) { end = 0; }
  if (!end || end < Date.now()) end = Date.now() + FLASH_ROUND_MS;
  flashEndSave(end);
  return end;
}
function flashEndSave(end) {
  try { sessionStorage.setItem(FLASH_KEY, String(end)); } catch (e) { /* 忽略 */ }
}

async function loadFlash() {
  let endTime = flashEndLocal();
  try {
    const data = await QM_API.products.flash();
    flashCardsHtml.value = (data.list || []).map(p => flashCard(p)).join('');
    /* 后端下发的场次结束时间优先（字符串 yyyy-MM-dd HH:mm:ss 或毫秒时间戳） */
    const remote = data && data.endTime;
    if (remote) {
      const t = typeof remote === 'number' ? remote : Date.parse(String(remote).replace(' ', 'T'));
      if (!isNaN(t) && t > Date.now()) { endTime = t; flashEndSave(endTime); }
    }
  } catch (e) {
    flashCardsHtml.value = emptyState('⚡', '秒杀商品加载失败', '接口暂时不可达，请稍后重试');
  }
  flashReady.value = true;
  const tick = () => {
    let left = Math.max(0, endTime - Date.now());
    /* 倒计时归零：重置为本轮结束时间并继续倒数。
       原实现只改写存储、不更新闭包里的 endTime，界面会永远停在 00:00:00 */
    if (left <= 0) {
      endTime = Date.now() + FLASH_ROUND_MS;
      flashEndSave(endTime);
      left = endTime - Date.now();
    }
    const pad = v => String(v).padStart(2, '0');
    hh.value = pad(Math.floor(left / 3600e3));
    mm.value = pad(Math.floor(left % 3600e3 / 60e3));
    ss.value = pad(Math.floor(left % 60e3 / 1e3));
    flashPct.value = Math.round((1 - left / FLASH_ROUND_MS) * 100);
  };
  tick();
  timers.push(setInterval(tick, 1000));
}

/* ---------- 猜你喜欢 ---------- */
const recFilter = ref('全部');
const recGridHtml = ref('');   // 骨架期为空，加载后填充（与原版 innerHTML 流程一致）
const moreVisible = ref(true); // 骨架期「加载更多」按钮默认可见（原版同款）
let page = 1;

async function loadRecommend(filter) {
  try {
    const res = await QM_API.products.recommend({ page: 1, size: 15 });
    let list = (res && res.list) || [];
    if (filter !== '全部') list = list.filter(p => p.category === filter);
    recGridHtml.value = list.length
      ? list.map(p => productCard(p)).join('')
      : emptyState('🔍', '该分类暂无推荐', '去其他分类看看吧');
    const btnShow = list.length >= 15 && filter === '全部';
    moreVisible.value = btnShow;
    if (btnShow) page = 1;
  } catch (e) {
    recGridHtml.value = emptyState('🔍', '推荐商品加载失败', '接口暂时不可达，请稍后重试');
    moreVisible.value = false;
  }
}

function onRecTab(c) {
  recFilter.value = c;
  loadRecommend(c);
}

const onLoadMore = (() => {
  /* 防抖 + 串行：连续点击「加载更多」时原实现会并发请求并把结果乱序 += 拼接（重复/错序商品）。
     这里用 loading 标志保证同一时刻只有一个分页请求在飞，加载中直接忽略后续点击。 */
  const moreLoading = ref(false);
  async function loadMore() {
    if (moreLoading.value || !moreVisible.value) return;
    moreLoading.value = true;
    try {
      const next = page + 1;
      const res = await QM_API.products.recommend({ page: next, size: 15 });
      const extra = (res && res.list) || [];
      if (!extra.length) { moreVisible.value = false; return; }
      page = next;
      recGridHtml.value += extra.map(p => productCard(p)).join('');
      if (extra.length < 15) moreVisible.value = false;
    } catch (e) {
      /* 加载失败保留按钮，用户可重试 */
    } finally {
      moreLoading.value = false;
    }
  }
  return loadMore;
})();

/* ---------- 锚点定位（#/home?sec=flash / recommend） ---------- */
function scrollToSec(sec) {
  if (!sec) return;
  setTimeout(() => {
    const el = document.getElementById(String(sec));
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, 120);
}

/* ---------- 页面入口（原 mount） ---------- */
onMounted(async () => {
  syncMember();

  /* 轮播自动播放 */
  timers.push(setInterval(() => go(idx.value + 1), 4000));

  /* 秒杀 → 猜你喜欢（与原版顺序一致：都加载完再做锚点定位） */
  await loadFlash();
  await loadRecommend('全部');

  /* 锚点定位（如 #/home?sec=flash） */
  scrollToSec(route.value.query.sec);
});

/* 同组件复用：/home?sec=flash ↔ /home?sec=recommend（仅 query 变化时不重挂载） */
watch(() => route.value.query.sec, sec => { if (sec) scrollToSec(sec); });

/* ---------- 清理（原 unmount） ---------- */
onBeforeUnmount(() => { timers.forEach(clearInterval); memberOffs.forEach(off => off()); });
</script>

<template>
  <div>
    <!-- ===== 首屏：左侧分类栏 / 中间轮播+快捷入口 / 右侧用户卡 ===== -->
    <div class="home-grid">
      <aside class="cat-rail">
        <h2>精选分类 <a href="#/category/全部">全部 ›</a></h2>
        <div v-for="c in CATEGORIES" :key="c.id" class="cat-rail-item">
          <button data-action="goto-category" :data-id="c.id">
          <span class="rail-icon">{{ c.icon }}</span>{{ c.id }}<b>›</b>
          </button>
          <div v-if="c.subs && c.subs.length" class="cat-rail-sub">
            <button v-for="s in c.subs" :key="s" @click="goToSub(c.id, s)">{{ s }}</button>
          </div>
        </div>
      </aside>

      <section class="hero-zone">
        <!-- 轮播 -->
        <div class="carousel" id="carousel">
          <div class="carousel-track" id="carouselTrack" :style="{ transform: 'translateX(-' + idx * 100 + '%)' }">
            <div
              v-for="s in SLIDES"
              :key="s.link"
              class="carousel-slide"
              :style="{ background: 'linear-gradient(120deg,' + s.g[0] + ',' + s.g[1] + ')' }"
            >
              <div class="carousel-copy">
                <span class="c-tag">{{ s.tag }}</span>
                <h1 v-html="s.title"></h1>
                <p>{{ s.sub }}</p>
                <a class="btn" :href="'#/category/' + encodeURIComponent(s.link)">马上逛逛 →</a>
              </div>
              <span class="carousel-art">{{ s.art }}</span>
            </div>
          </div>
          <button class="carousel-arrow prev" data-action="carousel" data-dir="-1" @click="go(idx - 1)">‹</button>
          <button class="carousel-arrow next" data-action="carousel" data-dir="1" @click="go(idx + 1)">›</button>
          <div class="carousel-dots">
            <i v-for="(s, i) in SLIDES" :key="s.link" :data-dot="i" :class="{ active: i === idx }" @click="go(i)"></i>
          </div>
        </div>
      </section>

      <!-- 用户卡 -->
      <aside class="member-card">
        <div class="member-user">
          <span class="member-avatar">{{ user ? user.nickname.slice(0, 1) : '语' }}</span>
          <p>
            <strong>{{ user ? user.nickname : '轻语用户' }}</strong>
            <small>{{ user ? '@' + user.userId : '登录后享受更多服务' }}</small>
          </p>
        </div>
        <div class="member-actions">
          <template v-if="user">
            <button class="btn btn-ghost" data-action="logout">退出</button>
            <a class="btn btn-primary" href="#/profile">我的主页</a>
          </template>
          <template v-else>
            <button class="btn btn-ghost" data-action="open-login">登录</button>
            <button class="btn btn-primary" data-action="open-register">免费注册</button>
          </template>
        </div>
        <div class="member-line"></div>
        <div class="order-shortcuts">
          <a href="#/orders"><span>◴</span>待付款</a>
          <a href="#/orders"><span>▣</span>待收货</a>
          <a href="#/favorites"><span>♡</span>收藏<b style="display:inline;color:var(--brand)">{{ favCount }}</b></a>
          <a href="#/chat"><span>◌</span>消息<b v-if="msgUnread" class="mini-badge">{{ msgUnread > 99 ? '99+' : msgUnread }}</b></a>
        </div>
        <div class="notice"><b>公告</b><span>新用户下单立享 30 元优惠券</span></div>
      </aside>
    </div>

    <!-- 服务保障条 -->
    <section class="benefits">
      <span><i>✓</i>正品保障</span><span><i>✓</i>极速发货</span><span><i>✓</i>7 天无忧退货</span><span><i>✓</i>售后无忧</span>
    </section>

    <!-- 限时秒杀（数据就绪后注入，原版 #flashSlot） -->
    <div id="flashSlot">
      <section v-if="flashReady" class="section-block" id="flash">
        <div class="flash-wrap">
          <div class="flash-side">
            <h3>限时秒杀</h3>
            <p>FLASH DEAL · 手慢无</p>
            <div class="flash-count" id="flashCount">
              <i id="fcH">{{ hh }}</i><b>:</b><i id="fcM">{{ mm }}</i><b>:</b><i id="fcS">{{ ss }}</i>
            </div>
            <a class="btn" href="#/home?sec=flash">马上抢 ›</a>
            <div class="flash-progress"><i id="flashProgress" :style="{ width: flashPct + '%' }"></i></div>
          </div>
          <div class="flash-products" v-html="flashCardsHtml"></div>
        </div>
      </section>
    </div>

    <!-- 猜你喜欢（骨架随首次渲染出现，与原版 render 一致） -->
    <div id="recommendSlot">
      <section class="section-block" id="recommend">
        <div class="section-heading">
          <div><span class="s-label">DISCOVER</span><h2>猜你喜欢<em>为你精选好物</em></h2></div>
          <div class="tabs" id="recTabs">
            <button
              v-for="c in REC_CATS"
              :key="c"
              :class="{ active: recFilter === c }"
              data-action="rec-filter"
              :data-id="c"
              @click="onRecTab(c)"
            >{{ c }}</button>
          </div>
        </div>
        <div id="productGrid" class="product-grid" v-html="recGridHtml"></div>
        <div class="load-more">
          <button class="btn btn-plain" id="loadMore" v-show="moreVisible" @click="onLoadMore">加载更多 ⌄</button>
        </div>
      </section>
    </div>
  </div>
</template>
