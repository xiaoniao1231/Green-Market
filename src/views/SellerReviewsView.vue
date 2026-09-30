<script setup>
/* =========================================================
   青集市 · views/SellerReviewsView.vue —— 我的店铺 · 评价管理
   两层结构：
   · 第一层「商品分组」：GET /reviews/shop/groups 返回本店每个商品的
     total / replied / unreplied / withAppend 统计，一张商品卡一行；
   · 第二层「单商品评价」：点击商品卡进入，GET /reviews/shop?productId=…
     主筛选：全部 / 未回复 / 已回复 / 用户追评；子筛选：全部 / 5 / 4 / 3 / 2 / 1 星。

   回复评价：POST /reviews/{id}/reply（每条一次，后端硬校验）。
   匿名评价对店家同样隐藏昵称（后端 toVO(..., false)）。
   ========================================================= */
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import QM_UI from '../core/ui.js';
import QM_API from '../core/api.js';
import useRouteCompat from '../composables/useRouteCompat.js';

const { esc, fullTime, artStyle, artHtml, toast, modal } = QM_UI;
const router = useRouter();
const route = useRouteCompat();

const STAR_TEXT = ['', '差评', '较差', '一般', '较好', '好评'];

/* ---------- 商品分组（第一层） ---------- */
const groups = ref([]);
const groupsPhase = ref('loading');
const groupsError = ref('');
/* 第一层筛选：商品名搜索 + 只看有未回复的商品 */
const keyword = ref('');
const onlyUnreplied = ref(false);
const filteredGroups = computed(() => {
  let arr = groups.value;
  if (onlyUnreplied.value) arr = arr.filter(g => g.unreplied > 0);
  const kw = keyword.value.trim().toLowerCase();
  if (kw) arr = arr.filter(g => (g.title || '').toLowerCase().includes(kw));
  return arr;
});

async function loadGroups() {
  groupsPhase.value = 'loading';
  groupsError.value = '';
  try {
    groups.value = await QM_API.reviews.shopGroups();
    groupsPhase.value = 'ready';
  } catch (e) {
    groups.value = [];
    groupsError.value = (e && e.message) || '商品分组加载失败';
    groupsPhase.value = 'error';
  }
}

/* ---------- 当前选中的商品（第二层） ---------- */
const current = ref(null);          // { productId, title, art }
const list = ref([]);
const total = ref(0);
const phase = ref('loading');
const errorMsg = ref('');
const busy = ref(false);

/* 主筛选：all 全部 / unreplied 未回复 / replied 已回复 / appended 用户追评 */
const replyStatus = ref('all');
/* 子筛选：星级，0 = 全部 */
const score = ref(0);

async function loadList() {
  if (!current.value) return;
  phase.value = 'loading';
  errorMsg.value = '';
  try {
    const d = await QM_API.reviews.shopList({
      productId: current.value.productId,
      replyStatus: replyStatus.value,
      score: score.value || undefined,
      size: 50
    });
    list.value = d.list || [];
    total.value = d.total || 0;
    phase.value = 'ready';
  } catch (e) {
    list.value = [];
    total.value = 0;
    errorMsg.value = (e && e.message) || '评价加载失败';
    phase.value = 'error';
  }
}

function openProduct(g) {
  /* URL 写入 ?productId=，让浏览器历史栈里有「分组 → 单商品」两条记录，
     用户点浏览器回退就能回到分组视图，而不是直接跳出评价管理页 */
  router.push({ path: '/seller/reviews', query: { productId: g.productId } });
}
function backToGroups() {
  /* 清空 query 回到分组视图；这次 push 也会写一条历史记录，
     用户再点一次浏览器回退会回到进入评价管理前的页面（店铺工作台） */
  router.push({ path: '/seller/reviews', query: {} });
}

/* URL query.productId 变化（包括浏览器前进 / 回退）时同步当前视图 */
watch(() => route.value.query.productId, pid => {
  if (pid) {
    const g = groups.value.find(x => String(x.productId) === String(pid));
    if (g) {
      current.value = g;
      replyStatus.value = 'all';
      score.value = 0;
      loadList();
      return;
    }
  }
  current.value = null;
  list.value = [];
  total.value = 0;
});

watch([replyStatus, score], () => { if (current.value) loadList(); });

onMounted(async () => {
  await loadGroups();
  /* 直接带 ?productId= 进入（刷新 / 外链）：分组加载完后再按 URL 同步一次视图 */
  const pid = route.value.query.productId;
  if (pid) {
    const g = groups.value.find(x => String(x.productId) === String(pid));
    if (g) {
      current.value = g;
      replyStatus.value = 'all';
      score.value = 0;
      loadList();
    }
  }
});

const stars = n => '★'.repeat(Math.max(0, Math.min(5, Number(n) || 0))) + '☆'.repeat(5 - Math.max(0, Math.min(5, Number(n) || 0)));

const REPLY_TABS = [
  { key: 'all', label: '全部' },
  { key: 'unreplied', label: '未回复' },
  { key: 'replied', label: '已回复' },
  { key: 'appended', label: '用户追评' }
];
const SCORE_OPTIONS = [0, 5, 4, 3, 2, 1].map(s => ({ key: s, label: s === 0 ? '全部星级' : s + ' 星' }));

/* ---------- 下拉筛选：评分星级 / 评论时间 ---------- */
const openDropdown = ref(null);   // 'score' | 'time' | null
const timeRange = ref('all');     // 'all' 或 'YYYY-MM-DD'

const dropPos = ref({ top: 0, left: 0 });
function toggleDropdown(key, e) {
  if (openDropdown.value === key) { openDropdown.value = null; return; }
  const el = (e && e.currentTarget) || (e && e.target);
  if (el && el.getBoundingClientRect) {
    const r = el.getBoundingClientRect();
    dropPos.value = { top: r.bottom + 6, left: r.left };
  }
  openDropdown.value = key;
}
function pickScore(s) { score.value = s; openDropdown.value = null; }
function pickTime(t) { timeRange.value = t; openDropdown.value = null; }
function onPickDate(e) {
  const v = e.target.value;
  if (v) { timeRange.value = v; openDropdown.value = null; }
}
/* 今天的 YYYY-MM-DD：date input 未筛选时也默认显示今天，而不是空白 */
function todayStr() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/* 评论时间在本地过滤：精确到当天 0 点 - 次日 0 点 */
const displayedList = computed(() => {
  if (timeRange.value === 'all') return list.value;
  const [y, m, d] = timeRange.value.split('-').map(Number);
  const start = new Date(y, m - 1, d, 0, 0, 0).getTime();
  const end = start + 24 * 3600 * 1000;
  return list.value.filter(r => (r.createdAt || 0) >= start && (r.createdAt || 0) < end);
});
const scoreLabel = computed(() => score.value === 0 ? '评分星级：全部' : '评分星级：' + score.value + ' 星');
const timeLabel = computed(() => timeRange.value === 'all' ? '评论时间：不限' : '评论时间：' + timeRange.value);

/* 回复弹窗 */
function openReply(r) {
  if (busy.value) return;
  if (r.reply) { toast('该评价已回复', 'error'); return; }
  const m = modal(`
    <div>
      <h3>回复评价</h3>
      <p class="modal-sub">
        ${esc(current.value ? current.value.title : '')}
        <br><span class="rv-sku-badge">款式：${esc(r.sku || '默认')}</span>
        <small>买家 ${esc((r.user && r.user.nickname) || '匿名用户')} · ${esc(r.content || '（未填写文字）')}</small>
      </p>
      <div class="form-row">
        <label>回复内容</label>
        <textarea id="replyContent" rows="4" maxlength="500" placeholder="礼貌、真诚地回应买家的反馈（最多 500 字）"></textarea>
        <small class="form-tip" id="replyCount">0 / 500</small>
      </div>
      <div class="modal-actions">
        <button class="btn btn-plain" data-close>取消</button>
        <button class="btn btn-primary" id="replySubmit">发布回复</button>
      </div>
    </div>`, { wide: true });

  const contentEl = m.root.querySelector('#replyContent');
  const countEl = m.root.querySelector('#replyCount');
  contentEl.oninput = () => { countEl.textContent = contentEl.value.length + ' / 500'; };

  m.root.querySelector('#replySubmit').onclick = async () => {
    const content = contentEl.value.trim();
    if (!content) { toast('请填写回复内容', 'error'); return; }
    busy.value = true;
    m.root.querySelector('#replySubmit').disabled = true;
    m.root.querySelector('#replySubmit').textContent = '发布中…';
    try {
      const updated = await QM_API.reviews.reply(r.id, content);
      r.reply = (updated && updated.reply) || { content, time: Date.now() };
      m.close();
      toast('回复已发布', 'success');
      /* 同步刷新商品分组卡上的已回复 / 未回复计数 */
      const g = groups.value.find(x => String(x.productId) === String(current.value.productId));
      if (g) { g.replied++; g.unreplied = Math.max(0, g.unreplied - 1); }
    } catch (e) {
      toast((e && e.message) || '回复失败，请稍后重试', 'error');
      m.root.querySelector('#replySubmit').disabled = false;
      m.root.querySelector('#replySubmit').textContent = '发布回复';
    } finally {
      busy.value = false;
    }
  };
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 我的店铺 / 评价管理</div>
        <h1>评价管理</h1>
      </div>
      <a v-if="current" class="btn btn-plain" @click="backToGroups()">← 返回商品分组</a>
      <a v-else class="btn btn-plain" href="#/seller">← 返回店铺工作台</a>
    </div>

    <!-- ============ 第一层：商品分组 ============ -->
    <template v-if="!current">
      <!-- 工具栏：商品名搜索 + 只看未回复 -->
      <div style="display:flex; gap:10px; align-items:center; margin-bottom:14px; flex-wrap:wrap">
        <input v-model="keyword" placeholder="搜索商品名称…"
               style="flex:1; min-width:220px; padding:8px 12px; border:1px solid #e5e7eb; border-radius:8px; font-size:14px" />
        <button class="btn btn-sm"
                :class="onlyUnreplied ? 'btn-primary' : 'btn-plain'"
                @click="onlyUnreplied = !onlyUnreplied">
          只看未回复 {{ groups.some(g => g.unreplied > 0) ? '(' + groups.reduce((n, g) => n + (g.unreplied > 0 ? 1 : 0), 0) + ')' : '' }}
        </button>
      </div>

      <div v-if="groupsPhase === 'loading'" class="order-card">
        <div class="empty-state"><div class="empty-icon">⏳</div><h3>正在加载本店商品…</h3></div>
      </div>
      <div v-else-if="groupsPhase === 'error'" class="order-card">
        <div class="empty-state">
          <div class="empty-icon">🚧</div>
          <h3>商品分组加载失败</h3>
          <p>{{ groupsError }}</p>
          <button class="btn btn-primary" @click="loadGroups">重新加载</button>
        </div>
      </div>
      <div v-else-if="!groups.length" class="order-card">
        <div class="empty-state">
          <div class="empty-icon">📭</div>
          <h3>还没有买家评价</h3>
          <p>买家确认收货并发表评价后，会按商品聚合出现在这里。</p>
        </div>
      </div>
      <div v-else-if="!filteredGroups.length" class="order-card">
        <div class="empty-state">
          <div class="empty-icon">🔍</div>
          <h3>没有匹配的商品</h3>
          <p>换个关键词或关掉「只看未回复」试试</p>
        </div>
      </div>
      <div v-else class="order-card" v-for="g in filteredGroups" :key="g.productId"
           style="cursor:pointer; padding:10px 14px; display:flex; align-items:center; gap:12px"
           @click="openProduct(g)">
        <span class="review-card-art" style="width:44px; height:44px; flex:none; border-radius:6px" :style="artStyle(g.art)" v-html="artHtml(g.art)"></span>
        <div style="flex:1; min-width:0">
          <h4 class="ellipsis" :title="g.title" style="margin:0 0 4px; font-size:14px">{{ g.title }}</h4>
          <small style="color:#888">共 {{ g.total }} 条评价</small>
        </div>
        <div style="display:flex; gap:14px; font-size:13px; flex:none">
          <span>已回复 <b>{{ g.replied }}</b></span>
          <span :style="g.unreplied ? 'color:#ff5000; font-weight:600' : 'color:#888'">未回复 <b>{{ g.unreplied }}</b></span>
          <span style="color:#888">追评 <b>{{ g.withAppend }}</b></span>
        </div>
      </div>
    </template>

    <!-- ============ 第二层：单商品评价列表 ============ -->
    <template v-else>
      <!-- 主筛选 -->
      <div style="display:flex; align-items:center; gap:12px; margin-bottom:16px; flex-wrap:wrap">
        <div class="order-tabs" style="margin:0">
          <button v-for="t in REPLY_TABS" :key="t.key"
                  :class="{ active: replyStatus === t.key }"
                  @click="replyStatus = t.key">{{ t.label }}</button>
        </div>
        <!-- 下拉：评分星级 -->
        <div style="position:relative">
          <button class="btn btn-plain btn-sm" @click.stop="toggleDropdown('score', $event)">
            {{ scoreLabel }} <span style="opacity:.6">▾</span>
          </button>
          <div v-if="openDropdown === 'score'"
               :style="{ position: 'fixed', top: dropPos.top + 'px', left: dropPos.left + 'px', minWidth: '140px', background: '#fff', border: '1px solid #e5e7eb', borderRadius: '8px', boxShadow: '0 8px 24px rgba(0,0,0,.1)', zIndex: 99999, overflow: 'hidden' }">
            <button v-for="s in SCORE_OPTIONS" :key="s.key"
                    style="display:block; width:100%; text-align:left; padding:8px 14px; border:0; background:transparent; cursor:pointer; font-size:13px"
                    :style="{ background: score === s.key ? '#fff3ec' : 'transparent', color: score === s.key ? '#ff5000' : 'inherit' }"
                    @click="pickScore(s.key)">{{ s.label }}</button>
          </div>
        </div>
        <!-- 下拉：评论时间 -->
        <div style="position:relative">
          <button class="btn btn-plain btn-sm" @click.stop="toggleDropdown('time', $event)">
            {{ timeLabel }} <span style="opacity:.6">▾</span>
          </button>
          <div v-if="openDropdown === 'time'"
               :style="{ position: 'fixed', top: dropPos.top + 'px', left: dropPos.left + 'px', minWidth: '220px', background: '#fff', border: '1px solid #e5e7eb', borderRadius: '8px', boxShadow: '0 8px 24px rgba(0,0,0,.1)', zIndex: 99999, padding: '10px' }">
            <input type="date" :value="timeRange === 'all' ? todayStr() : timeRange"
                   @change="onPickDate"
                   @click.stop
                   style="display:block; width:100%; padding:6px 8px; border:1px solid #e5e7eb; border-radius:6px; font-size:13px; box-sizing:border-box" />
            <small v-if="timeRange !== 'all'" style="display:block; margin-top:6px; color:#888; font-size:12px; cursor:pointer" @click="pickTime('all')">清除筛选</small>
          </div>
        </div>
      </div>
      <!-- 点击空白处关闭下拉 -->
      <div v-if="openDropdown" style="position:fixed; inset:0; z-index:5" @click="openDropdown = null"></div>

      <div v-if="phase === 'loading'" class="order-card">
        <div class="empty-state"><div class="empty-icon">⏳</div><h3>正在加载评价…</h3></div>
      </div>
      <div v-else-if="phase === 'error'" class="order-card">
        <div class="empty-state">
          <div class="empty-icon">🚧</div>
          <h3>评价加载失败</h3><p>{{ errorMsg }}</p>
          <button class="btn btn-primary" @click="loadList">重新加载</button>
        </div>
      </div>
      <div v-else-if="!displayedList.length" class="order-card">
        <div class="empty-state">
          <div class="empty-icon">🧾</div>
          <h3>当前筛选下暂无评价</h3>
          <p>换个筛选条件看看</p>
        </div>
      </div>

      <div v-for="r in displayedList" :key="r.id" class="order-card review-group">
        <div class="review-item">
          <div class="review-item-head">
            <b class="stars">{{ stars(r.score) }}</b>
            <small class="review-item-score-text">{{ STAR_TEXT[r.score] || '' }}</small>
            <span class="rv-sku-badge">款式：{{ r.sku || '默认' }}</span>
            <span class="rv-sku-badge">买家：{{ (r.user && r.user.nickname) || '匿名用户' }}</span>
            <small class="review-item-time">{{ r.createdAt ? fullTime(r.createdAt) : '' }}</small>
          </div>
          <div class="review-card-body">
            <p v-if="r.content" class="review-text">{{ r.content }}</p>
            <p v-else class="review-text review-text-empty">（该评价未填写文字内容）</p>
            <div v-if="r.images && r.images.length" class="review-imgs static">
              <span v-for="(u, i) in r.images" :key="i" class="review-img">
                <img :src="u" alt="晒单图" loading="lazy" />
              </span>
            </div>
            <div v-if="r.append" class="review-append">
              <b>买家追评<template v-if="r.append.time">（{{ fullTime(r.append.time) }}）</template></b>
              <p>{{ r.append.content }}</p>
            </div>
            <div v-if="r.reply" class="review-reply">
              <b>我的回复<template v-if="r.reply.time">（{{ fullTime(r.reply.time) }}）</template></b>
              <p>{{ r.reply.content }}</p>
            </div>
          </div>
          <div class="order-foot">
            <div class="of-sum"><span class="of-count">评价 #{{ r.id }}</span></div>
            <div class="o-actions">
              <button v-if="!r.reply" class="btn btn-primary btn-sm" @click="openReply(r)">回复评价</button>
              <span v-else class="reviewed-tag">已回复</span>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
