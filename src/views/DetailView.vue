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

const { artStyle, artHtml, sales, productCard, toast, avatarHtml, isImage, fullTime } = QM_UI;
const route = useRouteCompat();
const router = useRouter();

/* 三档可选外观（与 detail.js 的 VARIANTS 一致：原色 / 暖色渐变 / 冷色渐变） */
const VARIANTS = [
  { g: null, e: null },                    // 原色
  { g: ['#ffecd2', '#fcb69f'], e: null },  // 暖色
  { g: ['#a1c4fd', '#c2e9fb'], e: null }   // 冷色
];

const id = computed(() => route.value.params[0] || 'p01');

/* ---------- SKU 值兼容三种形态：字符串（旧数据）、{ v, img }（款式带图）、{ v, img, price }（款式带价） ---------- */
const valOf = v => (typeof v === 'string' ? v : (v && v.v) || '');
const valImg = v => (typeof v === 'string' ? '' : (v && v.img) || '');
/** 款式价：未设置（旧数据 / 留空）返回 null，表示沿用商品默认价 */
const valPrice = v => {
  const n = Number(typeof v === 'object' && v ? v.price : NaN);
  return Number.isFinite(n) && n > 0 ? n : null;
};
const moneyText = n => (Number.isInteger(Number(n)) ? String(Number(n)) : Number(n).toFixed(2));

/* ---------- 页面状态（对应原版 render + mount 阶段性输出） ---------- */
const phase = ref('loading');   // loading 加载中 / missing 商品不存在 / ready 已就绪
const product = ref(null);
const dTitle = ref('加载中…');
const selected = ref([]);       // 每组 SKU 当前选中值的下标（原版 selected[group]）
const qty = ref(1);
const variant = ref(0);         // 无图商品的图集外观（VARIANTS 下标，仅 art.img 为空时使用）
const lastSkuGroup = ref(null); // 最近点击的 SKU 组：有图商品展示图跟随款式切换
const tab = ref('desc');        // desc 图文详情 / spec 规格参数 / comment 商品评价
const relatedList = ref([]);
const favTick = ref(0);         // QM_STORE 非响应式：用版本号驱动收藏按钮重算

/* ---------- 商品评价（GET /products/{id}/reviews，公开接口，见 docs/评价晒单接口文档.md） ----------
   页面只读评分：商品评分 = 该商品**全部款式**评价的平均分（后端 products.rating，
   无评价为 null → 显示「暂无评分」）；店铺评分 = 该店铺全部商品评分的平均值
   （后端 shops.score，由商品对象里的 shop.score 下发）。
   ⭐ 评价粒度是「订单条目 = 具体款式」：每条评价都带 sku（款式文本），
   这里逐条显示款式标签，并支持按款式筛选（summary.skus），
   让买家能直接看自己关心的那个款式的评价，而不是把不同款式的好评差评混在一起。
   接口失败时如实提示并空态（strict），不使用任何本地演示评价。 */
const reviewPhase = ref('idle');   // idle / loading / ready / error
const reviews = ref([]);           // 当前页评价
const reviewTotal = ref(0);
const reviewSummary = ref(null);   // { rating, reviewCount, goodRate, distribution, shopScore, skus }
const reviewFilter = ref(0);       // 0 全部 / 5 五星 / 4 四星 / 3 三星
const reviewHasImage = ref(false); // 只看有晒单图
const reviewSku = ref('');         // 款式筛选（'' = 全部款式；值为款式文本，如「曜石黑 / 标准版」）
const reviewPage = ref(1);
const REVIEW_SIZE = 6;

/* 款式筛选条数据：来自 summary.skus（各款式的评价条数与平均分）；
   只有一个款式时不展示筛选条（没有筛选意义） */
const reviewSkuOptions = computed(() => {
  const s = reviewSummary.value;
  const list = (s && Array.isArray(s.skus)) ? s.skus.filter(g => g && g.sku) : [];
  return list.length > 1 ? list : [];
});

/* 星级文本（评价卡渲染：★×n + ☆×(5-n)） */
const stars = n => {
  const v = Math.max(0, Math.min(5, Number(n) || 0));
  return '★'.repeat(v) + '☆'.repeat(5 - v);
};

/* 商品评分文案：优先商品对象的 rating（后端冗余列），其次评价汇总，都没有 → 暂无评分 */
const productRatingText = computed(() => {
  const raw = product.value ? product.value.rating : null;
  const r = (raw === undefined || raw === null || raw === '') ? null : Number(raw);
  if (r !== null && Number.isFinite(r)) return r.toFixed(1);
  const s = reviewSummary.value;
  if (s && s.rating !== null && s.rating !== undefined) return Number(s.rating).toFixed(1);
  return '暂无评分';
});
/* 是否有评分（决定是否显示星级与好评率） */
const hasRating = computed(() => {
  const raw = product.value ? product.value.rating : null;
  if (raw !== undefined && raw !== null && raw !== '') return Number.isFinite(Number(raw));
  const s = reviewSummary.value;
  return !!(s && s.reviewCount > 0 && s.rating !== null);
});
/* 店铺评分文案：后端 shops.score = 本店全部商品评分的平均值（保留 2 位，页面上取 1 位） */
const shopScoreText = computed(() => {
  const s = (product.value && product.value.shop) ? Number(product.value.shop.score) : NaN;
  return Number.isFinite(s) && s > 0 ? s.toFixed(1) : '暂无评分';
});
/* 好评率：来自评价汇总（score >= 4 占比），无评价时显示「—」 */
const goodRate = computed(() => {
  const s = reviewSummary.value;
  return (s && s.reviewCount > 0) ? s.goodRate : 0;
});
/* 评分对应的星级（四舍五入，用于汇总栏的 ★ 展示；无评分时 0 星） */
const ratingStars = computed(() => {
  if (!hasRating.value) return 0;
  const s = reviewSummary.value;
  const raw = (s && s.rating !== null && s.rating !== undefined) ? s.rating
    : (product.value ? product.value.rating : null);
  return Math.round(Number(raw) || 0);
});
/* 是否还有下一页（「加载更多」按钮） */
const reviewHasMore = computed(() => reviews.value.length < reviewTotal.value);

/* 拉取评价列表：追加模式供「加载更多」复用 */
async function loadReviews(pid, mySeq, append = false) {
  if (!append) reviewPhase.value = 'loading';
  try {
    const d = await QM_API.reviews.listByProduct(pid, {
      page: reviewPage.value,
      size: REVIEW_SIZE,
      score: reviewFilter.value || undefined,
      hasImage: reviewHasImage.value,
      sku: reviewSku.value || undefined,   // 款式筛选：只看该款式的评价
      sort: 'new'
    });
    if (disposed || mySeq !== seq) return;
    reviews.value = append ? reviews.value.concat(d.list) : d.list;
    reviewTotal.value = d.total;
    reviewSummary.value = d.summary;
    reviewPhase.value = 'ready';
  } catch (e) {
    if (disposed || mySeq !== seq) return;
    if (!append) { reviews.value = []; reviewTotal.value = 0; reviewSummary.value = null; }
    reviewPhase.value = 'error';
  }
}
/* 切换筛选（评分档位 / 有图 / 款式）后回到第一页重新拉取 */
function setReviewFilter(score) {
  reviewFilter.value = score;
  reviewPage.value = 1;
  loadReviews(id.value, seq);
}
function toggleReviewImage() {
  reviewHasImage.value = !reviewHasImage.value;
  reviewPage.value = 1;
  loadReviews(id.value, seq);
}
/* 款式筛选：点「款式：xxx」标签只看该款式的评价，再点一次取消（回到全部款式） */
function setReviewSku(sku) {
  const next = String(sku || '');
  reviewSku.value = (reviewSku.value === next) ? '' : next;
  reviewPage.value = 1;
  loadReviews(id.value, seq);
}
function loadMoreReviews() {
  reviewPage.value += 1;
  loadReviews(id.value, seq, true);
}

/* 价格两段式拆分（复刻 ui.js price() 的输出结构，避免在 DOM 上多包一层） */
function priceParts(n) {
  n = Number(n || 0);
  const text = n % 1 === 0 ? String(n) : n.toFixed(2);
  const [int, dec] = text.split('.');
  return { int, dec: dec !== undefined ? '.' + dec : '' };
}

/* 当日秒杀价：后端判定为秒杀商品、且该账号今日还有资格时才有值（用完了就按到手价） */
const flashPrice = computed(() => {
  const p = product.value;
  if (!p || p.flashUsed === true) return null;
  const n = Number(p.flashPrice);
  return Number.isFinite(n) && n > 0 ? n : null;
});
/* 该账号今日已用完这件商品的秒杀价 */
const flashUsed = computed(() => !!(product.value && product.value.flashUsed === true));

/* 款式价（不含秒杀）：加入购物车按它计价，秒杀优惠在下单时按「限 1 件」体现 */
const skuUnitPrice = computed(() => {
  const p = product.value;
  if (!p) return 0;
  let unit = Number(p.price) || 0;
  (p.skus || []).forEach((g, gi) => {
    const sp = valPrice((g.values || [])[selected.value[gi]]);
    if (sp !== null) unit = sp;
  });
  return unit;
});

/* 成交价：秒杀商品展示秒杀价（限 1 件）；其余按当前选中的款式取价 —— 多个规格组都有款式价时，
   **靠后的组覆盖靠前的组**（即「最后一个设置了价格的已选款式」生效）；都没设款式价 → 用商品默认价。
   该规则与商品管理页、后端 priceMin / priceMax 计算保持一致，详见 docs/店家中心商品管理接口文档.md 1.3 */
const curUnitPrice = computed(() => (flashPrice.value !== null ? flashPrice.value : skuUnitPrice.value));
const curPrice = computed(() => priceParts(curUnitPrice.value));
/* 划线价：秒杀时划掉原到手价，平时划商品原价 */
const strikePrice = computed(() => {
  const p = product.value;
  if (!p) return 0;
  return flashPrice.value !== null ? (Number(p.price) || 0) : (Number(p.original) || 0);
});
const origPrice = computed(() => priceParts(strikePrice.value));

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
  return Object.assign({ shopName: p.shop.name, score: p.shop.score, fans: 0, founded: '' }, p.shop, s);
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
        <button class="btn btn-primary" data-action="goto-chat" data-id="${s.userId || s.id}" data-shop-id="${s.id || ''}">联系卖家</button>
        <button class="btn btn-plain" data-action="goto-shop" data-id="${s.shopName}" data-shop-id="${s.id || ''}">进店逛逛</button>
        <button class="btn btn-plain" data-close>关闭</button>
      </div>
    </div>`, { wide: true });
}

/* 关注 / 取消关注店铺：走后端 POST / DELETE /shops/{shopId}/follow，
   粉丝数以接口返回的 fans 为准（本地 shopFavs 只是关注态镜像） */
const shopFavBusy = ref(false);
async function toggleShopFav() {
  if (!requireLogin()) return;
  if (shopFavBusy.value) return;
  const p = product.value;
  const sid = (p && p.shop && (p.shop.id || p.shop.shopId)) || '';
  if (!sid) { toast('店铺信息缺失，请刷新页面后重试', 'error'); return; }
  const wasFaved = QM_STORE.shopFav.has(p.shop.name);
  shopFavBusy.value = true;
  try {
    const res = wasFaved ? await QM_API.shops.unfollow(sid) : await QM_API.shops.follow(sid);
    QM_STORE.shopFav.set(p.shop.name, !wasFaved);
    if (res && res.fans !== undefined && res.fans !== null) {
      QM_STORE.rememberShop({ id: sid, fans: res.fans });
      /* 详情页粉丝数取自 shopProfile（见 shopInfo），这里同时把 fans 落到商品自带的 shop 上，
         触发 computed 重算，避免只更新了本地档案但页面不刷新 */
      p.shop.fans = res.fans;
    }
    toast(wasFaved ? '已取消关注' : '已关注店铺 ♥', wasFaved ? '' : 'success');
  } catch (e) {
    toast((e && e.message) || '操作失败，请稍后重试', 'error');
  } finally {
    shopFavBusy.value = false;
  }
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

/* 展示图：跟随最近点击的 SKU 款式；未点击时显示第一个 SKU 的图 */
const mainArt = computed(() => {
  const p = product.value;
  if (!p) return null;
  /* 找所有 SKU 图 */
  const allImgs = [];
  p.skus.forEach((g, gi) => (g.values || []).forEach((v, vi) => {
    const img = valImg(v);
    if (img) allImgs.push({ kind: 'sku', group: gi, vi, img });
  }));
  if (!allImgs.length) {
    /* 无 SKU 图时回退渐变外观变体 */
    return thumbArtOf(VARIANTS[variant.value]);
  }
  /* 最近点击的 SKU 有图就用，否则用第一个 */
  const g = lastSkuGroup.value;
  if (g !== null && p.skus[g]) {
    const v = p.skus[g].values[selected.value[g]];
    const img = v && valImg(v);
    if (img) return { img };
  }
  return { img: allImgs[0].img };
});

/* 缩略图列表：只显示 SKU 图，无 SKU 图时显示渐变外观变体 */
const thumbs = computed(() => {
  const p = product.value;
  if (!p) return [];
  const list = [];
  const seen = new Set();
  p.skus.forEach((g, gi) => (g.values || []).forEach((v, vi) => {
    const img = valImg(v);
    if (img && !seen.has(img)) { seen.add(img); list.push({ kind: 'sku', group: gi, vi, img }); }
  }));
  if (!list.length) return VARIANTS.map((v, i) => ({ kind: 'variant', i }));
  return list;
});
/* 当前高亮缩略图：有 SKU 图时按最近点击款式定位，否则第一个；无 SKU 图时按变体 */
const currentThumb = computed(() => {
  const p = product.value;
  if (!p) return { kind: 'variant', i: 0 };
  const allImgs = [];
  p.skus.forEach((g, gi) => (g.values || []).forEach((v, vi) => {
    const img = valImg(v);
    if (img) allImgs.push({ kind: 'sku', group: gi, vi });
  }));
  if (!allImgs.length) return { kind: 'variant', i: variant.value };
  const g = lastSkuGroup.value;
  if (g !== null && p.skus[g]) {
    const v = p.skus[g].values[selected.value[g]];
    if (v && valImg(v)) return { kind: 'sku', group: g, vi: selected.value[g] };
  }
  return allImgs[0];
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
  /* 从 URL 预选款式（评价晒单 / 订单等入口带 ?sku=款式文本）：按「 / 」分段逐组匹配，
     命中即选中对应款式，并把主图 / 缩略图联动到该款式图；未命中保持默认第一项 */
  const skuParam = route.value.query.sku;
  if (skuParam) {
    const parts = String(skuParam).split(' / ').map(s => s.trim());
    selected.value = p.skus.map((g, gi) => {
      const want = parts[gi];
      if (!want) return 0;
      const idx = (g.values || []).findIndex(v => valOf(v) === want);
      return idx >= 0 ? idx : 0;
    });
    const hitGroup = selected.value.findIndex((vi, gi) => {
      const v = (p.skus[gi] && p.skus[gi].values) ? p.skus[gi].values[vi] : null;
      return !!(v && valImg(v));
    });
    if (hitGroup >= 0) lastSkuGroup.value = hitGroup;
  }
  dTitle.value = p.title;
  phase.value = 'ready';
  /* 浏览足迹：登录用户加载详情成功后向后端上报一次浏览（POST /footprints，
     body {productId}，userId 由后端从登录 token 取）。未登录 / 上报失败都不影响
     详情主流程（足迹只是顺带记录，不能因为上报失败拖垮商品详情）。 */
  if (QM_STORE.state && QM_STORE.state.user) {
    QM_API.footprints.record(pid).catch(() => { /* 足迹失败静默，详情照常 */ });
  }
  loadRelated(pid, mySeq);
  /* 评价与商品详情并行加载：评价接口（公开）失败不影响详情主体，只让评价页签显示错误态 */
  reviews.value = [];
  reviewTotal.value = 0;
  reviewPhase.value = 'idle';
  reviewPage.value = 1;
  reviewFilter.value = 0;
  reviewHasImage.value = false;
  reviewSku.value = '';
  reviewSummary.value = null;
  loadReviews(pid, mySeq);
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
  lastSkuGroup.value = group; // 有图商品：点击款式即切换展示图
}
function pickThumb(t) {
  if (t.kind === 'sku') { lastSkuGroup.value = t.group; selected.value[t.group] = t.vi; }
  else if (t.kind === 'variant') { variant.value = t.i; }
  else { lastSkuGroup.value = null; } // 点展示图缩略图 → 恢复商品展示图
}
function setTab(name) { tab.value = name; }
/* 每人限购件数：**只以后端下发为准**（商品详情里的 limitPerUser，预留字段）。
   后端没给 → 不限购、也不显示任何限购提示；给了才按它设上限并展示提示。 */
const limitPerUser = computed(() => {
  const p = product.value;
  if (!p) return 0;
  const n = Number(p.limitPerUser !== undefined ? p.limitPerUser : p.limit);
  return Number.isFinite(n) && n > 0 ? Math.floor(n) : 0;   // 0 表示不限购
});
function setQty(v) {
  const max = limitPerUser.value || Infinity;
  qty.value = Math.max(1, Math.min(max, v));
}
function stepQty(dir) { setQty(qty.value + dir); }
function onQtyInput(e) { setQty(parseInt(e.target.value, 10) || 1); }

async function addCart() {
  /* 第 4 个参数是「当前选中款式的到手价」：购物车按它计价（留空 / 无款式价时用商品默认价），
     秒杀优惠由后端在下单时按「限 1 件」抵扣。加购只走后端（strict）：失败如实报错，不做本地假加购。 */
  await QM_API.cart.add(product.value.id, skuText(), qty.value, skuUnitPrice.value);
  toast('已加入购物车 🛒', 'success');
}
async function buyNow() {
  await QM_API.cart.add(product.value.id, skuText(), qty.value, skuUnitPrice.value);
  /* 记录「本次立即购买」的目标商品：确认订单页据此精确结算，
     避免结算到购物车里最后一条（可能是无关的旧商品） */
  try {
    sessionStorage.setItem('qm_v2_buynow', JSON.stringify({ productId: product.value.id, sku: skuText() }));
  } catch (e) { /* 存储不可用时退化为确认订单页默认的结算行为 */ }
  router.push('/checkout'); // 原 QM_ROUTER.go('/cart')；结算已抽成确认订单页
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
          <!-- 图集（店家上传的展示图 / 表情+渐变，缩略图随款式联动） -->
          <div class="detail-gallery">
            <div class="g-main" id="gMain" :style="artStyle(mainArt)" v-html="artHtml(mainArt)"></div>
            <div class="g-thumbs">
              <template v-for="(t, i) in thumbs" :key="i">
                <!-- 无图商品：外观变体缩略图 -->
                <div v-if="t.kind === 'variant'" class="g-thumb" :class="{ active: currentThumb.kind === 'variant' && currentThumb.i === t.i }" data-action="thumb" :data-i="t.i" :style="artStyle(thumbArtOf(VARIANTS[t.i]))" v-html="artHtml(thumbArtOf(VARIANTS[t.i]))" @click="pickThumb(t)"></div>
                <!-- 有图商品：款式图缩略图 -->
                <div v-else-if="t.kind === 'sku'" class="g-thumb" :class="{ active: currentThumb.kind === 'sku' && currentThumb.group === t.group && currentThumb.vi === t.vi }" :title="valOf(product.skus[t.group].values[t.vi])" @click="pickThumb(t)"><img class="thumb-img" :src="t.img" alt="" loading="lazy" /></div>
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
                <span class="price-badge">{{ flashPrice !== null ? '秒杀价' : '到手价' }}</span>
                <span v-if="flashPrice !== null" class="flash-flag">限时秒杀 · 限 1 件</span>
                <span v-else-if="flashUsed" class="flash-flag used">今日秒杀价已用完，现按到手价</span>
                <del v-if="strikePrice > 0"><span class="price"><i>¥</i>{{ origPrice.int }}<em v-if="origPrice.dec">{{ origPrice.dec }}</em></span></del>
              </div>
              <div class="price-meta">
                <span>销量 {{ sales(product.sales) }}</span>
                <span>库存 {{ product.stock }} 件</span>
                <span>好评率 {{ hasRating ? goodRate + '%' : '—' }}</span>
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
                >{{ valOf(v) }}<em v-if="valPrice(v)" class="sku-chip-price">¥{{ moneyText(valPrice(v)) }}</em></button>
              </div>
            </div>

            <!-- 数量（限购件数由后端下发，未下发则不限购、不显示提示） -->
            <div class="detail-qty">
              <span>数量</span>
              <span class="stepper">
                <button data-action="qty" data-dir="-1" @click="stepQty(-1)">−</button>
                <input id="qtyInput" :value="qty" @input="onQtyInput" />
                <button data-action="qty" data-dir="1" @click="stepQty(1)">＋</button>
              </span>
              <span v-if="limitPerUser" class="pill pill-gray">每人限购 {{ limitPerUser }} 件</span>
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
                <button class="buy-icon" data-action="goto-chat" :data-id="serviceId" :data-shop-id="(product && product.shop && product.shop.id) || ''" title="联系卖家">
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
              <div class="shop-stat"><b>{{ shopScoreText }}</b><span>店铺评分</span></div>
              <div class="shop-stat"><b>{{ productRatingText }}</b><span>商品评分</span></div>
              <div class="shop-stat"><b>{{ sales(shopInfo.fans) }}</b><span>粉丝</span></div>
            </div>
            <button class="btn btn-ghost" :class="{ faved: shopFaved }" :disabled="shopFavBusy" @click="toggleShopFav">{{ shopFaved ? '♥ 已关注' : '♡ 关注店铺' }}</button>
            <button class="btn btn-ghost" data-action="goto-chat" :data-id="serviceId" :data-shop-id="(product && product.shop && product.shop.id) || ''">◌ 联系卖家</button>
            <button class="btn btn-plain" data-action="goto-shop" :data-id="product.shop.name" :data-shop-id="product.shop.id || ''">进店逛逛 →</button>
          </aside>
        </div>

        <!-- 详情 / 评价 / 相关推荐 -->
        <div class="detail-below">
          <div class="detail-panel">
            <div class="detail-tabs" id="detailTabs">
              <button :class="{ active: tab === 'desc' }" data-tab="desc" @click="setTab('desc')">图文详情</button>
              <button :class="{ active: tab === 'spec' }" data-tab="spec" @click="setTab('spec')">规格参数</button>
              <button :class="{ active: tab === 'comment' }" data-tab="comment" @click="setTab('comment')">商品评价（{{ reviewTotal }}）</button>
            </div>
            <div id="tabDesc" class="detail-desc" :class="{ hidden: tab !== 'desc' }">
              <p v-if="product.desc">{{ product.desc }}</p>
              <!-- 旧数据（无图文详情段落）：补一张渐变展示图大图 -->
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
              <!-- 评分汇总：商品评分 / 星级 / 评价数 / 好评率 / 店铺评分（全部来自后端） -->
              <div class="review-summary">
                <div class="rs-score">
                  <b>{{ productRatingText }}</b>
                  <span class="stars">{{ stars(ratingStars) }}</span>
                  <small>{{ (reviewSummary && reviewSummary.reviewCount) || 0 }} 条评价 · 好评率 {{ hasRating ? goodRate + '%' : '—' }}</small>
                  <small>店铺评分 {{ shopScoreText }}</small>
                </div>
                <div class="rs-filters">
                  <button :class="{ active: reviewFilter === 0 && !reviewHasImage && !reviewSku }" @click="setReviewFilter(0)">全部</button>
                  <button :class="{ active: reviewFilter === 5 }" @click="setReviewFilter(5)">5 星</button>
                  <button :class="{ active: reviewFilter === 4 }" @click="setReviewFilter(4)">4 星</button>
                  <button :class="{ active: reviewFilter === 3 }" @click="setReviewFilter(3)">3 星</button>
                  <button :class="{ active: reviewHasImage }" @click="toggleReviewImage">有图</button>
                </div>
              </div>

              <!-- 款式筛选：每个款式一行评价数据，方便买家只看自己关心的款式（评价粒度 = 订单条目/款式） -->
              <div v-if="reviewSkuOptions.length" class="rs-skus">
                <span class="rs-skus-label">按款式看评价</span>
                <button v-for="g in reviewSkuOptions" :key="g.sku"
                        class="rv-sku-badge clickable" :class="{ active: reviewSku === g.sku }"
                        :title="reviewSku === g.sku ? '取消款式筛选' : '只看「' + g.sku + '」的评价'"
                        @click="setReviewSku(g.sku)">
                  {{ g.sku }}（{{ g.count }}<template v-if="g.rating !== null"> · {{ Number(g.rating).toFixed(1) }} 分</template>）
                </button>
                <button v-if="reviewSku" class="rs-skus-clear" @click="setReviewSku(reviewSku)">显示全部款式</button>
              </div>

              <div v-if="reviewPhase === 'loading'" class="review-state">评价加载中…</div>
              <div v-else-if="reviewPhase === 'error'" class="review-state review-state-error">
                评价加载失败，请稍后重试
              </div>
              <template v-else>
                <div v-for="r in reviews" :key="r.id" class="comment-item">
                  <span class="c-avatar">{{ (r.user.nickname || '匿').slice(0, 1) }}</span>
                  <div>
                    <b>{{ r.user.nickname }} <span class="stars">{{ stars(r.score) }}</span></b>
                    <p v-if="r.content">{{ r.content }}</p>
                    <div v-if="r.images && r.images.length" class="review-imgs static">
                      <span v-for="(u, i) in r.images" :key="i" class="review-img"><img :src="u" alt="晒单图" loading="lazy" /></span>
                    </div>
                    <!-- 评价对应的款式：点一下只看该款式的评价（再点取消），方便他人定位同款反馈 -->
                    <div class="rv-item-tags">
                      <button class="rv-sku-badge clickable" :class="{ active: reviewSku === r.sku }"
                              :title="reviewSku === r.sku ? '取消款式筛选' : '只看「' + r.sku + '」的评价'"
                              @click="setReviewSku(r.sku)">款式：{{ r.sku }}</button>
                      <small>{{ r.createdAt ? fullTime(r.createdAt) : '' }}</small>
                    </div>
                    <div v-if="r.append" class="review-append">
                      <b>追评（{{ r.append.time ? fullTime(r.append.time).slice(0, 16) : '' }}）</b>
                      <p>{{ r.append.content }}</p>
                    </div>
                    <div v-if="r.reply" class="review-reply">
                      <b>商家回复<template v-if="r.reply.time">（{{ fullTime(r.reply.time).slice(0, 16) }}）</template></b>
                      <p>{{ r.reply.content }}</p>
                    </div>
                  </div>
                </div>
                <div v-if="!reviews.length" class="review-state">该筛选条件下暂无评价</div>
                <div v-if="reviewHasMore" class="review-more">
                  <button class="btn btn-plain" @click="loadMoreReviews">加载更多评价</button>
                </div>
              </template>
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
