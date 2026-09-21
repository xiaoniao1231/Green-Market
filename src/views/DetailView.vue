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
import QM_STORE from '../core/store.js';
import useRouteCompat from '../composables/useRouteCompat.js';

const { artStyle, artHtml, sales, productCard, toast, avatarHtml, isImage } = QM_UI;
const route = useRouteCompat();
const router = useRouter();

/* 三档可选外观（与 detail.js 的 VARIANTS 一致：原色 / 暖色渐变 / 冷色渐变） */
const VARIANTS = [
  { g: null, e: null },                    // 原色
  { g: ['#ffecd2', '#fcb69f'], e: null },  // 暖色
  { g: ['#a1c4fd', '#c2e9fb'], e: null }   // 冷色
];

const id = computed(() => route.value.params[0] || 'p01');

/* ---------- SKU 值兼容两种形态：字符串（旧数据）或 { v, img }（新数据，款式带图） ---------- */
const valOf = v => (typeof v === 'string' ? v : (v && v.v) || '');
const valImg = v => (typeof v === 'string' ? '' : (v && v.img) || '');

/* ---------- 页面状态（对应原版 render + mount 阶段性输出） ---------- */
const phase = ref('loading');   // loading 加载中 / missing 商品不存在 / ready 已就绪
const product = ref(null);
const dTitle = ref('加载中…');
const selected = ref([]);       // 每组 SKU 当前选中值的下标（原版 selected[group]）
const qty = ref(1);
const variant = ref(0);         // 无图商品的图集外观（VARIANTS 下标，仅 art.img 为空时使用）
const lastSkuGroup = ref(null); // 最近点击的 SKU 组：有图商品主图跟随款式切换
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

/* 店铺的开店用户 id（详情页「联系卖家」→ 对端就是这位用户，由消息中心创建会话）
   卖家账号由后端随商品下发（shop.userId / shop.ownerUserId），
   兜底从已拉取的店铺档案里取（商品带 shop.id 时） */
const serviceId = computed(() => {
  const p = product.value;
  if (!p || !p.shop) return '';
  const s = p.shop;
  const fromProfile = s.id ? (QM_STORE.state.shopProfile[s.id] || {}) : {};
  return s.userId || s.ownerUserId || fromProfile.ownerUserId || fromProfile.userId || '';
});

/* 店铺信息（店铺页 / 店铺信息弹窗共用）：{shopName, owner, avatar, color, fans, founded, shopIntro, ...} */
const shopInfo = computed(() => {
  const p = product.value;
  if (!p) return null;
  const s = QM_STORE.shopService(p.shop.name);
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
    <div>
      <div style="display:flex;align-items:center;gap:14px;margin-bottom:16px">
        ${avatarHtml(s.avatar, 'lg', s.color)}
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
        <button class="btn btn-plain" data-close>关闭</button>
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

/* 商品图形（表情 + 渐变；仅无图商品使用） */
const thumbArtOf = v => {
  const p = product.value;
  return v.g ? { e: p.art.e, g: v.g } : p.art;
};

/* 有图商品：主图跟随款式 —— 最近点击的 SKU 组选中值有图则显示该图，否则回退商品主图；
   无图商品：保持原「原色 / 暖色 / 冷色」外观变体切换 */
const mainArt = computed(() => {
  const p = product.value;
  if (!p) return null;
  if (p.art.img) {
    const g = lastSkuGroup.value;
    if (g !== null && p.skus[g]) {
      const v = p.skus[g].values[selected.value[g]];
      const img = v && valImg(v);
      if (img) return { img };
    }
    return p.art;
  }
  return thumbArtOf(VARIANTS[variant.value]);
});

/* 缩略图列表：
   · 无图商品 → 3 个渐变外观变体（原行为）；
   · 有图商品 → 商品主图 + 各 SKU 值图（去重），点击即选中对应款式 */
const thumbs = computed(() => {
  const p = product.value;
  if (!p) return [];
  if (!p.art.img) return VARIANTS.map((v, i) => ({ kind: 'variant', i }));
  const list = [{ kind: 'main' }];
  const seen = new Set();
  p.skus.forEach((g, gi) => (g.values || []).forEach((v, vi) => {
    const img = valImg(v);
    if (img && !seen.has(img)) { seen.add(img); list.push({ kind: 'sku', group: gi, vi, img }); }
  }));
  return list;
});
/* 当前高亮缩略图：有图商品按最近点击款式定位，否则主图 */
const currentThumb = computed(() => {
  const p = product.value;
  if (!p) return { kind: 'main' };
  if (p.art.img) {
    const g = lastSkuGroup.value;
    if (g !== null && p.skus[g]) {
      const v = p.skus[g].values[selected.value[g]];
      if (v && valImg(v)) return { kind: 'sku', group: g, vi: selected.value[g] };
    }
    return { kind: 'main' };
  }
  return { kind: 'variant', i: variant.value };
});

/* 图文详情段落：新数据为 detail 段落数组（text/img 混合），旧数据仅有 desc 字符串 */
const detailBlocks = computed(() => (product.value && product.value.detail) || []);

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
  lastSkuGroup.value = null;
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
const skuText = () => product.value.skus.map((s, i) => valOf(s.values[selected.value[i]])).join(' / ');

function pickSku(group, vi) {
  selected.value[group] = vi;
  lastSkuGroup.value = group; // 有图商品：点击款式即切换主图
}
function pickThumb(t) {
  if (t.kind === 'sku') { lastSkuGroup.value = t.group; selected.value[t.group] = t.vi; }
  else if (t.kind === 'variant') { variant.value = t.i; }
  else { lastSkuGroup.value = null; } // 点主图缩略图 → 恢复商品主图
}
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
          <!-- 图集（店家上传主图 / 表情+渐变，缩略图随款式联动） -->
          <div class="detail-gallery">
            <div class="g-main" id="gMain" :style="artStyle(mainArt)" v-html="artHtml(mainArt)"></div>
            <div class="g-thumbs">
              <template v-for="(t, i) in thumbs" :key="i">
                <!-- 无图商品：外观变体缩略图 -->
                <div v-if="t.kind === 'variant'" class="g-thumb" :class="{ active: currentThumb.kind === 'variant' && currentThumb.i === t.i }" data-action="thumb" :data-i="t.i" :style="artStyle(thumbArtOf(VARIANTS[t.i]))" v-html="artHtml(thumbArtOf(VARIANTS[t.i]))" @click="pickThumb(t)"></div>
                <!-- 有图商品：款式图缩略图 -->
                <div v-else-if="t.kind === 'sku'" class="g-thumb" :class="{ active: currentThumb.kind === 'sku' && currentThumb.group === t.group && currentThumb.vi === t.vi }" :title="valOf(product.skus[t.group].values[t.vi])" @click="pickThumb(t)"><img class="thumb-img" :src="t.img" alt="" loading="lazy" /></div>
                <!-- 有图商品：主图缩略图 -->
                <div v-else class="g-thumb" :class="{ active: currentThumb.kind === 'main' }" title="商品主图" @click="pickThumb(t)"><span :style="artStyle(product.art)" v-html="artHtml(product.art)"></span></div>
              </template>
            </div>
          </div>

          <!-- 商品信息 -->
          <div class="detail-info">
            <h1>{{ product.title }}</h1>
            <p class="detail-sub">{{ product.sub }} · {{ product.shop.name }}</p>
            <div class="detail-price-card">
              <div class="price-row">
                <span class="price"><i>¥</i>{{ curPrice.int }}<em v-if="curPrice.dec">{{ curPrice.dec }}</em></span>
                <span class="price-badge">到手价</span>
                <del v-if="product.original > 0"><span class="price"><i>¥</i>{{ origPrice.int }}<em v-if="origPrice.dec">{{ origPrice.dec }}</em></span></del>
              </div>
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
                >{{ valOf(v) }}</button>
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

            <!-- 购买栏：购物车图标块 + 主按钮「立即购买」+ 收藏 / 联系卖家小按钮 -->
            <div class="buy-bar">
              <div class="buy-main">
                <button class="buy-cart" data-action="add-cart" @click="addCart" title="加入购物车">
                  <svg class="buy-cart-icon" viewBox="0 0 24 24" aria-hidden="true">
                    <circle cx="9" cy="20.4" r="1.3" />
                    <circle cx="18" cy="20.4" r="1.3" />
                    <path d="M2.6 3h1.9l2.5 11.7a2 2 0 0 0 1.96 1.6h8.88a2 2 0 0 0 1.96-1.58L21 7.4H5.3" />
                    <path d="M12.4 9.5v3.6M10.6 11.3h3.6" />
                  </svg>
                  <span class="buy-cart-text">加入购物车</span>
                </button>
                <button class="buy-now" data-action="buy-now" @click="buyNow">立即购买</button>
              </div>
              <div class="buy-mini">
                <button class="buy-icon" :class="{ on: faved }" data-action="toggle-fav" :data-id="product.id" :title="faved ? '取消收藏' : '收藏商品'">
                  <span class="buy-icon-glyph">{{ faved ? '♥' : '♡' }}</span><span>收藏</span>
                </button>
                <button class="buy-icon" data-action="goto-chat" :data-id="serviceId" title="联系卖家">
                  <span class="buy-icon-glyph">◌</span><span>联系</span>
                </button>
              </div>
            </div>
            <div class="service-row detail-service"><span>正品保障</span><span>极速发货</span><span>7 天无忧退货</span></div>
          </div>

          <!-- 店铺信息 -->
          <aside class="detail-shop">
            <div class="shop-mini">
              <span class="shop-avatar" :style="{ background: shopInfo.color }">
                <img v-if="isImage(shopInfo.avatar)" class="avatar-img" :src="shopInfo.avatar" alt="" />
                <template v-else>{{ shopInfo.avatar || '店' }}</template>
              </span>
              <button class="shop-mini-meta" title="点击查看店铺信息" @click="openShopInfo">
                <b class="ellipsis">{{ shopInfo.shopName }}</b>
                <small>点击查看店铺信息</small>
              </button>
            </div>
            <div class="shop-stats">
              <div class="shop-stat"><b>{{ product.shop.score }}</b><span>店铺评分</span></div>
              <div class="shop-stat"><b>4.8</b><span>商品评分</span></div>
              <div class="shop-stat"><b>{{ sales(shopInfo.fans) }}</b><span>粉丝</span></div>
            </div>
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
              <button :class="{ active: tab === 'comment' }" data-tab="comment" @click="setTab('comment')">商品评价（{{ (product.comments || []).length }}）</button>
            </div>
            <div id="tabDesc" class="detail-desc" :class="{ hidden: tab !== 'desc' }">
              <p v-if="product.desc">{{ product.desc }}</p>
              <!-- 旧数据（无图文详情段落）：补一张渐变主图大图 -->
              <p v-if="!product.art.img && !detailBlocks.length" class="detail-art-big" style="margin-top:14px" v-html="artHtml(product.art, '120px')"></p>
              <!-- 图文详情段落：文字 / 图片穿插渲染 -->
              <template v-for="(b, i) in detailBlocks" :key="i">
                <div v-if="b.type === 'img'" class="detail-desc-img"><img :src="b.url" alt="" loading="lazy" /></div>
                <p v-else>{{ b.text }}</p>
              </template>
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
          <!-- 相关推荐为空时整块隐藏，避免只留一个「看了又看」空标题 -->
          <aside v-if="relatedList.length" class="detail-shop" style="margin-top:0">
            <h4>看了又看</h4>
            <div id="relatedList" style="display:flex;flex-direction:column;gap:10px" v-html="relatedHtml"></div>
          </aside>
        </div>
      </template>
    </div>
  </div>
</template>
