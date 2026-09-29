<script setup>
/* =========================================================
   青集市 · views/ReviewsView.vue —— 评价晒单（#/reviews）
   两个页签：
   · 待评价：GET /reviews/pending 返回「已完成」订单及其**商品条目**——每个条目就是
     一个具体款式（同一商品的不同款式各占一行），条目带 reviewed 标记（已评价的置灰）；
     点击「评价」打开评价弹窗（星级 + 内容 + 晒单图 + 匿名）→ POST /reviews（body 只传
     orderItemId，orderId / productId / sku 由服务端按条目推导并快照）；
   · 我的评价：GET /reviews/mine **一次返回本人全部评价（后端不分页）**，
     前端按**商品分类**（mineGroups：同一商品的不同款式评价归到一张商品卡下），
     每条都标注对应款式；
     可追评（POST /reviews/{id}/append）。
     评价发布后**不可删除、不可修改**：评分是商品评分与店铺评分的输入，
     允许删除评价等于允许「打差评 → 谈条件 → 删掉」的评分操纵。

   评分口径（全部由后端计算，前端只读、绝不上报）：
   · 商品评分 = 该商品**全部款式**评价的平均分（无评价 = 「暂无评分」）；
   · 店铺评分 = 该店铺全部商品评分的平均值。

   strict 策略：接口失败如实报错并显示错误提示 + 空态，绝不把评价写进浏览器存储
   冒充「已评价」——那样数据库里没有记录、刷新即消失、评分也永远不会变。
   入口：我的订单「已完成」→ 评价晒单、个人中心「评价晒单」、订单详情的
   「评价晒单」按钮（带 ?orderId= 时自动打开该订单的评价弹窗）。
   ========================================================= */
import { computed, onMounted, ref } from 'vue';
import QM_UI from '../core/ui.js';
import QM_API from '../core/api.js';
import useRouteCompat from '../composables/useRouteCompat.js';

const { esc, fullTime, artStyle, artHtml, toast, modal } = QM_UI;
const route = useRouteCompat();

/* 评分档位文案（与星级一一对应，0 不用） */
const STAR_TEXT = ['', '差评', '较差', '一般', '较好', '好评'];
const IMAGE_MAX = 6;
const IMAGE_MAX_SIZE = 10 * 1024 * 1024;

/* 页签：pending 待评价 / mine 我的评价 */
const tab = ref('pending');
/* 页面状态：loading 加载中 / ready 就绪 / error 接口失败（后端未实现或不可达） */
const phase = ref('loading');
const errorMsg = ref('');

const pendingOrders = ref([]);   // 已完成订单（含条目）
const pendingTotal = ref(0);
const mineList = ref([]);        // 我的评价（后端一次返回全部，不分页）
const mineTotal = ref(0);
const busy = ref(false);         // 防重复提交（弹窗内表单）

/* 待评价条目数（未评价的条目，页签角标） */
const pendingTodoCount = computed(() =>
  pendingOrders.value.reduce((n, o) => n + (o.items || []).filter(it => !it.reviewed).length, 0)
);

/* 「我的评价」分类：**按商品归类**（后端返回全部评价，分类在前端做）。
   同一商品的不同款式评价归到一张商品卡下，卡片内按评价时间倒序（后端已按 id 倒序下发），
   卡片之间按「该商品最新一条评价」排序 —— 也就是保持后端下发的先后顺序。 */
const mineGroups = computed(() => {
  const groups = new Map();
  mineList.value.forEach(r => {
    const pid = (r.product && r.product.id !== undefined && r.product.id !== null)
      ? r.product.id
      : (r.productId !== undefined && r.productId !== null ? r.productId : 'unknown');
    let g = groups.get(pid);
    if (!g) {
      g = {
        productId: pid,
        title: (r.product && r.product.title) || '商品',
        art: (r.product && r.product.art) || null,
        reviews: []
      };
      groups.set(pid, g);
    }
    g.reviews.push(r);
  });
  return Array.from(groups.values());
});

/* 星级文本：★×n + ☆×(5-n)，评价卡与弹窗共用 */
const stars = n => '★'.repeat(Math.max(0, Math.min(5, Number(n) || 0))) + '☆'.repeat(5 - Math.max(0, Math.min(5, Number(n) || 0)));

/* ---------- 数据加载（全部 strict：失败即报错，不做本地兜底） ---------- */
async function loadPending() {
  const d = await QM_API.reviews.pending({ page: 1, size: 20 });
  pendingOrders.value = d.orders || [];
  pendingTotal.value = d.total || 0;
}
async function loadMine() {
  /* 后端一次返回全部评价（不分页）——「按商品分类」由前端 mineGroups 完成 */
  const d = await QM_API.reviews.mine();
  mineList.value = d.list || [];
  mineTotal.value = d.total || 0;
}
async function refresh() {
  phase.value = 'loading';
  errorMsg.value = '';
  try {
    await Promise.all([loadPending(), loadMine()]);
    phase.value = 'ready';
    openFromQuery();
  } catch (e) {
    pendingOrders.value = [];
    mineList.value = [];
    errorMsg.value = (e && e.message) || '评价数据加载失败';
    phase.value = 'error';
  }
}

/* 从 #/reviews?orderId=xxx 进入（订单列表 / 订单详情的「评价晒单」）时，
   自动打开该订单待评价条目的评价弹窗；同一订单只自动打开一次 */
let autoOpened = false;
function openFromQuery() {
  if (autoOpened) return;
  const oid = route.value.query.orderId;
  if (!oid) return;
  const order = pendingOrders.value.find(o => String(o.id) === String(oid));
  if (!order) return;
  const item = (order.items || []).find(it => !it.reviewed);
  autoOpened = true;
  if (item) reviewModal(order, item);
}

/* ---------- 评价弹窗（星级 + 内容 + 晒单图 + 匿名） ---------- */
function reviewModal(order, item) {
  const m = modal(`
    <div>
      <h3>评价晒单</h3>
      <p class="modal-sub">
        ${esc(item.title)}
        <br><span class="rv-sku-badge" title="本次评价针对该款式">款式：${esc(item.sku || '默认')}</span>
        <small>订单号 ${esc(order.orderNo)}</small>
      </p>
      <div class="form-row">
        <label>评分</label>
        <div class="star-picker" id="rvStars">
          ${[1, 2, 3, 4, 5].map(i => `<button type="button" class="star" data-score="${i}" title="${STAR_TEXT[i]}">★</button>`).join('')}
          <span class="star-text" id="rvStarText">${STAR_TEXT[5]}</span>
        </div>
      </div>
      <div class="form-row">
        <label>评价内容</label>
        <textarea id="rvContent" rows="4" maxlength="500" placeholder="说说这件商品的使用感受（最多 500 字）"></textarea>
        <small class="form-tip" id="rvCount">0 / 500</small>
      </div>
      <div class="form-row">
        <label>晒单图</label>
        <div class="review-uploader">
          <div class="review-imgs" id="rvImgs"></div>
          <div class="review-upload-actions">
            <button type="button" class="btn btn-plain" id="rvPick">选择图片</button>
            <small>最多 ${IMAGE_MAX} 张，单张不超过 10MB；图片上传至阿里云 OSS</small>
          </div>
          <input type="file" id="rvFile" accept="image/*" multiple class="hidden" />
        </div>
      </div>
      <div class="form-row">
        <label>评价方式</label>
        <label class="checkbox-row"><input type="checkbox" id="rvAnon" /> 匿名评价（列表中显示「匿名用户」）</label>
      </div>
      <div class="modal-actions">
        <button class="btn btn-plain" data-close>取消</button>
        <button class="btn btn-primary" id="rvSubmit">发布评价</button>
      </div>
    </div>`, { wide: true });

  let score = 5;                 // 默认 5 星（好评）
  const images = [];             // 已上传的晒单图地址

  const starBtns = Array.from(m.root.querySelectorAll('#rvStars .star'));
  const paintStars = () => starBtns.forEach((b, i) => b.classList.toggle('on', i < score));
  starBtns.forEach(b => {
    b.onclick = () => {
      score = Number(b.dataset.score) || 5;
      paintStars();
      m.root.querySelector('#rvStarText').textContent = STAR_TEXT[score];
    };
  });
  paintStars();

  const contentEl = m.root.querySelector('#rvContent');
  contentEl.oninput = () => { m.root.querySelector('#rvCount').textContent = contentEl.value.length + ' / 500'; };

  const imgsEl = m.root.querySelector('#rvImgs');
  const renderImgs = () => {
    imgsEl.innerHTML = images.map((u, i) => `
      <span class="review-img">
        <img src="${esc(u)}" alt="晒单图" loading="lazy" />
        <button type="button" class="rv-del" data-i="${i}" title="移除">✕</button>
      </span>`).join('');
    imgsEl.querySelectorAll('.rv-del').forEach(b => {
      b.onclick = () => { images.splice(Number(b.dataset.i), 1); renderImgs(); };
    });
  };

  /* 选图即上传（POST /reviews/image），拿到 OSS 地址后进入待提交列表 */
  const fileEl = m.root.querySelector('#rvFile');
  m.root.querySelector('#rvPick').onclick = () => fileEl.click();
  fileEl.onchange = async () => {
    const files = Array.from(fileEl.files || []);
    fileEl.value = '';   // 允许再次选择同一文件
    for (const f of files) {
      if (images.length >= IMAGE_MAX) { toast(`晒单图最多 ${IMAGE_MAX} 张`, 'error'); break; }
      if (f.size > IMAGE_MAX_SIZE) { toast('单张图片不能超过 10MB', 'error'); continue; }
      try {
        const r = await QM_API.reviews.uploadImage(f);
        if (r && r.url) { images.push(r.url); renderImgs(); }
        else toast('图片上传失败：未返回地址', 'error');
      } catch (e) {
        toast((e && e.message) || '图片上传失败', 'error');
      }
    }
  };

  const submitBtn = m.root.querySelector('#rvSubmit');
  submitBtn.onclick = async () => {
    if (busy.value) return;
    if (!item.orderItemId) { toast('缺少订单条目信息，请刷新页面后重试', 'error'); return; }
    const content = contentEl.value.trim();
    if (!content && !images.length) { toast('请填写评价内容或上传晒单图', 'error'); return; }
    busy.value = true;
    submitBtn.disabled = true;
    submitBtn.textContent = '发布中…';
    try {
      await QM_API.reviews.create({
        /* 评价针对「订单条目」= 具体款式：同一商品买了两款就是两行条目、两条评价；
           orderId / productId / sku 全部由服务端按 orderItemId 推导并快照，前端不上报，
           避免评价被挂到别的款式上 */
        orderItemId: item.orderItemId,
        score,
        content,
        images: images.slice(),
        anonymous: !!m.root.querySelector('#rvAnon').checked
      });
      m.close();
      toast('评价发布成功，感谢你的分享 ♥', 'success');
      tab.value = 'mine';       // 发布后切到「我的评价」，让用户看到结果
      await refresh();
    } catch (e) {
      toast((e && e.message) || '评价发布失败，请稍后重试', 'error');
      submitBtn.disabled = false;
      submitBtn.textContent = '发布评价';
    } finally {
      busy.value = false;
    }
  };
}

/* ---------- 追评弹窗（每条评价一次，追评不改变评分） ---------- */
function appendModal(r) {
  const m = modal(`
    <div>
      <h3>追加评价</h3>
      <p class="modal-sub">${esc((r.product && r.product.title) || '')}<br><small>追评不改变已发布的评分</small></p>
      <div class="form-row">
        <label>追评内容</label>
        <textarea id="apContent" rows="4" maxlength="500" placeholder="用了一段时间后的感受（最多 500 字）"></textarea>
      </div>
      <div class="modal-actions">
        <button class="btn btn-plain" data-close>取消</button>
        <button class="btn btn-primary" id="apSubmit">发布追评</button>
      </div>
    </div>`, { wide: true });

  const btn = m.root.querySelector('#apSubmit');
  btn.onclick = async () => {
    if (busy.value) return;
    const content = m.root.querySelector('#apContent').value.trim();
    if (!content) { toast('请填写追评内容', 'error'); return; }
    busy.value = true;
    btn.disabled = true;
    btn.textContent = '提交中…';
    try {
      await QM_API.reviews.append(r.id, content);
      m.close();
      toast('已发布追评', 'success');
      await refresh();
    } catch (e) {
      toast((e && e.message) || '追评失败，请稍后重试', 'error');
      btn.disabled = false;
      btn.textContent = '发布追评';
    } finally {
      busy.value = false;
    }
  };
}

/* ---------- 评价不可删除 ----------
   接口不提供删除能力（契约见 docs/评价晒单接口文档.md）：评价是商品评分 / 店铺评分的输入，
   允许买家删除评价就等于允许评分操纵，也会让商品评分在短期内反复跳动。
   内容写错了用「追加评价」补充说明即可。 */

onMounted(refresh);
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 评价晒单</div>
        <h1>评价晒单</h1>
      </div>
      <a class="btn btn-plain" href="#/orders">← 返回我的订单</a>
    </div>

    <div class="order-tabs" id="reviewTabs">
      <button :class="{ active: tab === 'pending' }" @click="tab = 'pending'">
        待评价 <b>{{ pendingTodoCount || '' }}</b>
      </button>
      <button :class="{ active: tab === 'mine' }" @click="tab = 'mine'">
        我的评价 <b>{{ mineTotal || '' }}</b>
      </button>
    </div>

    <!-- 加载中 -->
    <div v-if="phase === 'loading'" class="order-card">
      <div class="empty-state">
        <div class="empty-icon">⏳</div>
        <h3>正在加载评价数据…</h3>
      </div>
    </div>

    <!-- 接口失败（后端未实现 / 不可达）：如实提示，不展示任何假数据 -->
    <div v-else-if="phase === 'error'" class="order-card">
      <div class="empty-state">
        <div class="empty-icon">🚧</div>
        <h3>评价功能暂时不可用</h3>
        <p>{{ errorMsg }}</p>
        <button class="btn btn-primary" @click="refresh">重新加载</button>
      </div>
    </div>

    <template v-else>
      <!-- ===== 待评价 ===== -->
      <div v-if="tab === 'pending'">
        <div v-for="o in pendingOrders" :key="o.id" class="order-card">
          <div class="order-head">
            <div class="oh-left">
              <span class="oh-time">{{ o.finishTime ? fullTime(o.finishTime).slice(0, 16) : '—' }} 完成</span>
              <span class="oh-no">订单号 {{ o.orderNo }}</span>
            </div>
            <span class="o-status done">已完成</span>
          </div>
          <div class="order-body review-order-body">
            <div v-for="(it, i) in (o.items || [])" :key="i" class="oi-row">
              <span class="oi-art" :style="artStyle(it.art)" v-html="artHtml(it.art)"></span>
              <div class="oi-info">
                <h4 class="ellipsis" :title="it.title">{{ it.title }}</h4>
                <div class="oi-meta">
                  <span class="oi-sku" :title="it.sku">款式：{{ it.sku || '默认' }}</span>
                  <span class="oi-qty">×{{ it.qty }}</span>
                </div>
              </div>
              <div class="oi-right">
                <button v-if="!it.reviewed" class="btn btn-primary btn-sm" @click="reviewModal(o, it)">评价晒单</button>
                <span v-else class="reviewed-tag">已评价</span>
              </div>
            </div>
          </div>
        </div>
        <div v-if="!pendingOrders.length" class="order-card">
          <div class="empty-state">
            <div class="empty-icon">🧾</div>
            <h3>暂无待评价订单</h3>
            <p>确认收货后，订单里的商品会出现在这里等待你的评价</p>
            <a class="btn btn-primary" href="#/orders">查看我的订单</a>
          </div>
        </div>
      </div>

      <!-- ===== 我的评价（后端返回全部评价，这里按商品分类展示） ===== -->
      <div v-else>
        <!-- 一张卡片 = 一件商品；卡片内逐条列出该商品各款式的评价 -->
        <div v-for="g in mineGroups" :key="g.productId" class="order-card review-group">
          <div class="review-card-head">
            <span class="review-card-art" :style="artStyle(g.art)" v-html="artHtml(g.art)"></span>
            <div class="review-card-title">
              <h4 class="ellipsis" :title="g.title">{{ g.title }}</h4>
              <div class="review-card-tags">
                <small>共 {{ g.reviews.length }} 条评价 · 每个款式分别评价</small>
              </div>
            </div>
          </div>

          <div v-for="r in g.reviews" :key="r.id" class="review-item">
            <div class="review-item-head">
              <b class="stars">{{ stars(r.score) }}</b>
              <small class="review-item-score-text">{{ STAR_TEXT[r.score] || '' }}</small>
              <span class="rv-sku-badge" title="这条评价对应的款式">款式：{{ r.sku || '默认' }}</span>
            </div>
            <div class="review-card-body">
              <p v-if="r.content" class="review-text">{{ r.content }}</p>
              <p v-else class="review-text review-text-empty">（该评价未填写文字内容）</p>
              <div v-if="r.images && r.images.length" class="review-imgs static">
                <span v-for="(u, i) in r.images" :key="i" class="review-img">
                  <img :src="u" alt="晒单图" loading="lazy" />
                </span>
              </div>
              <p class="review-meta">
                <small>{{ r.createdAt ? fullTime(r.createdAt) : '' }}<template v-if="r.orderNo"> · 订单号 {{ r.orderNo }}</template><template v-if="r.anonymous"> · 匿名评价</template></small>
              </p>
              <div v-if="r.append" class="review-append">
                <b>追评（{{ r.append.time ? fullTime(r.append.time).slice(0, 16) : '' }}）</b>
                <p>{{ r.append.content }}</p>
              </div>
              <div v-if="r.reply" class="review-reply">
                <b>商家回复<template v-if="r.reply.time">（{{ fullTime(r.reply.time).slice(0, 16) }}）</template></b>
                <p>{{ r.reply.content }}</p>
              </div>
            </div>
            <div class="order-foot">
              <div class="of-sum">
                <span class="of-count">评价编号 #{{ r.id }}</span>
              </div>
              <div class="o-actions">
                <button v-if="!r.append" class="btn btn-plain btn-sm" @click="appendModal(r)">追加评价</button>
                <span v-else class="reviewed-tag">已追评</span>
              </div>
            </div>
          </div>
        </div>
        <div v-if="!mineGroups.length" class="order-card">
          <div class="empty-state">
            <div class="empty-icon">⭐</div>
            <h3>还没有发表过评价</h3>
            <p>去「待评价」里分享你的使用体验，帮助更多人挑选好物</p>
            <button class="btn btn-primary" @click="tab = 'pending'">去评价</button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
