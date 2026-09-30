<script setup>
/* =========================================================
   青集市 · views/AfterSalesView.vue —— 售后服务（#/after-sales）
   数据来源：QM_API.afterSales.*（后端接口）：
   · GET  /after-sales                      我的全部售后单（一次返回，不分页 / 不筛选）
   · POST /after-sales                      申请售后（orderId + items 明细，一个订单一条售后单）
   · POST /after-sales/image                上传凭证图（阿里云 OSS）
   · POST /after-sales/{id}/cancel          撤销申请（仅待商家处理）
   · POST /after-sales/{id}/ship            填写寄回物流（仅待买家寄回）

   **列表分类在前端**：后端把该账号的全部售后单一次性返回，页签（全部 / 处理中 / 已完成 /
   已拒绝 / 已撤销）与角标数量都由本页本地计算（inTab / localCounts），
   所以没有「切页签重新请求」这回事，也没有多余的计数接口。

   售后**按店铺（= 按订单）申请**：下单时已按店铺拆单（一个订单只属于一个店铺），
   所以一条售后单 = 一个订单，可含该订单里的多件商品（明细落 after_sale_items，
   接口以 items 数组下发）；不再对「订单里的某个商品款式」单独申请。
   金额口径：每件明细退款金额 = 该条目下单单价 × 售后件数（换货为 0），
   整单退款金额 = 各明细之和，由后端计算并定格；前端只提交订单号与类型 / 原因 / 说明，
   **绝不提交商品清单与金额** —— 整单售后，明细由后端按该订单的全部商品条目生成。

   入口与弹窗时机：我的订单「售后服务 / 售后进度」、订单详情「售后服务」、个人中心
   「售后服务」（均可带 ?orderId= 进入本页），以及本页顶部「申请售后」按钮（自行选择订单）。
   · 带 orderId 进来且该订单**还没有售后记录**（用户点的就是「售后服务」）→
     进页面**直接弹出整单「申请售后」窗口**；
   · 已有售后记录（用户点的是「售后进度」）→ **不自动弹窗**，
     进度弹窗由用户点列表里那笔售后的「查看进度」打开；
   · 撤销 / 被拒的售后单可在进度弹窗里点「重新申请」再发起一次。

   strict 策略：接口失败如实报错并显示错误提示 + 空态，**不做本地假成功** ——
   旧版把售后申请写进浏览器存储（提示「本机演示」），店家根本看不到，
   刷新后还会「凭空消失」。
   ========================================================= */
import { computed, onMounted, ref, watch } from 'vue';
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
/* 售后原因字典（与后端校验口径一致，非字典文本后端会拒收）
   后端长度上限 100 字：本字段前端是 select 单选，取值只能来自下面这几项（最长 7 字），
   不存在超长输入，因此不需要 maxlength；上限由后端把关 */
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
/* 时间线动作文案（后端只回 role + action code；售后不含双方留言，只有状态流转留痕） */
const ACTION_TEXT = {
  apply: '提交售后申请',
  approve: '商家同意',
  refuse: '商家拒绝',
  ship: '买家已寄回商品',
  receive: '商家确认收货并处理',
  cancel: '买家撤销申请'
};
/* 只有这三种订单状态可以申请售后（后端同样硬校验） */
const APPLICABLE_ORDER_STATUS = ['paid', 'shipped', 'done'];

const IMAGE_MAX = 6;
const IMAGE_MAX_SIZE = 100 * 1024 * 1024;

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
const list = ref([]);               // 我的全部售后单（后端一次返回，本地按页签分组）
const counts = ref({});             // 页签角标（由 list 本地统计，不额外请求接口）
const busy = ref(false);            // 防重复提交

/* 页签过滤：处理中 = pending + agreed + returned；已完成 = refunded + exchanged */
function inTab(a, key) {
  if (!key) return true;
  if (key === 'processing') return ['pending', 'agreed', 'returned'].includes(a.status);
  if (key === 'refunded') return a.status === 'refunded' || a.status === 'exchanged';
  return a.status === key;
}
const filtered = computed(() => list.value.filter(a => inTab(a, tab.value)));
/* 页签计数：全部在本地算（后端只返回全量列表，不做聚合） */
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
const actionText = l => ACTION_TEXT[l.action] || l.action || '处理记录';
/* 操作按钮可用性（前端按状态机判断，后端仍硬校验 —— 前端判断只是体验） */
const canCancel = a => a.status === 'pending';
const canShip = a => a.status === 'agreed' && a.type !== 'refund';
/* 撤销 / 被拒后可重新申请（复用同一条售后单，后端把状态重置回 pending 并重写明细） */
const canReapply = a => a.status === 'canceled' || a.status === 'refused';

/* ---------- 数据加载 ---------- */
async function refresh() {
  phase.value = 'loading';
  errorMsg.value = '';
  try {
    /* 后端一次性返回本账号的全部售后商品（不分页、不筛选），
       页签分类与角标在这里本地算 —— 切页签不再请求后端 */
    list.value = await QM_API.afterSales.list();
    counts.value = localCounts(list.value);
    phase.value = 'ready';
    /* 入口参数：带 orderId 且该订单还没售后记录时，直接弹申请窗口（进度窗不自动弹） */
    await autoOpenIfApplying();
  } catch (e) {
    list.value = [];
    counts.value = {};
    errorMsg.value = (e && e.message) || '售后数据加载失败';
    phase.value = 'error';
  }
}

/* ---------- 订单入口参数 ----------
   入口形如 #/after-sales?orderId=1（订单列表 / 订单详情带过来）。
   规则：
   · 该订单**还没有售后记录** —— 用户点的就是订单页的「售后服务」，进入本页直接弹出
     整单「申请售后」窗口；
   · 该订单**已有售后记录** —— 用户点的是「售后进度」，此时**不自动弹窗**，
     要看进度就点列表里那笔售后的「查看进度」；
   · 不带 orderId（个人中心入口 / 直接访问）：不弹窗，用户点顶部「申请售后」选订单。 */
let entryAutoDone = false;       // 同一次进入只自动尝试一次

/* 该订单已有的售后记录（售后粒度 = 订单，一个订单至多一条） */
function afterSaleOfOrder(orderId) {
  return list.value.find(a => String(a.orderId) === String(orderId)) || null;
}

/* 带订单号进来且该订单还没售后记录 → 直接弹出整单申请窗口；其余情况不自动弹 */
async function autoOpenIfApplying() {
  if (entryAutoDone) return;
  entryAutoDone = true;
  const oid = route.value.query.orderId;
  if (!oid || afterSaleOfOrder(oid)) return;
  await openEntry(oid, { quiet: true });
}

/* 打开某个订单的售后：已有记录 → 进度弹窗；没有记录 → 按订单状态决定能否申请 */
function openForOrder(order) {
  const exist = afterSaleOfOrder(order.id);
  if (exist) {
    /* 已有记录：进行中会先给一句提示；已撤销 / 被拒的可在进度弹窗里点「重新申请」 */
    if (['pending', 'agreed', 'returned'].includes(exist.status)) toast('该订单已有进行中的售后', 'error');
    progressModal(exist);
    return;
  }
  if (!APPLICABLE_ORDER_STATUS.includes(order.status)) {
    toast('当前订单状态不支持申请售后', 'error');
    return;
  }
  applyModal(order, null);
}

/* 加载订单后打开售后入口；opts.quiet = 自动触发时不弹加载失败的错误提示 */
async function openEntry(orderId, opts = {}) {
  try {
    const order = await QM_API.orders.get(orderId);
    if (!order || !order.id) {
      if (!opts.quiet) toast('订单不存在或已失效', 'error');
      return;
    }
    openForOrder(order);
  } catch (err) {
    if (!opts.quiet) toast((err && err.message) || '订单加载失败', 'error');
  }
}

/* 重新申请：撤销 / 被拒后用同一个订单再发起一次（复用原售后单，后端重置状态与明细） */
async function reapplyForOrder(afterSale) {
  const orderId = afterSale && afterSale.orderId;
  if (orderId === undefined || orderId === null) return;
  let order;
  try {
    order = await QM_API.orders.get(orderId);
  } catch (e) {
    toast((e && e.message) || '订单加载失败', 'error');
    return;
  }
  if (!order || !order.id) { toast('订单不存在或已失效', 'error'); return; }
  applyModal(order, afterSale);
}

/* ---------- 选择订单（顶部「申请售后」入口） ----------
   售后按店铺申请：本平台下单即按店铺拆单，一个订单只属于一个店铺，
   所以这里列出「可申请售后的订单」，选中一个就是选中一个店铺。 */
async function chooseOrderModal() {
  let orders = [];
  try {
    orders = await QM_API.orders.list();
  } catch (e) {
    toast((e && e.message) || '订单加载失败', 'error');
    return;
  }
  const usable = (orders || []).filter(o => APPLICABLE_ORDER_STATUS.includes(o.status));
  const m = modal(`
    <div>
      <h3>选择要售后的订单</h3>
      <div class="as-pick">
        ${usable.map((o, i) => {
          const as = afterSaleOfOrder(o.id);
          const tag = as ? `<span class="o-tag">${esc((STATUS_TEXT[as.status] || {}).text || '售后中')}</span>` : '';
          const items = o.items || [];
          const first = items[0] || {};
          return `<button type="button" class="as-pick-row" data-i="${i}">
            <span class="as-pick-info">
              <b>订单 ${esc(o.orderNo)}</b>
              <small>${items.length} 件商品 · ${esc(first.title || '')}${items.length > 1 ? ' 等' : ''}</small>
              <small>实付 ${price(o.total)}</small>
            </span>
            ${tag}
          </button>`;
        }).join('') || '<p class="hint">没有可申请售后的订单（仅「待发货 / 待收货 / 已完成」的订单可申请）</p>'}
      </div>
      <div class="modal-actions"><button class="btn btn-plain" data-close>关闭</button></div>
    </div>`, { wide: true });
  m.root.querySelectorAll('.as-pick-row').forEach(btn => {
    btn.onclick = () => {
      const o = usable[Number(btn.dataset.i)];
      m.close();
      if (o) openForOrder(o);
    };
  });
}

/* ---------- 申请弹窗（订单级：整单售后 + 类型 / 原因 / 说明 / 凭证图） ---------- */
function applyModal(order, exist) {
  /* 已退款 / 已换货的订单不允许重复申请（后端同样拒绝，这里先给即时提示） */
  if (exist && ['refunded', 'exchanged'].includes(exist.status)) {
    toast('该订单售后已完成，不能重复申请', 'error');
    progressModal(exist);
    return;
  }
  /* 售后范围 = 该订单的全部商品（整单售后）：这里只做只读展示，不挑商品也不改件数 */
  const items = (order.items || []).map(it => ({
    productId: it.productId,
    sku: String(it.sku || '默认'),
    title: it.title || '',
    art: it.art || null,
    qty: Number(it.qty) || 1
  }));
  if (!items.length) { toast('该订单没有可申请售后的商品', 'error'); return; }
  const isReapply = !!(exist && ['canceled', 'refused'].includes(exist.status));
  const m = modal(`
    <div>
      <h3>${isReapply ? '重新申请售后' : '申请售后'}</h3>
      <p class="modal-sub">
        订单号 ${esc(order.orderNo)}
        ${isReapply ? `<br><small>上次申请：${esc((STATUS_TEXT[exist.status] || {}).text || '')}</small>` : ''}
      </p>
      <div class="form-row">
        <label>售后商品</label>
        <div class="as-items static">
          ${items.map(it => `
            <div class="as-item">
              <span class="oi-art" style="${artStyle(it.art)}">${artHtml(it.art)}</span>
              <span class="as-item-info">
                <b class="ellipsis" title="${esc(it.title)}">${esc(it.title)}</b>
                <small>${esc(it.sku)} · ×${it.qty}</small>
              </span>
            </div>`).join('')}
        </div>
      </div>
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
        <label>补充说明</label>
        <textarea id="asDesc" rows="3" maxlength="2000" placeholder="选填，最多 2000 字；描述问题有助于商家快速处理"></textarea>
        <small class="form-tip" id="asDescCount">0 / 2000</small>
      </div>
      <div class="form-row">
        <label>凭证图</label>
        <div class="review-uploader">
          <div class="review-imgs" id="asImgs"></div>
          <div class="review-upload-actions">
            <button type="button" class="btn btn-plain" id="asPick">选择图片</button>
            <small>最多 ${IMAGE_MAX} 张，单张不超过 100MB</small>
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
  descEl.oninput = () => { m.root.querySelector('#asDescCount').textContent = descEl.value.length + ' / 2000'; };

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
      if (f.size > IMAGE_MAX_SIZE) { toast('单张图片不能超过 100MB', 'error'); continue; }
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
    const description = descEl.value.trim();
    if (type !== 'refund' && !description && !images.length) {
      toast('退货 / 换货建议填写说明或上传凭证图，便于商家核实', 'error');
      return;
    }
    busy.value = true;
    submitBtn.disabled = true;
    submitBtn.textContent = '提交中…';
    try {
      /* 不传 items：整单售后，后端按该订单的全部商品条目建明细并汇总金额。
         返回值不在这里消费 —— 提交成功后**不自动弹「售后进度」**，
         只给一句成功提示并刷新列表，进度由用户点「查看进度」自行打开 */
      await QM_API.afterSales.create({
        orderId: order.id,
        type,
        reason: m.root.querySelector('#asReason').value,
        description,
        images: images.slice()
      });
      m.close();
      toast('售后申请已提交，等待商家处理', 'success');
      tab.value = '';
      await refresh();
    } catch (e) {
      toast((e && e.message) || '售后申请提交失败，请稍后重试', 'error');
      submitBtn.disabled = false;
      submitBtn.textContent = '提交申请';
    } finally {
      busy.value = false;
    }
  };
}

/* ---------- 进度弹窗（时间线 + 撤销 / 寄回） ---------- */
function progressModal(a) {
  const st = stOf(a);
  const m = modal(`
    <div>
      <h3>售后进度</h3>
      <p class="modal-sub">
        售后单号 ${esc(a.afterNo || a.id)} · 订单号 ${esc(a.orderNo)}
        <br><small>${a.shop && a.shop.name ? esc(a.shop.name) + ' · ' : ''}${a.items.length} 件商品 · 共 ${a.qty} 件</small>
      </p>
      <div class="as-items static">
        ${a.items.map(it => `
          <div class="as-item">
            <span class="oi-art" style="${artStyle(it.art)}">${artHtml(it.art)}</span>
            <span class="as-item-info">
              <b class="ellipsis" title="${esc(it.title)}">${esc(it.title)}</b>
              <small>${esc(it.sku || '默认')} · ×${it.qty}${it.refundAmount ? ' · 退款 ' + price(it.refundAmount) : ''}</small>
            </span>
          </div>`).join('') || '<p class="hint">该售后单没有商品明细</p>'}
      </div>
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
      <div class="modal-actions">
        <button class="btn btn-plain" data-close>关闭</button>
        ${canReapply(a) ? '<button class="btn btn-primary" id="asReapply">重新申请</button>' : ''}
        ${canShip(a) ? '<button class="btn btn-primary" id="asShip">填写寄回物流</button>' : ''}
        ${canCancel(a) ? '<button class="btn btn-plain" id="asCancel">撤销申请</button>' : ''}
      </div>
    </div>`, { wide: true });

  const reapplyBtn = m.root.querySelector('#asReapply');
  if (reapplyBtn) reapplyBtn.onclick = () => { m.close(); reapplyForOrder(a); };

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
        <small class="form-tip">最多 50 字</small>
      </div>
      <div class="form-row">
        <label>运单号</label>
        <input id="asTrack" maxlength="50" placeholder="仅字母、数字与连字符" />
        <small class="form-tip">最多 50 字，仅字母、数字与连字符</small>
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
/* 已在售后页时又从订单带另一个 orderId 进来（路由 query 变化）：按新订单重新走一次入口规则 */
watch(() => route.value.query.orderId, () => {
  entryAutoDone = false;
  autoOpenIfApplying();
});
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 售后服务</div>
        <h1>售后服务</h1>
      </div>
      <div class="o-actions">
        <button class="btn btn-primary" @click="chooseOrderModal">申请售后</button>
        <a class="btn btn-plain" href="#/orders">← 返回我的订单</a>
      </div>
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
            <span v-if="a.shop && a.shop.name" class="oh-no">店铺 {{ a.shop.name }}</span>
          </div>
          <span class="o-status" :class="stOf(a).cls">{{ stOf(a).text }}</span>
        </div>

        <div class="order-body">
          <!-- 一条售后单 = 一个订单 = 一个店铺，可含该订单的多件商品：逐条列出明细 -->
          <div v-for="(it, i) in a.items" :key="i" class="oi-row">
            <span class="oi-art" :style="artStyle(it.art)" v-html="artHtml(it.art)"></span>
            <div class="oi-info">
              <h4 class="ellipsis" :title="it.title">{{ it.title }}</h4>
              <div class="oi-meta">
                <span class="oi-sku" :title="'款式：' + (it.sku || '默认')">款式：{{ it.sku || '默认' }}</span>
                <span class="oi-qty">×{{ it.qty }}</span>
                <span class="o-tag">{{ (AS_TYPES.find(t => t.key === a.type) || {}).label || a.type }}</span>
              </div>
            </div>
            <div class="oi-right">
              <span v-if="a.type !== 'exchange'" class="oi-price" v-html="price(it.refundAmount)"></span>
              <span v-else class="oi-qty">换货</span>
            </div>
          </div>
          <div v-if="!a.items.length" class="hint">该售后单没有商品明细</div>

          <div class="as-info">
            <div><span>商品件数</span><b>{{ a.items.length }} 件商品 · 共 {{ a.qty }} 件</b></div>
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
          <p>点上方「申请售后」选择订单即可（下单已按店铺拆单，一个订单就是一个店铺），也可以在「待发货 / 待收货 / 已完成」的订单里点「售后服务」</p>
          <a class="btn btn-primary" href="#/orders">查看我的订单</a>
        </div>
      </div>
    </template>
  </div>
</template>
