<script setup>
/* =========================================================
   青集市 · views/DetailView.vue —— 商品详情页
   移植自 mall-web/js/pages/detail.js（页面结构 / 交互逻辑不变）
   要点：
   · 原版 render() 先输出“加载中”骨架，mount() 里 await 商品后整块替换
     #detailRoot —— 这里用 phase（loading/missing/ready）三态 + 模板条件输出复刻；
   · 原版在 mount() 里逐个给 sku/qty/thumb/tab/add-cart/buy-now 绑定事件，
     这里用响应式状态 + @click 实现（DOM 结构与 data-action 全部保留）；
   · toggle-fav / goto-chat / goto-category 及推荐卡片里的 open-product /
     quick-add-cart 由 App.vue 的全局事件代理处理，本组件只保留属性并按原版
     订阅 QM_STORE 'favorites' 事件同步收藏按钮文案；
   · QM_ROUTER.go('/cart') → router.push('/cart')，QM_STORE.fav.has(id) 驱动文案。
   ========================================================= */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import QM_UI from '../core/ui.js';
import QM_API from '../core/api.js';
import QM_MOCK from '../core/mock.js';
import QM_STORE from '../core/store.js';
import useRouteCompat from '../composables/useRouteCompat.js';

const { artStyle, artHtml, sales, productCard, toast } = QM_UI;
const route = useRouteCompat();
const router = useRouter();

/* 三档可选外观（与 detail.js 的 VARIANTS 一致：原色 / 暖色渐变 / 冷色渐变） */
const VARIANTS = [
  { g: null, e: null },                    // 原色
  { g: ['#ffecd2', '#fcb69f'], e: null },  // 暖色
  { g: ['#a1c4fd', '#c2e9fb'], e: null }   // 冷色
];

const id = computed(() => route.value.params[0] || 'p01');

/* ---------- 页面状态（对应原版 render + mount 阶段性输出） ---------- */
const phase = ref('loading');   // loading 加载中 / missing 商品不存在 / ready 已就绪
const product = ref(null);
const dTitle = ref('加载中…');
const selected = ref([]);       // 每组 SKU 当前选中值的下标（原版 selected[group]）
const qty = ref(1);
const variant = ref(0);         // 当前图集外观（VARIANTS 下标）
const tab = ref('desc');        // desc 图文详情 / spec 规格参数 / comment 商品评价
const relatedList = ref([]);
const favTick = ref(0);         // QM_STORE 非响应式：用版本号驱动收藏按钮重算

/* 价格两段式拆分（复刻 ui.js price() 的输出结构，避免在 DOM 上多包一层） */
function priceParts(n) {
  n = Number(n || 0);
  const text = n % 1 === 0 ? String(n) : n.toFixed(2);
  const [int, dec] = text.split('.');
  return { int, dec: dec !== undefined ? '.' + dec : '' };
}

const curPrice = computed(() => priceParts(product.value.price));
const origPrice = computed(() => priceParts(product.value.original));
const goodRate = computed(() => Math.round((product.value.shop.score / 5) * 100));

/* 店铺的开店用户 id（详情页「联系卖家」→ 对端就是这位用户，由消息中心创建会话） */
const serviceId = computed(() => (product.value ? QM_MOCK.serviceOf(product.value.shop.name).userId : 'platform'));

/* 店铺信息（店铺页 / 店铺信息弹窗共用）：{shopName, owner, avatar, color, fans, founded, shopIntro, ...} */
const shopInfo = computed(() => {
  const p = product.value;
  if (!p) return null;
  const s = QM_MOCK.serviceOf(p.shop.name);
  return Object.assign({ shopName: p.shop.name, score: p.shop.score }, s);
});
/* 关注店铺按钮文案：订阅 shopFavs 事件驱动重算 */
const shopFavTick = ref(0);
const shopFaved = computed(() => {
  shopFavTick.value;
  return !!(product.value && QM_STORE.shopFav.has(product.value.shop.name));
});

/* 点击店铺头像查看店铺基本信息弹窗 */
function openShopInfo() {
  const s = shopInfo.value;
  if (!s) return;
  QM_UI.modal(`
    <div style="position:relative">
      <button class="modal-close" data-close>×</button>
      <div style="display:flex;align-items:center;gap:14px;margin-bottom:16px">
        <span class="shop-avatar lg" style="background:${s.color}">${s.avatar || '店'}</span>
        <div>
          <h3 style="margin-bottom:2px">${s.shopName}</h3>
          <p class="modal-sub" style="margin-bottom:0">卖家：${s.owner || '—'}</p>
        </div>
      </div>
      <p class="shop-info-intro">${s.shopIntro || s.intro || ''}</p>
      <div class="shop-info-grid">
        <div><b>${s.score || '—'}</b><span>店铺评分</span></div>
        <div><b>${QM_UI.sales(s.fans || 0)}</b><span>粉丝</span></div>
        <div><b>${s.founded || '—'}</b><span>开店时间</span></div>
        <div><b>${QM_UI.sales((product.value ? product.value.sales : 0) || 0)}</b><span>累计销量</span></div>
      </div>
      <div class="modal-actions" style="margin-top:16px">
        <button class="btn btn-primary" data-action="goto-chat" data-id="${s.userId || s.id}">联系卖家</button>
        <button class="btn btn-plain" data-action="goto-shop" data-id="${s.shopName}">进店逛逛</button>
      </div>
    </div>`, { wide: true });
}

/* 关注 / 取消关注店铺 */
function toggleShopFav() {
  if (!requireLogin()) return;
  const faved = QM_STORE.shopFav.toggle(product.value.shop.name);
  toast(faved ? '已关注店铺 ♥' : '已取消关注', faved ? 'success' : '');
}
function requireLogin() {
  if (QM_STORE.state.user) return true;
  toast('请先登录后再继续', 'error');
  router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } });
  return false;
}

/* 商品图形（表情 + 渐变） */
const thumbArtOf = v => {
  const p = product.value;
  return v.g ? { e: p.art.e, g: v.g } : p.art;
};
const mainArt = computed(() => thumbArtOf(VARIANTS[variant.value]));

const relatedHtml = computed(() => relatedList.value.map(p => productCard(p)).join(''));

/* 收藏按钮文案：原版按钮由全局事件 toggle-fav 处理，本组件订阅 favorites 同步刷新 */
const faved = computed(() => {
  favTick.value; // 依赖版本号，store 事件触发后重算
  return !!(product.value && QM_STORE.fav.has(product.value.id));
});

let seq = 0;        // 加载序号：/detail/p01 → /detail/p02 快速切换时丢弃过期响应
let disposed = false;
let offFav = null;

async function load() {
  const mySeq = ++seq;
  const pid = id.value;
  /* 复位到原版 render() 的初始状态（相当于重新 render + mount） */
  phase.value = 'loading';
  product.value = null;
  dTitle.value = '加载中…';
  relatedList.value = [];
  qty.value = 1;
  variant.value = 0;
  tab.value = 'desc';
  let p = null;
  try { p = await QM_API.products.get(pid); } catch (e) { toast(e.message, 'error'); }
  if (disposed || mySeq !== seq) return;
  if (!p) {
    phase.value = 'missing';  // 原版：根节点替换为“商品不存在”空状态，标题保持“加载中…”
    return;
  }
  product.value = p;
  selected.value = p.skus.map(() => 0);
  dTitle.value = p.title;
  phase.value = 'ready';
  loadRelated(pid, mySeq);
}

/* 相关推荐（原版 mount 里异步追加到 #relatedList） */
async function loadRelated(pid, mySeq) {
  try {
    const data = await QM_API.products.related(pid, 4);
    if (disposed || mySeq !== seq) return;
    /* 兼容两种响应：直接返回数组，或返回 {list:[...]}（后端分页结构） */
    relatedList.value = Array.isArray(data) ? data : ((data && data.list) || []);
  } catch (e) {
    /* 推荐位失败不应影响商品详情主流程：保持骨架/空态即可 */
    if (!disposed && mySeq === seq) relatedList.value = [];
  }
}

/* ---------- 交互（原版 mount 里逐个绑定的事件） ---------- */
const skuText = () => product.value.skus.map((s, i) => s.values[selected.value[i]]).join(' / ');

function pickSku(group, vi) { selected.value[group] = vi; }
function pickThumb(i) { variant.value = i; }
function setTab(name) { tab.value = name; }
function setQty(v) { qty.value = Math.max(1, Math.min(5, v)); }
function stepQty(dir) { setQty(qty.value + dir); }
function onQtyInput(e) { setQty(parseInt(e.target.value, 10) || 1); }

async function addCart() {
  await QM_API.cart.add(product.value.id, skuText(), qty.value);
  toast('已加入购物车 🛒', 'success');
}
async function buyNow() {
  await QM_API.cart.add(product.value.id, skuText(), qty.value);
  /* 记录「本次立即购买」的目标商品：购物车页据此精确结算，
     避免结算到购物车里最后一条（可能是无关的旧商品） */
  try {
    sessionStorage.setItem('qm_v2_buynow', JSON.stringify({ productId: product.value.id, sku: skuText() }));
  } catch (e) { /* 存储不可用时退化为购物车页默认的结算行为 */ }
  router.push('/cart'); // 原 QM_ROUTER.go('/cart')
}

/* /detail/p01 → /detail/p02 时复用本组件：按原版“重新挂载”复位并重新加载 */
watch(id, load);

let offShopFav = null;
onMounted(() => {
  offFav = QM_STORE.on('favorites', () => { favTick.value++; });
  offShopFav = QM_STORE.on('shopFavs', () => { shopFavTick.value++; });
  load();
});
onBeforeUnmount(() => {
  disposed = true;
  if (offFav) { offFav(); offFav = null; }
  if (offShopFav) { offShopFav(); offShopFav = null; }
});
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 商品详情</div>
        <h1 id="dTitle">{{ dTitle }}</h1>
      </div>
    </div>

    <div id="detailRoot">
      <!-- 加载中（原版 render() 初始输出） -->
      <div v-if="phase === 'loading'" class="empty-state">
        <div class="empty-icon">⏳</div>
        <h3>正在加载商品详情…</h3>
      </div>

      <!-- 商品不存在 / 已下架 -->
      <div v-else-if="phase === 'missing'" class="empty-state">
        <div class="empty-icon">🛍️</div>
        <h3>商品不存在</h3>
        <p>该商品已下架或链接失效</p>
        <a class="btn btn-primary" href="#/home">返回首页</a>
      </div>

      <template v-else>
        <div class="detail-layout">
          <!-- 图集（表情 + 渐变） -->
          <div class="detail-gallery">
            <div class="g-main" id="gMain" :style="artStyle(mainArt)" v-html="artHtml(mainArt)"></div>
            <div class="g-thumbs">
              <div
                v-for="(v, i) in VARIANTS"
                :key="i"
                class="g-thumb"
                :class="{ active: variant === i }"
                data-action="thumb"
                :data-i="i"
                :style="artStyle(thumbArtOf(v))"
                v-html="artHtml(thumbArtOf(v))"
                @click="pickThumb(i)"
              ></div>
            </div>
          </div>

          <!-- 商品信息 -->
          <div class="detail-info">
            <h1>{{ product.title }}</h1>
            <p class="detail-sub">{{ product.sub }} · {{ product.shop.name }}</p>
            <div class="detail-price-card">
              <span class="price"><i>¥</i>{{ curPrice.int }}<em v-if="curPrice.dec">{{ curPrice.dec }}</em></span
              ><del><span class="price"><i>¥</i>{{ origPrice.int }}<em v-if="origPrice.dec">{{ origPrice.dec }}</em></span></del>
              <div class="price-meta">
                <span>销量 {{ sales(product.sales) }}</span>
                <span>库存 {{ product.stock }} 件</span>
                <span>好评率 {{ goodRate }}%</span>
              </div>
            </div>

            <!-- 规格选择 -->
            <div class="detail-skus">
              <div v-for="(g, gi) in product.skus" :key="gi" class="sku-group">
                <b>{{ g.name }}：</b>
                <button
                  v-for="(v, vi) in g.values"
                  :key="vi"
                  class="sku-chip"
                  :class="{ active: selected[gi] === vi }"
                  data-action="sku"
                  :data-group="gi"
                  :data-value="v"
                  @click="pickSku(gi, vi)"
                >{{ v }}</button>
              </div>
            </div>

            <!-- 数量（限购 5 件） -->
            <div class="detail-qty">
              <span>数量</span>
              <span class="stepper">
                <button data-action="qty" data-dir="-1" @click="stepQty(-1)">−</button>
                <input id="qtyInput" :value="qty" @input="onQtyInput" />
                <button data-action="qty" data-dir="1" @click="stepQty(1)">＋</button>
              </span>
              <span class="pill pill-gray">每人限购 5 件</span>
            </div>

            <!-- 加入购物车 / 立即购买 -->
            <div class="detail-buy">
              <button class="btn btn-lg btn-ghost" data-action="add-cart" @click="addCart">🛒 加入购物车</button>
              <button class="btn btn-lg btn-primary" data-action="buy-now" @click="buyNow">立即购买</button>
            </div>
            <div class="detail-buy" style="margin-top:-6px">
              <button class="btn btn-plain" data-action="toggle-fav" :data-id="product.id">{{ faved ? '♥ 已收藏' : '♡ 收藏商品' }}</button>
              <button class="btn btn-plain" data-action="goto-chat" :data-id="serviceId">◌ 联系卖家</button>
            </div>
            <div class="service-row detail-service"><span>正品保障</span><span>极速发货</span><span>7 天无忧退货</span></div>
          </div>

          <!-- 店铺信息 -->
          <aside class="detail-shop">
            <div class="shop-mini">
              <span class="shop-avatar" :style="{ background: shopInfo.color }">{{ shopInfo.avatar || '店' }}</span>
              <button class="shop-mini-meta" title="点击查看店铺信息" @click="openShopInfo">
                <b class="ellipsis">{{ shopInfo.shopName }}</b>
                <small>点击查看店铺信息</small>
              </button>
            </div>
            <div class="shop-score"><span>店铺评分</span><b>{{ product.shop.score }}</b></div>
            <div class="shop-score"><span>商品描述</span><b>4.8</b></div>
            <div class="shop-score"><span>发货速度</span><b>4.9</b></div>
            <div class="shop-score"><span>粉丝</span><b>{{ sales(shopInfo.fans) }}</b></div>
            <button class="btn btn-ghost" :class="{ faved: shopFaved }" @click="toggleShopFav">{{ shopFaved ? '♥ 已关注' : '♡ 关注店铺' }}</button>
            <button class="btn btn-ghost" data-action="goto-chat" :data-id="serviceId">◌ 联系卖家</button>
            <button class="btn btn-plain" data-action="goto-shop" :data-id="product.shop.name">进店逛逛 →</button>
          </aside>
        </div>

        <!-- 详情 / 评价 / 相关推荐 -->
        <div class="detail-below">
          <div class="detail-panel">
            <div class="detail-tabs" id="detailTabs">
              <button :class="{ active: tab === 'desc' }" data-tab="desc" @click="setTab('desc')">图文详情</button>
              <button :class="{ active: tab === 'spec' }" data-tab="spec" @click="setTab('spec')">规格参数</button>
              <button :class="{ active: tab === 'comment' }" data-tab="comment" @click="setTab('comment')">商品评价（{{ product.comments.length }}）</button>
            </div>
            <div id="tabDesc" class="detail-desc" :class="{ hidden: tab !== 'desc' }">
              <p>{{ product.desc }}</p>
              <p style="margin-top:14px" v-html="artHtml(product.art, '120px')"></p>
            </div>
            <table id="tabSpec" class="spec-table" :class="{ hidden: tab !== 'spec' }">
              <tr v-for="(p, i) in product.params" :key="i"><td>{{ p[0] }}</td><td>{{ p[1] }}</td></tr>
            </table>
            <div id="tabComment" :class="{ hidden: tab !== 'comment' }">
              <div v-for="(c, i) in product.comments" :key="i" class="comment-item">
                <span class="c-avatar">{{ c.user.slice(0, 1) }}</span>
                <div>
                  <b>{{ c.user }} <span style="color:#ffb400">{{ '★'.repeat(c.rate) }}{{ '☆'.repeat(5 - c.rate) }}</span></b>
                  <p>{{ c.text }}</p>
                  <small>{{ c.time }} · 颜色款式：默认</small>
                </div>
              </div>
            </div>
          </div>
          <aside class="detail-shop" style="margin-top:0">
            <h4>看了又看</h4>
            <div id="relatedList" style="display:flex;flex-direction:column;gap:10px" v-html="relatedHtml"></div>
          </aside>
        </div>
      </template>
    </div>
  </div>
</template>
