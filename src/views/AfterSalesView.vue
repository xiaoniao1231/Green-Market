<script setup>
/* =========================================================
   青集市 · views/AfterSalesView.vue —— 售后服务（#/after-sales）
   数据来源：QM_API.afterSales.*（后端接口）：
   · GET  /after-sales?status=&page=&size=  我的售后列表
   · GET  /after-sales/counts               各状态计数（页签角标）
   · POST /after-sales                      申请售后（orderId + productId + sku 定位订单条目）
   · POST /after-sales/image                上传凭证图（阿里云 OSS）
   · POST /after-sales/{id}/cancel          撤销申请（仅待商家处理）
   · POST /after-sales/{id}/ship            填写寄回物流（仅待买家寄回）
   · POST /after-sales/{id}/message         追加留言（不改状态）

   金额口径：退款金额 = 订单条目单价 × 售后件数，由后端计算并定格；
   前端只提交件数（上限 = 该条目下单数量），**绝不提交金额**。

   入口：我的订单「售后服务 / 售后进度」、订单详情的逐条商品按钮、
   个人中心「售后服务」（均可带 ?orderId=&productId=&sku= 直接打开申请弹窗）。

   strict 策略：接口失败如实报错并显示错误提示 + 空态，**不做本地假成功** ——
   旧版把售后申请写进浏览器存储（提示「本机演示」），店家根本看不到，
   刷新后还会「凭空消失」。
   ========================================================= */
import { computed, onMounted, ref } from 'vue';
import QM_UI from '../core/ui.js';
import QM_API from '../core/api.js';
import useRouteCompat from '../composables/useRouteCompat.js';

const { esc, price, fullTime, artStyle, artHtml, toast, modal, confirmDialog } = QM_UI;
const route = useRouteCompat();

/* 售后类型（与后端枚举一致：refund / return / exchange） */
const AS_TYPES = [
  { key: 'refund', label: '仅退款', desc: '不退货，直接退款' },
  { key: 'return', label: '退货退款', desc: '寄回商品后退款' },
  { key: 'exchange', label: '换货', desc: '寄回后重新发出' }
];
/* 售后原因字典（与后端校验口径一致，非字典文本后端会拒收） */
const AS_REASONS = ['不想要了', '商品质量问题', '发错货 / 少件', '与商品描述不符', '快递损坏或丢失', '其他原因'];

/* 状态文案 + 配色类（cls 对应 pages.css 的 .order-head .o-status.<cls>） */
const STATUS_TEXT = {
  pending: { text: '待商家处理', cls: 'pending' },
  agreed: { text: '待买家寄回', cls: 'paid' },
  returned: { text: '待商家收货', cls: 'shipped' },
  refunded: { text: '退款完成', cls: 'done' },
  exchanged: { text: '换货完成', cls: 'done' },
  refused: { text: '商家已拒绝', cls: 'canceled' },
  canceled: { text: '已撤销', cls: 'canceled' }
};
/* 时间线动作文案（后端只回 role + action code） */
const ACTION_TEXT = {
  apply: '提交售后申请',
  approve: '商家同意',
  refuse: '商家拒绝',
  ship: '买家已寄回商品',
  receive: '商家确认收货并处理',
  cancel: '买家撤销申请',
  message: '协商留言'
};
/* 只有这三种订单状态可以申请售后（后端同样硬校验） */
const APPLICABLE_ORDER_STATUS = ['paid', 'shipped', 'done'];

const IMAGE_MAX = 6;
const IMAGE_MAX_SIZE = 10 * 1024 * 1024;

/* 页签：'' 全部 / processing 处理中 / refunded 已完成 / refused 已拒绝 / canceled 已撤销 */
const TABS = [
  { key: '', label: '全部' },
  { key: 'processing', label: '处理中' },
  { key: 'refunded', label: '已完成' },
  { key: 'refused', label: '已拒绝' },
  { key: 'canceled', label: '已撤销' }
];

const tab = ref('');
const phase = ref('loading');       // loading / ready / error
const errorMsg = ref('');
const list = ref([]);               // 我的售后单（一次拉全，本地按页签分组）
const counts = ref({});             // 页签角标（GET /after-sales/counts）
const busy = ref(false);            // 防重复提交

/* 页签过滤：处理中 = pending + agreed + returned；已完成 = refunded + exchanged */
function inTab(a, key) {
  if (!key) return true;
  if (key === 'processing') return ['pending', 'agreed', 'returned'].includes(a.status);
  if (key === 'refunded') return a.status === 'refunded' || a.status === 'exchanged';
  return a.status === key;
}
const filtered = computed(() => list.value.filter(a => inTab(a, tab.value)));
/* 本地兜底计数（counts 接口不可用时用列表自己数） */
function localCounts(rows) {
  const c = { all: rows.length, processing: 0, refunded: 0, exchanged: 0, refused: 0, canceled: 0 };
  rows.forEach(a => {
    if (['pending', 'agreed', 'returned'].includes(a.status)) c.processing++;
    else if (a.status === 'refunded') c.refunded++;
    else if (a.status === 'exchanged') c.exchanged++;
    else if (a.status === 'refused') c.refused++;
    else if (a.status === 'canceled') c.canceled++;
  });
  return c;
}
const countOf = key => {
  const c = counts.value || {};
  if (!key) return Number(c.all) || 0;
  if (key === 'processing') return Number(c.processing) || 0;
  if (key === 'refunded') return (Number(c.refunded) || 0) + (Number(c.exchanged) || 0);
  return Number(c[key]) || 0;
};

const stOf = a => STATUS_TEXT[a.status] || STATUS_TEXT.pending;
const actionText = l => {
  const base = ACTION_TEXT[l.action] || l.action || '处理记录';
  if (l.action === 'message') return l.role === 'seller' ? '商家留言' : '买家留言';
  return base;
};
/* 操作按钮可用性（前端按状态机判断，后端仍硬校验 —— 前端判断只是体验） */
const canCancel = a => a.status === 'pending';
const canShip = a => a.status === 'agreed' && a.type !== 'refund';
const canMessage = a => !!a.status;

/* ---------- 数据加载 ---------- */
async function refresh() {
  phase.value = 'loading';
  errorMsg.value = '';
  try {
    const d = await QM_API.afterSales.list({ page: 1, size: 100 });
    list.value = d.list || [];
    /* 计数失败不阻塞列表（角标降级为列表本地统计） */
    try { counts.value = (await QM_API.afterSales.counts()) || localCounts(list.value); }
    catch (e2) { counts.value = localCounts(list.value); }
    phase.value = 'ready';
    await openFromQuery();
  } catch (e) {
    list.value = [];
    counts.value = {};
    errorMsg.value = (e && e.message) || '售后数据加载失败';
    phase.value = 'error';
  }
}

/* ---------- 从订单入口进来：自动打开申请弹窗 ---------- */
let autoOpened = false;
async function openFromQuery() {
  if (autoOpened) return;
  const oid = route.value.query.orderId;
  if (!oid) return;
  autoOpened = true;
  let order;
  try {
    order = await QM_API.orders.get(oid);
  } catch (e) {
    toast((e && e.message) || '订单加载失败', 'error');
    return;
  }
  if (!order || !order.id) { toast('订单不存在或已失效', 'error'); return; }
  const pid = route.value.query.productId;
  const sku = route.value.query.sku;
  const items = order.items || [];
  let item = null;
  if (pid) {
    item = items.find(it => String(it.productId) === String(pid) && (!sku || String(it.sku || '默认') === String(sku)))
      || items.find(it => String(it.productId) === String(pid));
  }
  /* ① 带商品进来：有售后记录就看进度，没记录才按订单状态决定能否申请 */
  if (item) {
    if (!afterSaleOfItem(order.id, item) && !APPLICABLE_ORDER_STATUS.includes(order.status)) {
      toast('当前订单状态不支持申请售后', 'error');
      return;
    }
    pickItem(order, item);
    return;
  }
  /* ② 只带订单号、且该订单已有售后：直接打开进行中那条的进度弹窗 */
  const existing = list.value.filter(a => String(a.orderId) === String(order.id));
  if (existing.length) {
    progressModal(existing.find(a => ['pending', 'agreed', 'returned'].includes(a.status)) || existing[0]);
    return;
  }
  /* ③ 订单状态校验通过后进入申请流程：多条商品时先让买家选一件 */
  if (!APPLICABLE_ORDER_STATUS.includes(order.status)) {
    toast('当前订单状态不支持申请售后', 'error');
    return;
  }
  chooseItemModal(order);
}

/* 该订单该条目已有的售后记录（按 productId + sku 匹配，前端不依赖后端条目 id） */
function afterSaleOfItem(orderId, item) {
  return list.value.find(a => String(a.orderId) === String(orderId)
    && String(a.productId) === String(item.productId)
    && String(a.sku || '默认') === String(item.sku || '默认')) || null;
}

/* 已有进行中的售后 → 直接看进度；已了结 / 已撤销 → 提示后可重新申请 */
function pickItem(order, item) {
  const exist = afterSaleOfItem(order.id, item);
  if (exist && ['pending', 'agreed', 'returned'].includes(exist.status)) {
    toast('该商品已有进行中的售后', 'error');
    progressModal(exist);
    return;
  }
  applyModal(order, item, exist);
}

/* 只带 orderId 进来（个人中心入口）：先让买家选一件商品 */
function chooseItemModal(order) {
  const items = order.items || [];
  const m = modal(`
    <div>
      <h3>选择售后商品</h3>
      <p class="modal-sub">订单号 ${esc(order.orderNo)} · 一次只能对一件商品申请售后</p>
      <div class="as-pick">
        ${items.map((it, i) => {
          const exist = afterSaleOfItem(order.id, it);
          const tag = exist ? `<span class="o-tag">${esc((STATUS_TEXT[exist.status] || {}).text || '售后中')}</span>` : '';
          return `<button type="button" class="as-pick-row" data-i="${i}">
            <span class="oi-art" style="${artStyle(it.art)}">${artHtml(it.art)}</span>
            <span class="as-pick-info"><b>${esc(it.title)}</b><small>${esc(it.sku || '默认')} · ×${it.qty}</small></span>
            ${tag}
          </button>`;
        }).join('') || '<p class="hint">该订单没有可申请售后的商品</p>'}
      </div>
      <div class="modal-actions"><button class="btn btn-plain" data-close>关闭</button></div>
    </div>`, { wide: true });
  m.root.querySelectorAll('.as-pick-row').forEach(btn => {
    btn.onclick = () => {
      const it = items[Number(btn.dataset.i)];
      m.close();
      if (it) pickItem(order, it);
    };
  });
}

/* ---------- 申请弹窗（类型 / 原因 / 件数 / 说明 / 凭证图） ---------- */
function applyModal(order, item, exist) {
  /* 已退款 / 已换货的条目不允许重复申请（后端同样拒绝，这里先给即时提示） */
  if (exist && ['refunded', 'exchanged'].includes(exist.status)) {
    toast('该商品售后已完成，不能重复申请', 'error');
    progressModal(exist);
    return;
  }
  const maxQty = Number(item.qty) || 1;
  const isReapply = !!(exist && ['canceled', 'refused'].includes(exist.status));
  const m = modal(`
    <div>
      <h3>${isReapply ? '重新申请售后' : '申请售后'}</h3>
      <p class="modal-sub">
        ${esc(item.title)}
        <br><small>${esc(item.sku || '默认')} · 订单号 ${esc(order.orderNo)}${isReapply ? ' · 上次申请：' + esc((STATUS_TEXT[exist.status] || {}).text || '') : ''}</small>
      </p>
      <div class="form-row">
        <label>售后类型</label>
        <div class="tag-chips" id="asTypes">
          ${AS_TYPES.map((t, i) => `<button type="button" class="tag-chip${i === 0 ? ' active' : ''}" data-type="${t.key}" title="${esc(t.desc)}">${t.label}</button>`).join('')}
        </div>
        <small class="form-tip" id="asTypeTip">${esc(AS_TYPES[0].desc)}</small>
      </div>
      <div class="form-row">
        <label>售后原因</label>
        <select id="asReason">${AS_REASONS.map(r => `<option value="${esc(r)}">${esc(r)}</option>`).join('')}</select>
      </div>
      <div class="form-row">
        <label>售后件数</label>
        <input type="number" id="asQty" min="1" max="${maxQty}" step="1" value="${maxQty}" />
        <small class="form-tip">本单该商品共 ${maxQty} 件，退款金额按件数 × 单价由服务端计算</small>
      </div>
      <div class="form-row">
        <label>补充说明</label>
        <textarea id="asDesc" rows="3" maxlength="500" placeholder="选填，最多 500 字；描述问题有助于商家快速处理"></textarea>
        <small class="form-tip" id="asDescCount">0 / 500</small>
      </div>
      <div class="form-row">
        <label>凭证图</label>
        <div class="review-uploader">
          <div class="review-imgs" id="asImgs"></div>
          <div class="review-upload-actions">
            <button type="button" class="btn btn-plain" id="asPick">选择图片</button>
            <small>最多 ${IMAGE_MAX} 张，单张不超过 10MB；图片上传至阿里云 OSS</small>
          </div>
          <input type="file" id="asFile" accept="image/*" multiple class="hidden" />
        </div>
      </div>
      <div class="modal-actions">
        <button class="btn btn-plain" data-close>再想想</button>
        <button class="btn btn-primary" id="asSubmit">提交申请</button>
      </div>
    </div>`, { wide: true });

  let type = AS_TYPES[0].key;
  const images = [];

  /* 类型切换：提示语跟着变（退货 / 换货需要寄回，仅退款不用） */
  m.root.querySelectorAll('#asTypes .tag-chip').forEach(el => {
    el.onclick = () => {
      m.root.querySelectorAll('#asTypes .tag-chip').forEach(x => x.classList.toggle('active', x === el));
      type = el.dataset.type;
      const t = AS_TYPES.find(x => x.key === type) || AS_TYPES[0];
      m.root.querySelector('#asTypeTip').textContent = t.desc;
    };
  });

  const descEl = m.root.querySelector('#asDesc');
  descEl.oninput = () => { m.root.querySelector('#asDescCount').textContent = descEl.value.length + ' / 500'; };

  /* 凭证图：选图即上传（POST /after-sales/image），拿到 OSS 地址后进待提交列表 */
  const imgsEl = m.root.querySelector('#asImgs');
  const renderImgs = () => {
    imgsEl.innerHTML = images.map((u, i) => `
      <span class="review-img">
        <img src="${esc(u)}" alt="凭证图" loading="lazy" />
        <button type="button" class="rv-del" data-i="${i}" title="移除">✕</button>
      </span>`).join('');
    imgsEl.querySelectorAll('.rv-del').forEach(b => {
      b.onclick = () => { images.splice(Number(b.dataset.i), 1); renderImgs(); };
    });
  };
  const fileEl = m.root.querySelector('#asFile');
  m.root.querySelector('#asPick').onclick = () => fileEl.click();
  fileEl.onchange = async () => {
    const files = Array.from(fileEl.files || []);
    fileEl.value = '';   // 允许再次选择同一文件
    for (const f of files) {
      if (images.length >= IMAGE_MAX) { toast(`凭证图最多 ${IMAGE_MAX} 张`, 'error'); break; }
      if (f.size > IMAGE_MAX_SIZE) { toast('单张图片不能超过 10MB', 'error'); continue; }
      try {
        const r = await QM_API.afterSales.uploadImage(f);
        if (r && r.url) { images.push(r.url); renderImgs(); }
        else toast('图片上传失败：未返回地址', 'error');
      } catch (e) {
        toast((e && e.message) || '图片上传失败', 'error');
      }
    }
  };

  const submitBtn = m.root.querySelector('#asSubmit');
  submitBtn.onclick = async () => {
    if (busy.value) return;
    const qty = Number(m.root.querySelector('#asQty').value);
    if (!Number.isInteger(qty) || qty < 1 || qty > maxQty) {
      toast(`售后件数必须是 1-${maxQty} 之间的整数`, 'error');
      return;
    }
    const description = descEl.value.trim();
    if (type !== 'refund' && !description && !images.length) {
      toast('退货 / 换货建议填写说明或上传凭证图，便于商家核实', 'error');
      return;
    }
    busy.value = true;
    submitBtn.disabled = true;
    submitBtn.textContent = '提交中…';
    try {
      const created = await QM_API.afterSales.create({
        orderId: order.id,
        productId: item.productId,
        sku: item.sku || '默认',
        type,
        reason: m.root.querySelector('#asReason').value,
        description,
        images: images.slice(),
        qty
      });
      m.close();
      toast('售后申请已提交，等待商家处理', 'success');
      tab.value = '';
      await refresh();
      if (created) progressModal(created);
    } catch (e) {
      toast((e && e.message) || '售后申请提交失败，请稍后重试', 'error');
      submitBtn.disabled = false;
      submitBtn.textContent = '提交申请';
    } finally {
      busy.value = false;
    }
  };
}

/* ---------- 进度弹窗（时间线 + 撤销 / 寄回 / 留言） ---------- */
function progressModal(a) {
  const st = stOf(a);
  const m = modal(`
    <div>
      <h3>售后进度</h3>
      <p class="modal-sub">
        售后单号 ${esc(a.afterNo || a.id)} · 订单号 ${esc(a.orderNo)}
        <br><small>${esc(a.title)} · ${esc(a.sku || '默认')} · ×${a.qty}</small>
      </p>
      <div class="as-detail">
        <div><span>售后类型</span><b>${esc((AS_TYPES.find(t => t.key === a.type) || {}).label || a.type)}</b></div>
        <div><span>售后原因</span><b>${esc(a.reason)}</b></div>
        <div><span>处理状态</span><b class="as-status">${esc(st.text)}</b></div>
        <div><span>退款金额</span><b>${a.type === 'exchange' ? '换货（不涉及退款）' : price(a.refundAmount)}</b></div>
        ${a.returnAddress ? `<div><span>寄回地址</span><b>${esc(a.returnAddress)}</b></div>` : ''}
        ${a.refuseReason ? `<div><span>拒绝原因</span><b>${esc(a.refuseReason)}</b></div>` : ''}
        ${a.sellerRemark ? `<div><span>商家备注</span><b>${esc(a.sellerRemark)}</b></div>` : ''}
        ${a.express ? `<div><span>我的寄回</span><b>${esc(a.express.company || '')} ${esc(a.express.trackingNo || '')}</b></div>` : ''}
        ${a.reship ? `<div><span>换货重发</span><b>${esc(a.reship.company || '')} ${esc(a.reship.trackingNo || '')}</b></div>` : ''}
      </div>
      ${a.images && a.images.length ? `<div class="review-imgs static as-imgs">${a.images.map(u => `<span class="review-img"><img src="${esc(u)}" alt="凭证图" loading="lazy" /></span>`).join('')}</div>` : ''}
      <h4 class="as-log-title">处理记录</h4>
      <div class="as-log">
        ${(a.logs || []).map(l => `
          <div class="as-log-item">
            <span class="as-log-dot ${esc(l.role || 'system')}"></span>
            <div>
              <b>${esc(actionText(l))}</b>
              ${l.content ? `<p>${esc(l.content)}</p>` : ''}
              <small>${l.time ? fullTime(l.time) : ''}</small>
            </div>
          </div>`).join('') || '<p class="hint">暂无处理记录</p>'}
      </div>
      <div class="form-row" style="margin-top:14px">
        <label>追加留言</label>
        <textarea id="asMsg" rows="2" maxlength="500" placeholder="补充说明或追问（最多 500 字）"></textarea>
      </div>
      <div class="modal-actions">
        <button class="btn btn-plain" data-close>关闭</button>
        <button class="btn btn-plain" id="asMsgSend">发送留言</button>
        ${canShip(a) ? '<button class="btn btn-primary" id="asShip">填写寄回物流</button>' : ''}
        ${canCancel(a) ? '<button class="btn btn-plain" id="asCancel">撤销申请</button>' : ''}
      </div>
    </div>`, { wide: true });

  const msgBtn = m.root.querySelector('#asMsgSend');
  msgBtn.onclick = async () => {
    const content = m.root.querySelector('#asMsg').value.trim();
    if (!content) { toast('留言内容不能为空', 'error'); return; }
    msgBtn.disabled = true;
    try {
      const updated = await QM_API.afterSales.message(a.id, content);
      m.close();
      toast('留言已发送');
      await refresh();
      if (updated) progressModal(updated);
    } catch (e) {
      toast((e && e.message) || '留言发送失败', 'error');
      msgBtn.disabled = false;
    }
  };

  const shipBtn = m.root.querySelector('#asShip');
  if (shipBtn) shipBtn.onclick = () => { m.close(); shipModal(a); };

  const cancelBtn = m.root.querySelector('#asCancel');
  if (cancelBtn) {
    cancelBtn.onclick = async () => {
      if (!await confirmDialog('撤销申请', '撤销后本次售后申请作废（可再次申请），确定撤销吗？', '撤销申请', true)) return;
      try {
        await QM_API.afterSales.cancel(a.id);
        m.close();
        toast('售后申请已撤销');
        await refresh();
      } catch (e) {
        toast((e && e.message) || '撤销失败', 'error');
      }
    };
  }
}

/* ---------- 填写寄回物流弹窗 ---------- */
function shipModal(a) {
  const m = modal(`
    <div>
      <h3>填写寄回物流</h3>
      <p class="modal-sub">
        售后单号 ${esc(a.afterNo || a.id)}
        ${a.returnAddress ? `<br><small>寄回地址：${esc(a.returnAddress)}</small>` : ''}
      </p>
      <div class="form-row">
        <label>快递公司</label>
        <input id="asCompany" maxlength="50" placeholder="如：顺丰速运" />
      </div>
      <div class="form-row">
        <label>运单号</label>
        <input id="asTrack" maxlength="50" placeholder="仅字母、数字与连字符" />
      </div>
      <div class="modal-actions">
        <button class="btn btn-plain" data-close>取消</button>
        <button class="btn btn-primary" id="asShipSubmit">提交</button>
      </div>
    </div>`, { wide: true });

  const btn = m.root.querySelector('#asShipSubmit');
  btn.onclick = async () => {
    const company = m.root.querySelector('#asCompany').value.trim();
    const trackingNo = m.root.querySelector('#asTrack').value.trim();
    if (!company) { toast('请填写快递公司', 'error'); return; }
    if (!trackingNo) { toast('请填写正确的运单号', 'error'); return; }
    btn.disabled = true;
    btn.textContent = '提交中…';
    try {
      await QM_API.afterSales.ship(a.id, { company, trackingNo });
      m.close();
      toast('寄回物流已提交，等待商家收货', 'success');
      await refresh();
    } catch (e) {
      toast((e && e.message) || '提交失败，请稍后重试', 'error');
      btn.disabled = false;
      btn.textContent = '提交';
    }
  };
}

onMounted(refresh);
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 售后服务</div>
        <h1>售后服务</h1>
      </div>
      <a class="btn btn-plain" href="#/orders">← 返回我的订单</a>
    </div>

    <div class="order-tabs" id="asTabs">
      <button
        v-for="t in TABS"
        :key="t.key"
        :class="{ active: tab === t.key }"
        @click="tab = t.key"
      >{{ t.label }} <b>{{ countOf(t.key) || '' }}</b></button>
    </div>

    <!-- 加载中 -->
    <div v-if="phase === 'loading'" class="order-card">
      <div class="empty-state">
        <div class="empty-icon">⏳</div>
        <h3>正在加载售后数据…</h3>
      </div>
    </div>

    <!-- 接口失败（后端未实现 / 不可达）：如实提示，不展示任何假数据 -->
    <div v-else-if="phase === 'error'" class="order-card">
      <div class="empty-state">
        <div class="empty-icon">🚧</div>
        <h3>售后服务暂时不可用</h3>
        <p>{{ errorMsg }}</p>
        <button class="btn btn-primary" @click="refresh">重新加载</button>
      </div>
    </div>

    <template v-else>
      <div v-for="a in filtered" :key="a.id" class="order-card">
        <div class="order-head">
          <div class="oh-left">
            <span class="oh-time">{{ a.createdAt ? fullTime(a.createdAt).slice(0, 16) : '—' }} 申请</span>
            <span class="oh-no">售后单号 {{ a.afterNo || a.id }}</span>
            <span class="oh-no">订单号 {{ a.orderNo }}</span>
          </div>
          <span class="o-status" :class="stOf(a).cls">{{ stOf(a).text }}</span>
        </div>

        <div class="order-body">
          <div class="oi-row">
            <span class="oi-art" :style="artStyle(a.art)" v-html="artHtml(a.art)"></span>
            <div class="oi-info">
              <h4 class="ellipsis" :title="a.title">{{ a.title }}</h4>
              <div class="oi-meta">
                <span class="oi-sku" :title="a.sku">{{ a.sku || '默认' }}</span>
                <span class="oi-qty">×{{ a.qty }}</span>
                <span class="o-tag">{{ (AS_TYPES.find(t => t.key === a.type) || {}).label || a.type }}</span>
              </div>
            </div>
            <div class="oi-right">
              <span v-if="a.type !== 'exchange'" class="oi-price" v-html="price(a.refundAmount)"></span>
              <span v-else class="oi-qty">换货</span>
              <span class="oi-qty">{{ a.shop && a.shop.name ? a.shop.name : '' }}</span>
            </div>
          </div>

          <div class="as-info">
            <div><span>售后原因</span><b>{{ a.reason }}</b></div>
            <div><span>处理状态</span><b class="as-status">{{ stOf(a).text }}</b></div>
            <div v-if="a.description"><span>补充说明</span><b>{{ a.description }}</b></div>
            <div v-if="a.returnAddress"><span>寄回地址</span><b>{{ a.returnAddress }}</b></div>
            <div v-if="a.refuseReason"><span>拒绝原因</span><b>{{ a.refuseReason }}</b></div>
            <div v-if="a.express"><span>我的寄回</span><b>{{ a.express.company }} {{ a.express.trackingNo }}</b></div>
            <div v-if="a.reship"><span>换货重发</span><b>{{ a.reship.company }} {{ a.reship.trackingNo }}</b></div>
          </div>

          <div v-if="a.images && a.images.length" class="review-imgs static as-imgs">
            <span v-for="(u, i) in a.images" :key="i" class="review-img">
              <img :src="u" alt="凭证图" loading="lazy" />
            </span>
          </div>

          <div v-if="a.logs && a.logs.length" class="as-log">
            <div v-for="(l, i) in a.logs" :key="i" class="as-log-item">
              <span class="as-log-dot" :class="l.role || 'system'"></span>
              <div>
                <b>{{ actionText(l) }}</b>
                <p v-if="l.content">{{ l.content }}</p>
                <small>{{ l.time ? fullTime(l.time) : '' }}</small>
              </div>
            </div>
          </div>
        </div>

        <div class="order-foot">
          <div class="of-sum">
            <span class="of-count">售后编号 #{{ a.id }}</span>
            <span class="of-extra">{{ a.finishTime ? '结束于 ' + fullTime(a.finishTime).slice(0, 16) : '处理中' }}</span>
          </div>
          <div class="o-actions">
            <button class="btn btn-plain btn-sm" @click="progressModal(a)">查看进度</button>
            <button v-if="canShip(a)" class="btn btn-primary btn-sm" @click="shipModal(a)">填写寄回物流</button>
            <button v-if="canCancel(a)" class="btn btn-plain btn-sm" @click="progressModal(a)">撤销申请</button>
            <a class="btn btn-plain btn-sm" :href="'#/order/' + a.orderId">查看订单</a>
          </div>
        </div>
      </div>

      <div v-if="!filtered.length" class="order-card">
        <div class="empty-state">
          <div class="empty-icon">📋</div>
          <h3>{{ tab ? '该状态下暂无售后单' : '还没有售后记录' }}</h3>
          <p>在「待发货 / 待收货 / 已完成」的订单里点「售后服务」即可发起申请</p>
          <a class="btn btn-primary" href="#/orders">查看我的订单</a>
        </div>
      </div>
    </template>
  </div>
</template>
