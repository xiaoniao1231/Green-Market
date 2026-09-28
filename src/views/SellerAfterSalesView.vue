<script setup>
/* =========================================================
   青集市 · views/SellerAfterSalesView.vue —— 我的店铺 · 售后管理
   数据来源：QM_API.seller.afterSales*（后端接口，strict）：
   · GET  /seller/after-sales?status=&afterNo=&page=&size=   本店售后列表
   · PUT  /seller/after-sales/{id}/approve  同意（仅退款 → 已退款；退货 / 换货 → 待买家寄回）
   · PUT  /seller/after-sales/{id}/refuse   拒绝（必须写原因，买家端会原样看到）
   · PUT  /seller/after-sales/{id}/receive  确认收货（退货退款 → 已退款；换货 → 换货完成，需重发物流）
   · POST /seller/after-sales/{id}/message  回复买家（不改状态）

   店铺身份由后端按 shops.owner_user_id 解析，前端不传 shopId；
   页签计数在已拉取的列表上本地统计（与订单管理页同一做法）。
   后端未实现（404）/ 不可达时提示失败 + 空态，不回退本地假数据。
   ========================================================= */
import { computed, onActivated, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import QM_UI from '../core/ui.js';
import QM_API from '../core/api.js';
import QM_STORE from '../core/store.js';
import useRouteCompat from '../composables/useRouteCompat.js';

const { esc, price, fullTime, artStyle, artHtml, toast, modal, confirmDialog, openShopCreate } = QM_UI;
const router = useRouter();
const route = useRouteCompat();

/* store 不是响应式对象：用 shopTick 版本号驱动「当前店铺」重算（开店 / 档案更新后自增） */
const shopTick = ref(0);
const svc = computed(() => { shopTick.value; return QM_STORE.seller.current(); });
const shopId = computed(() => (svc.value ? svc.value.id : ''));

const AS_TYPES = {
  refund: { label: '仅退款', desc: '同意后直接退款完成' },
  return: { label: '退货退款', desc: '买家寄回、商家收货后退款' },
  exchange: { label: '换货', desc: '买家寄回、商家重新发出' }
};
const STATUS_TEXT = {
  pending: { text: '待商家处理', cls: 'pending' },
  agreed: { text: '待买家寄回', cls: 'paid' },
  returned: { text: '待商家收货', cls: 'shipped' },
  refunded: { text: '已退款', cls: 'done' },
  exchanged: { text: '换货完成', cls: 'done' },
  refused: { text: '已拒绝', cls: 'canceled' },
  canceled: { text: '买家已撤销', cls: 'canceled' }
};
const ACTION_TEXT = {
  apply: '买家提交申请',
  approve: '商家同意',
  refuse: '商家拒绝',
  ship: '买家已寄回商品',
  receive: '商家确认收货并处理',
  cancel: '买家撤销申请',
  message: '协商留言'
};

/* 状态筛选：进页面时读 ?status=（工作台「待处理售后」卡可带它跳进来） */
const FILTERS = [
  { key: '', label: '全部' },
  { key: 'pending', label: '待处理' },
  { key: 'returned', label: '待收货' },
  { key: 'done', label: '已完成' },
  { key: 'refused', label: '已拒绝' }
];
const filter = ref(typeof route.value.query.status === 'string' ? route.value.query.status : '');
const afterNoKw = ref('');
const list = ref([]);
const loading = ref(false);
const busy = ref(false);

const stOf = a => STATUS_TEXT[a.status] || STATUS_TEXT.pending;
const typeOf = a => AS_TYPES[a.type] || { label: a.type || '售后', desc: '' };
const actionText = l => (l.action === 'message'
  ? (l.role === 'seller' ? '商家留言' : '买家留言')
  : (ACTION_TEXT[l.action] || l.action || '处理记录'));

/* 页签过滤：done = refunded + exchanged（已完成的两条终态合并展示） */
function inFilter(a, key) {
  if (!key) return true;
  if (key === 'done') return a.status === 'refunded' || a.status === 'exchanged';
  return a.status === key;
}
const filtered = computed(() => {
  const kw = afterNoKw.value.trim().toLowerCase();
  let base = list.value.filter(a => inFilter(a, filter.value));
  if (kw) base = base.filter(a => String(a.afterNo || a.id || '').toLowerCase().includes(kw));
  return base;
});
const statusCounts = computed(() => {
  const c = {};
  list.value.forEach(a => { c[a.status] = (c[a.status] || 0) + 1; });
  c.done = (c.refunded || 0) + (c.exchanged || 0);
  return c;
});
const countOf = key => (key ? (statusCounts.value[key] || 0) : list.value.length);
function setFilter(key) { filter.value = key; }
/* 按售后单号查询：回车或点「查询」再请求一次（后端支持 afterNo 时走服务端过滤） */
function searchByAfterNo() { refresh(); }
function clearAfterNo() { if (!afterNoKw.value) return; afterNoKw.value = ''; refresh(); }

/* 待处理 / 待收货数量：店家端顶部提示与工作台待办用得到 */
const pendingCount = computed(() => list.value.filter(a => a.status === 'pending').length);
const returnedCount = computed(() => list.value.filter(a => a.status === 'returned').length);

async function refresh() {
  if (!shopId.value) { list.value = []; return; }
  loading.value = true;
  try {
    const data = await QM_API.seller.afterSales({ page: 1, size: 100, afterNo: afterNoKw.value.trim() });
    list.value = (data && data.list) || [];
  } catch (e) {
    list.value = [];
    toast('售后列表加载失败：' + e.message, 'error');
  } finally {
    loading.value = false;
  }
}

/* ---------- 同意售后 ---------- */
function approveModal(a) {
  const isRefund = a.type === 'refund';
  const m = modal(`
    <div>
      <h3>同意售后</h3>
      <p class="modal-sub">
        售后单号 ${esc(a.afterNo || a.id)} · ${esc(typeOf(a).label)}
        <br><small>${esc(a.title)} · ${esc(a.sku || '默认')} ×${a.qty}${isRefund ? '' : ' · 退款金额 ' + price(a.refundAmount)}</small>
      </p>
      ${isRefund
        ? '<p class="seller-tip">仅退款：同意后本单直接进入「已退款」，无需买家寄回。</p>'
        : `<div class="form-row">
             <label>退货寄回地址</label>
             <input id="saAddr" maxlength="200" value="${esc('浙江省杭州市余杭区青集市仓储中心 售后组 0571-88888888')}" />
             <small class="form-tip">买家会看到该地址，按此寄回商品</small>
           </div>`}
      <div class="form-row">
        <label>处理备注</label>
        <textarea id="saRemark" rows="2" maxlength="200" placeholder="选填，买家可见（最多 200 字）"></textarea>
      </div>
      <div class="modal-actions">
        <button class="btn btn-plain" data-close>取消</button>
        <button class="btn btn-primary" id="saApprove">${isRefund ? '同意并退款' : '同意，等待买家寄回'}</button>
      </div>
    </div>`, { wide: true });

  const btn = m.root.querySelector('#saApprove');
  btn.onclick = async () => {
    if (busy.value) return;
    const addrEl = m.root.querySelector('#saAddr');
    const returnAddress = addrEl ? addrEl.value.trim() : '';
    if (!isRefund && !returnAddress) { toast('请填写退货寄回地址', 'error'); return; }
    busy.value = true;
    btn.disabled = true;
    btn.textContent = '处理中…';
    try {
      await QM_API.seller.afterSaleApprove(a.id, {
        returnAddress,
        remark: m.root.querySelector('#saRemark').value.trim()
      });
      m.close();
      toast(isRefund ? '已同意并完成退款' : '已同意，等待买家寄回', 'success');
      await refresh();
    } catch (e) {
      toast((e && e.message) || '操作失败，请稍后重试', 'error');
      btn.disabled = false;
      btn.textContent = isRefund ? '同意并退款' : '同意，等待买家寄回';
    } finally {
      busy.value = false;
    }
  };
}

/* ---------- 拒绝售后 ---------- */
function refuseModal(a) {
  const m = modal(`
    <div>
      <h3>拒绝售后</h3>
      <p class="modal-sub">售后单号 ${esc(a.afterNo || a.id)} · 拒绝原因会展示给买家</p>
      <div class="form-row">
        <label>拒绝原因</label>
        <textarea id="srReason" rows="3" maxlength="200" placeholder="如：商品已使用超过 7 天，且检测无质量问题"></textarea>
        <small class="form-tip" id="srCount">0 / 200</small>
      </div>
      <div class="modal-actions">
        <button class="btn btn-plain" data-close>取消</button>
        <button class="btn btn-primary" id="srSubmit">确认拒绝</button>
      </div>
    </div>`, { wide: true });

  const reasonEl = m.root.querySelector('#srReason');
  reasonEl.oninput = () => { m.root.querySelector('#srCount').textContent = reasonEl.value.length + ' / 200'; };

  const btn = m.root.querySelector('#srSubmit');
  btn.onclick = async () => {
    const reason = reasonEl.value.trim();
    if (!reason) { toast('请填写拒绝原因', 'error'); return; }
    btn.disabled = true;
    btn.textContent = '提交中…';
    try {
      await QM_API.seller.afterSaleRefuse(a.id, reason);
      m.close();
      toast('已拒绝该售后申请');
      await refresh();
    } catch (e) {
      toast((e && e.message) || '操作失败，请稍后重试', 'error');
      btn.disabled = false;
      btn.textContent = '确认拒绝';
    }
  };
}

/* ---------- 确认收货并处理（退货退款 → 退款；换货 → 重发） ---------- */
function receiveModal(a) {
  const isExchange = a.type === 'exchange';
  const m = modal(`
    <div>
      <h3>${isExchange ? '确认收货并换货' : '确认收货并退款'}</h3>
      <p class="modal-sub">
        售后单号 ${esc(a.afterNo || a.id)}
        <br><small>买家寄回：${esc((a.express && a.express.company) || '—')} ${esc((a.express && a.express.trackingNo) || '')}</small>
      </p>
      ${isExchange ? `
        <div class="form-row">
          <label>换货快递公司</label>
          <input id="scCompany" maxlength="50" placeholder="如：圆通速递" />
        </div>
        <div class="form-row">
          <label>换货运单号</label>
          <input id="scNo" maxlength="50" placeholder="仅字母、数字与连字符" />
        </div>` : `<p class="seller-tip">确认收货后，本单进入「已退款」，退款金额 ${price(a.refundAmount)}（台账记录，不接真实支付网关）。</p>`}
      <div class="form-row">
        <label>处理备注</label>
        <textarea id="scRemark" rows="2" maxlength="200" placeholder="选填，买家可见（最多 200 字）"></textarea>
      </div>
      <div class="modal-actions">
        <button class="btn btn-plain" data-close>取消</button>
        <button class="btn btn-primary" id="scSubmit">${isExchange ? '确认收货并发出换货' : '确认收货并退款'}</button>
      </div>
    </div>`, { wide: true });

  const btn = m.root.querySelector('#scSubmit');
  btn.onclick = async () => {
    if (busy.value) return;
    const payload = { remark: m.root.querySelector('#scRemark').value.trim() };
    if (isExchange) {
      payload.reshipCompany = m.root.querySelector('#scCompany').value.trim();
      payload.reshipNo = m.root.querySelector('#scNo').value.trim();
      if (!payload.reshipCompany) { toast('请填写换货重发的快递公司', 'error'); return; }
      if (!payload.reshipNo) { toast('请填写换货重发的运单号', 'error'); return; }
    }
    busy.value = true;
    btn.disabled = true;
    btn.textContent = '处理中…';
    try {
      await QM_API.seller.afterSaleReceive(a.id, payload);
      m.close();
      toast(isExchange ? '换货已发出' : '已确认收货并完成退款', 'success');
      await refresh();
    } catch (e) {
      toast((e && e.message) || '操作失败，请稍后重试', 'error');
      btn.disabled = false;
      btn.textContent = isExchange ? '确认收货并发出换货' : '确认收货并退款';
    } finally {
      busy.value = false;
    }
  };
}

/* ---------- 详情 / 回复买家 ---------- */
function detailModal(a) {
  const m = modal(`
    <div>
      <h3>售后详情</h3>
      <p class="modal-sub">
        售后单号 ${esc(a.afterNo || a.id)} · 订单号 ${esc(a.orderNo)}
        <br><small>${esc(a.title)} · ${esc(a.sku || '默认')} ×${a.qty}</small>
      </p>
      <div class="as-detail">
        <div><span>申请人</span><b>${esc((a.buyer && a.buyer.nickname) || a.userId || '—')}（${esc((a.buyer && a.buyer.userId) || a.userId || '')}）</b></div>
        <div><span>售后类型</span><b>${esc(typeOf(a).label)}</b></div>
        <div><span>处理状态</span><b class="as-status">${esc(stOf(a).text)}</b></div>
        <div><span>售后原因</span><b>${esc(a.reason)}</b></div>
        ${a.description ? `<div><span>补充说明</span><b>${esc(a.description)}</b></div>` : ''}
        <div><span>退款金额</span><b>${a.type === 'exchange' ? '换货（不涉及退款）' : price(a.refundAmount)}</b></div>
        ${a.returnAddress ? `<div><span>寄回地址</span><b>${esc(a.returnAddress)}</b></div>` : ''}
        ${a.express ? `<div><span>买家寄回</span><b>${esc(a.express.company || '')} ${esc(a.express.trackingNo || '')} ${a.express.time ? esc(fullTime(a.express.time).slice(0, 16)) : ''}</b></div>` : ''}
        ${a.reship ? `<div><span>换货重发</span><b>${esc(a.reship.company || '')} ${esc(a.reship.trackingNo || '')}</b></div>` : ''}
        ${a.refuseReason ? `<div><span>拒绝原因</span><b>${esc(a.refuseReason)}</b></div>` : ''}
        ${a.sellerRemark ? `<div><span>商家备注</span><b>${esc(a.sellerRemark)}</b></div>` : ''}
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
        <label>回复买家</label>
        <textarea id="sdMsg" rows="2" maxlength="500" placeholder="补充处理说明（最多 500 字）"></textarea>
      </div>
      <div class="modal-actions">
        <button class="btn btn-plain" data-close>关闭</button>
        <button class="btn btn-primary" id="sdSend">发送回复</button>
      </div>
    </div>`, { wide: true });

  const sendBtn = m.root.querySelector('#sdSend');
  sendBtn.onclick = async () => {
    const content = m.root.querySelector('#sdMsg').value.trim();
    if (!content) { toast('留言内容不能为空', 'error'); return; }
    sendBtn.disabled = true;
    try {
      await QM_API.seller.afterSaleMessage(a.id, content);
      m.close();
      toast('回复已发送');
      await refresh();
    } catch (e) {
      toast((e && e.message) || '发送失败，请稍后重试', 'error');
      sendBtn.disabled = false;
    }
  };
}

/* 未开店账号可直接在本页开店 */
function openShop() {
  if (!QM_STORE.state.user) { router.push({ path: '/login', query: { redirect: '/seller/after-sales' } }); return; }
  openShopCreate({ onDone: () => { shopTick.value++; refresh(); } });
}

let offShopProfile = null;
onMounted(() => {
  offShopProfile = QM_STORE.on('shopProfile', () => { shopTick.value++; refresh(); });
  refresh();
});
onActivated(refresh);
onBeforeUnmount(() => { if (offShopProfile) { offShopProfile(); offShopProfile = null; } });
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 我的店铺 / 售后管理</div>
        <h1>售后管理 <small>AFTER-SALES</small></h1>
      </div>
      <a class="btn btn-plain" href="#/seller">返回我的店铺</a>
    </div>

    <template v-if="svc">
      <div class="seller-tip">
        💡 当前店铺：<b>{{ svc.shopName }}</b>，共 {{ list.length }} 笔售后。
        仅退款同意后即退款完成；退货 / 换货需填写寄回地址，等买家寄回后再「确认收货」。
      </div>

      <div v-if="pendingCount || returnedCount" class="seller-tip urge">
        🔔
        <template v-if="pendingCount">有 <b>{{ pendingCount }}</b> 笔售后待处理</template>
        <template v-if="pendingCount && returnedCount">，</template>
        <template v-if="returnedCount">有 <b>{{ returnedCount }}</b> 笔退货已寄回待收货</template>
        —— 建议优先处理。
      </div>

      <!-- 状态筛选：从工作台「待处理售后」卡进来时会自动选中 -->
      <div class="tabs seller-order-tabs">
        <button v-for="f in FILTERS" :key="f.key" :class="{ active: filter === f.key }" @click="setFilter(f.key)">
          {{ f.label }}（{{ countOf(f.key) }}）
        </button>
      </div>

      <!-- 按售后单号查询：输入即时过滤，点「查询」再带单号请求后端 -->
      <div class="seller-order-search">
        <input v-model="afterNoKw" type="text" maxlength="32" placeholder="输入售后单号查询，如 AS20260930…"
               @keyup.enter="searchByAfterNo" />
        <button class="btn btn-primary btn-sm" @click="searchByAfterNo">查询</button>
        <button v-if="afterNoKw" class="btn btn-plain btn-sm" @click="clearAfterNo">清空</button>
        <small v-if="afterNoKw">单号含「{{ afterNoKw.trim() }}」的有 {{ filtered.length }} 笔</small>
      </div>

      <div v-if="loading" class="empty-state">
        <div class="empty-icon">⏳</div>
        <h3>正在加载售后单…</h3>
      </div>

      <template v-else>
        <div v-for="a in filtered" :key="a.id" class="seller-order-row"
             :class="'st-' + (a.status === 'pending' ? 'pending' : (a.status === 'returned' ? 'shipped' : (a.status === 'refunded' || a.status === 'exchanged' ? 'done' : 'canceled')))">
          <div class="seller-order-head">
            <span class="so-no">{{ a.afterNo || a.id }}</span>
            <span class="so-qty">{{ typeOf(a).label }}</span>
            <span class="o-time">订单号 {{ a.orderNo }}</span>
            <span class="o-time">{{ a.createdAt ? fullTime(a.createdAt).slice(0, 16) : '—' }}</span>
            <span class="o-status" :class="stOf(a).cls">{{ stOf(a).text }}</span>
          </div>

          <div class="seller-order-body">
            <div class="so-buyer">
              <div class="so-field"><span>申请人</span><b>{{ (a.buyer && a.buyer.nickname) || a.userId }}（{{ (a.buyer && a.buyer.userId) || a.userId }}）</b></div>
              <div class="so-field"><span>售后原因</span><b>{{ a.reason }}</b></div>
              <div v-if="a.description" class="so-field so-addr"><span>补充说明</span><b>{{ a.description }}</b></div>
              <div v-if="a.express" class="so-field"><span>买家寄回</span><b>{{ a.express.company }} {{ a.express.trackingNo }}</b></div>
              <div v-if="a.reship" class="so-field"><span>换货重发</span><b>{{ a.reship.company }} {{ a.reship.trackingNo }}</b></div>
              <div v-if="a.refuseReason" class="so-field so-addr"><span>拒绝原因</span><b>{{ a.refuseReason }}</b></div>
            </div>

            <div class="so-items">
              <div class="so-item">
                <span class="so-art" :style="artStyle(a.art)"><span v-html="artHtml(a.art)"></span></span>
                <div class="so-item-info">
                  <h4>{{ a.title }}</h4>
                  <small>{{ a.sku || '默认' }}</small>
                </div>
                <div class="so-item-price">
                  <span v-html="price(a.price)"></span>
                  <small>×{{ a.qty }}</small>
                </div>
              </div>
            </div>

            <div v-if="a.images && a.images.length" class="review-imgs static as-imgs">
              <span v-for="(u, i) in a.images" :key="i" class="review-img">
                <img :src="u" alt="凭证图" loading="lazy" />
              </span>
            </div>
          </div>

          <div class="seller-order-foot">
            <div class="so-sum">
              <span>退款金额 <b>{{ a.type === 'exchange' ? '—（换货）' : '¥' + Number(a.refundAmount || 0).toFixed(2) }}</b></span>
              <span v-if="a.returnAddress" class="so-discount">寄回地址 <b>{{ a.returnAddress }}</b></span>
            </div>
            <div class="o-actions">
              <button class="btn btn-plain btn-sm" @click="detailModal(a)">详情 / 回复</button>
              <template v-if="a.status === 'pending'">
                <button class="btn btn-primary btn-sm" :disabled="busy" @click="approveModal(a)">同意</button>
                <button class="btn btn-plain btn-sm" :disabled="busy" @click="refuseModal(a)">拒绝</button>
              </template>
              <template v-else-if="a.status === 'returned'">
                <button class="btn btn-primary btn-sm" :disabled="busy" @click="receiveModal(a)">
                  {{ a.type === 'exchange' ? '确认收货并换货' : '确认收货并退款' }}
                </button>
              </template>
              <template v-else-if="a.status === 'agreed'">
                <span class="so-wait">等待买家寄回商品</span>
              </template>
              <template v-else>
                <span class="so-wait">{{ stOf(a).text }}</span>
              </template>
            </div>
          </div>
        </div>

        <div v-if="!filtered.length" class="seller-order-row" style="text-align:center;color:var(--text-3);padding:28px 18px">
          {{ filter ? '没有「' + (FILTERS.find(f => f.key === filter) || {}).label + '」的售后单' : '暂无售后单，买家申请后会自动出现在这里' }}
        </div>
      </template>
    </template>

    <!-- 已登录但未开店：可直接开店 -->
    <div v-else-if="QM_STORE.state.user" class="empty-state">
      <div class="empty-icon">🏪</div>
      <h3>你还没有店铺</h3>
      <p>当前账号「{{ QM_STORE.state.user.nickname }}」名下还没有店铺。先开通店铺，即可处理售后。</p>
      <button class="btn btn-primary" @click="openShop">立即开店</button>
      <a class="btn btn-plain" href="#/home">先逛逛</a>
    </div>

    <!-- 未登录 -->
    <div v-else class="empty-state">
      <div class="empty-icon">🔐</div>
      <h3>请先登录</h3>
      <p>全站只有一套用户账号，登录后即可处理自己店铺的售后。</p>
      <a class="btn btn-primary" href="#/login?redirect=%2Fseller%2Fafter-sales">去登录</a>
    </div>
  </div>
</template>
