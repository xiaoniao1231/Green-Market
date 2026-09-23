<script setup>
/* =========================================================
   青集市 · views/CartView.vue —— 购物车 + 结算下单（演示）
   移植自 mall-web/js/pages/cart.js（页面结构 / 交互逻辑不变）
   ========================================================= */
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import QM_API from '../core/api.js';
import QM_STORE from '../core/store.js';
import QM_UI from '../core/ui.js';

const { esc, price, artStyle, artHtml, toast, modal, confirmDialog } = QM_UI;
const router = useRouter();

/* ---------- SKU 工具（与 DetailView 同口径：值兼容字符串 / {v,img} / {v,img,price}） ---------- */
const valOf = v => (typeof v === 'string' ? v : (v && v.v) || '');
const valImg = v => (typeof v === 'string' ? '' : (v && v.img) || '');
const valPrice = v => {
  const n = Number(typeof v === 'object' && v ? v.price : NaN);
  return Number.isFinite(n) && n > 0 ? n : null;
};
const moneyText = n => (Number.isInteger(Number(n)) ? String(Number(n)) : Number(n).toFixed(2));
/* 生成规格文本：各组已选值用 ' / ' 拼接（与详情页加购口径一致） */
function skuTextOf(skus, pick) { return skus.map((g, gi) => valOf(g.values[pick[gi]])).join(' / '); }
/* 按选中款式计算成交价：靠后的款式价覆盖靠前；无款式价回退商品默认价（与详情页 curUnitPrice 同规则） */
function skuPriceOf(product, pick) {
  let unit = Number(product.price) || 0;
  (product.skus || []).forEach((g, gi) => {
    const sp = valPrice((g.values || [])[pick[gi]]);
    if (sp !== null) unit = sp;
  });
  return unit;
}
/* 按选中款式取展示图（与详情页 mainArt 同口径：最近点击的规格组有图就用它，
   否则第一个带图的选中组；都没有回退商品主图） */
function pickArtOf(product, pick, lastGroup) {
  const skus = (product && Array.isArray(product.skus)) ? product.skus : [];
  let first = null;
  for (let gi = 0; gi < skus.length; gi++) {
    const v = (skus[gi].values || [])[pick[gi]];
    const img = valImg(v);
    if (!img) continue;
    if (!first) first = { img };
    if (gi === lastGroup) return { img };
  }
  return first || (product && product.art) || null;
}

/* 原版 #cartTitle 文本（textContent）与 #cartRoot 容器（innerHTML 由 renderList 重建，
   与原版 mount 里对 view.querySelector('#cartRoot') 的写法保持一致） */
const cartTitle = ref('');
const cartRootEl = ref(null);

function cartRow(item) {
  const p = item.product;
  /* 条目图片：优先显示加购时选中的款式图（item.img），没有款式图才回退商品主图 */
  const art = (item.img && { img: item.img }) || (p && p.art) || null;
  /* 商品状态：已删除(deleted=1) / 已下架(onSale=0) 时，图片灰化并叠加状态标签 */
  const isDeleted = p && p.deleted === 1;
  const isOffShelf = !isDeleted && p && p.onSale === 0;
  const statusLabel = isDeleted ? '已删除' : (isOffShelf ? '已下架' : '');
  /* 可换款式：商品在售（未删除、未下架）且带规格、每组都有可选值；已下架/已删除的商品不再提供换款式 */
  const canChangeSku = p && !isDeleted && p.onSale !== 0
    && Array.isArray(p.skus) && p.skus.length
    && p.skus.every(g => Array.isArray(g.values) && g.values.length > 0);
  const ciArtCls = (isDeleted || isOffShelf) ? 'ci-art is-inactive' : 'ci-art';
  const statusBadge = statusLabel ? `<span class="ci-status-badge">${statusLabel}</span>` : '';
  return `
    <div class="cart-item" data-key="${esc(item.key)}">
      <span class="cart-check ${item.checked ? 'checked' : ''}" data-action="cart-check" data-key="${esc(item.key)}">${item.checked ? '✓' : ''}</span>
      <div class="ci-main">
        <span class="${ciArtCls}" style="${artStyle(art)}">${statusBadge}${artHtml(art)}</span>
        <div class="ci-info">
          <h4 class="ellipsis-2" data-action="open-product" data-id="${esc(p.id)}">${esc(p.title)}</h4>
          <span class="ci-sku">规格：${esc(item.sku)}${canChangeSku ? `<a class="ci-sku-edit" data-action="cart-sku" data-key="${esc(item.key)}">换款式</a>` : ''}</span>
        </div>
      </div>
      <span>${price(QM_STORE.cart.unitPrice(item))}</span>
      <span class="stepper">
        <button data-action="cart-qty" data-dir="-1" data-key="${esc(item.key)}">−</button>
        <input value="${item.qty}" data-action="cart-qty-input" data-key="${esc(item.key)}" />
        <button data-action="cart-qty" data-dir="1" data-key="${esc(item.key)}">＋</button>
      </span>
      <span>${price(QM_STORE.cart.subTotal(item))}</span>
      <button class="ci-del" data-action="cart-del" data-key="${esc(item.key)}">删除</button>
    </div>`;
}

/* 「去结算」按钮（原版在 mount 里对初始 #checkoutBtn 绑定一次；该按钮位于 #cartRoot 的
   innerHTML 中，重建后原节点会失效，这里改为每次 renderList 重建后重新绑定，
   保证任意勾选 / 改数量之后「去结算」仍然可用——外观与触发目标与原版一致） */
function bindCheckout() {
  const btn = cartRootEl.value && cartRootEl.value.querySelector('#checkoutBtn');
  if (btn) btn.onclick = () => {
    const selected = QM_STORE.cart.selected();
    if (selected.length) openCheckout(selected);
  };
}

/* 按店铺分组（保持首次出现顺序；无店铺信息归入「其他店铺」） */
function groupByShop(items) {
  const groups = [];
  const idx = new Map();
  items.forEach(it => {
    const name = (it.product && it.product.shop && it.product.shop.name) || '其他店铺';
    if (!idx.has(name)) { idx.set(name, groups.length); groups.push({ name, items: [] }); }
    groups[idx.get(name)].items.push(it);
  });
  return groups;
}

/* 每次渲染前先走接口同步（strict：购物车数据以后端为准，失败如实报错并显示空态，
   绝不用本地旧缓存冒充后端数据），成功后再按本地缓存渲染 */
async function renderList() {
  try {
    await QM_API.cart.list();
  } catch (e) {
    /* strict：接口失败不展示本地旧缓存（列表 / 角标同步归零），并给出明确原因 */
    QM_STORE.state.cart = [];
    QM_STORE.saveNow();
    QM_STORE.emit('cart');
    toast((e && e.message) || '购物车加载失败', 'error');
  }
  const items = QM_STORE.cart.list();
  const allChecked = items.length > 0 && items.every(i => i.checked);
  const selected = items.filter(i => i.checked);
  const total = selected.reduce((s, i) => s + QM_STORE.cart.subTotal(i), 0);
  cartTitle.value = `${items.length} 种商品`;
  const root = cartRootEl.value;
  if (!root) return;
  root.innerHTML = items.length ? `
        <div class="cart-body">
          <div class="cart-list">
            <div class="cart-head">
              <span class="cart-check ${allChecked ? 'checked' : ''}" data-action="cart-check-all">${allChecked ? '✓' : ''}</span>
              <span>商品信息</span><span>单价</span><span>数量</span><span>小计</span><span>操作</span>
            </div>
            <div class="cart-batch-bar">
              <span>已选 <b style="color:var(--accent)">${selected.length}</b> 种</span>
              <button class="btn btn-plain btn-sm" data-action="cart-batch-del" ${selected.length ? '' : 'disabled'}>批量删除</button>
            </div>
            ${groupByShop(items).map(g => `
              <div class="cart-shop-group">
                <div class="cart-shop-head">
                  <span>${esc(g.name)}</span>
                  <small>共 ${g.items.reduce((s, i) => s + i.qty, 0)} 件</small>
                </div>
                ${g.items.map(cartRow).join('')}
              </div>`).join('')}
          </div>
          <aside class="cart-summary">
            <h3>结算明细</h3>
            <div class="sum-row"><span>已选商品</span><span>${selected.length} 种</span></div>
            <div class="sum-row"><span>合计件数</span><span>${selected.reduce((s, i) => s + i.qty, 0)} 件</span></div>
            <div class="sum-row"><span>运费</span><span>${total >= 99 || total === 0 ? '包邮' : price(8)}</span></div>
            <div class="sum-total"><span>合计</span>${price(total)}</div>
            <button class="btn btn-primary btn-lg" id="checkoutBtn" ${selected.length ? '' : 'disabled'}>去结算（${selected.length}）</button>
            <button class="btn btn-plain" style="width:100%;margin-top:8px" data-action="cart-clear">清空购物车</button>
          </aside>
        </div>` : `<div class="cart-list"><div class="empty-state"><div class="empty-icon">🛒</div><h3>购物车还是空的</h3><p>快去挑选心仪的好物吧</p><a class="btn btn-primary" href="#/home">去逛逛</a></div></div>`;
  bindCheckout();
}

/* 换款式弹层：选择新规格组合（与详情页同一套规格渲染 / 价格口径），
   确认后调 PUT /cart/items/sku（后端事务：新款式合并数量并删除旧条目），成功后重拉列表 */
function openSkuPicker(key) {
  const item = QM_STORE.cart.list().find(i => i.key === key);
  if (!item || !item.product) return;
  const p = item.product;
  /* 已删除 / 已下架的商品不再支持换款式（入口已隐藏，此处双保险） */
  if (p.deleted === 1 || p.onSale === 0) return;
  const skus = Array.isArray(p.skus) ? p.skus : [];
  if (!skus.length) return;
  /* 从当前 sku 反解初始选中（' / ' 切分逐组匹配；匹配不到默认第 0 项） */
  const curParts = String(item.sku || '').split(' / ').map(s => s.trim());
  const pick = skus.map((g, gi) => {
    const want = curParts[gi];
    const vs = g.values || [];
    const idx = vs.findIndex(v => valOf(v) === want);
    return idx >= 0 ? idx : 0;
  });
  /* 弹层头部图：优先当前选中款式图（最近点击的规格组有图优先），无则回退商品主图 */
  let lastGroup = -1;
  let pickArt = pickArtOf(p, pick, lastGroup);

  const m = modal(`
    <div class="sku-picker">
      <div class="sku-picker-head">
        <span id="skuPickArt" class="ci-art" style="${artStyle(pickArt)}">${artHtml(pickArt)}</span>
        <div class="sku-picker-info">
          <h3 class="ellipsis-2">${esc(p.title)}</h3>
          <p class="sku-picker-price">款式价：¥<b id="skuPickPrice">${esc(moneyText(skuPriceOf(p, pick)))}</b></p>
        </div>
      </div>
      <div class="sku-picker-body">
        ${skus.map((g, gi) => `
          <div class="sku-group">
            <b>${esc(g.name || '规格')}：</b>
            ${(g.values || []).map((v, vi) => `
              <button class="sku-chip${vi === pick[gi] ? ' active' : ''}" data-group="${gi}" data-vi="${vi}">
                ${esc(valOf(v))}${valPrice(v) ? `<em class="sku-chip-price">¥${esc(moneyText(valPrice(v)))}</em>` : ''}
              </button>`).join('')}
          </div>`).join('')}
      </div>
      <div class="modal-actions">
        <button class="btn btn-plain" data-close>取消</button>
        <button class="btn btn-primary btn-lg" id="skuPickConfirm" style="flex:1">确认更换</button>
      </div>
    </div>`, { wide: true });

  m.root.querySelectorAll('[data-group]').forEach(btn => btn.onclick = () => {
    const gi = Number(btn.dataset.group);
    const vi = Number(btn.dataset.vi);
    pick[gi] = vi;
    lastGroup = gi;
    m.root.querySelectorAll(`[data-group="${gi}"]`).forEach(x => x.classList.toggle('active', Number(x.dataset.vi) === vi));
    m.root.querySelector('#skuPickPrice').textContent = moneyText(skuPriceOf(p, pick));
    /* 头部图随所选款式实时切换（与详情页主图同口径） */
    const artEl = m.root.querySelector('#skuPickArt');
    const art = pickArtOf(p, pick, lastGroup);
    artEl.style.cssText = artStyle(art);
    artEl.innerHTML = artHtml(art);
  });

  m.root.querySelector('#skuPickConfirm').onclick = async () => {
    const newSku = skuTextOf(skus, pick);
    if (newSku === String(item.sku || '')) { m.close(); toast('款式未变化'); return; }
    try {
      await QM_API.cart.updateSku(item.key, newSku, skuPriceOf(p, pick));
      m.close();
      await renderList();
      toast('已更换款式');
    } catch (e) { toast((e && e.message) || '修改款式失败，请稍后重试', 'error'); }
  };
}

/* 结算弹窗（地址 / 优惠券 / 支付方式选择，QM_UI.modal） */
async function openCheckout(items) {
  /* 地址一律以服务端为准（QM_API.addresses 是 strict 接口，不做本地回退）：
     拉取失败就把地址区置空并给出明确原因，绝不用浏览器缓存冒充后端地址 ——
     否则用户可能把订单发到一个后端并不存在的「假地址」上 */
  let addresses = [];
  let addrError = '';
  try {
    await QM_API.addresses.list();
    addresses = QM_STORE.addr.list();
  } catch (e) {
    addrError = (e && e.message) || '收货地址加载失败';
  }
  const coupons = QM_STORE.coupon.list().filter(c => c.status === 'unused');
  const goodsAmount = items.reduce((s, i) => s + QM_STORE.cart.subTotal(i), 0);
  const usableCoupons = coupons.filter(c => goodsAmount >= c.threshold);
  const freight = goodsAmount >= 99 ? 0 : 8;
  let chosenCoupon = null;
  let chosenAddr = addresses.find(a => a.isDefault) || addresses[0];
  let payMethod = '支付宝';

  const m = modal(`
      <div>
        <h3>确认订单</h3>
        <p class="modal-sub">共 ${items.reduce((s, i) => s + i.qty, 0)} 件商品</p>
        <div class="form-row">
          <label>收货地址 <a href="#/profile" style="float:right;color:var(--brand)" data-close>管理地址</a></label>
          <div id="addrList">
            ${addresses.map(a => `
              <div class="addr-option ${a === chosenAddr ? 'active' : ''}" data-action="pick-addr" data-id="${esc(a.id)}">
                <b>${esc(a.name)} ${esc(a.phone)}${a.isDefault ? ' <span class="pill pill-orange">默认</span>' : ''}${a.tag ? ` <span class="pill pill-gray">${esc(a.tag)}</span>` : ''}</b>
                <small>${esc(a.region)} ${esc(a.detail)}</small>
              </div>`).join('')}
            ${addresses.length ? '' : (addrError
              ? `<p class="hint" style="color:var(--accent-ink)">收货地址加载失败：${esc(addrError)}</p>`
              : '<p class="hint">暂无地址，请先到「个人中心 → 收货地址」添加</p>')}
          </div>
        </div>
        <div class="form-row">
          <label>优惠券（${usableCoupons.length} 张可用）</label>
          <select id="couponSel">
            <option value="">不使用优惠券</option>
            ${usableCoupons.map(c => `<option value="${esc(c.id)}">${esc(c.title)}（满 ${c.threshold} 减 ${c.amount}）</option>`).join('')}
          </select>
        </div>
        <div class="form-row">
          <label>支付方式</label>
          <div class="pay-methods">
            <div class="pay-method active" data-pay="支付宝">支付宝</div>
            <div class="pay-method" data-pay="微信支付">微信支付</div>
            <div class="pay-method" data-pay="银行卡">银行卡</div>
          </div>
        </div>
        <div class="form-row">
          <label>订单备注</label>
          <input id="orderRemark" placeholder="选填，给卖家留言（50 字内）" maxlength="50" />
        </div>
        <div style="border-top:1px dashed var(--border);padding-top:12px;margin-top:4px">
          <div class="checkout-item"><span>商品金额</span><span>${price(goodsAmount)}</span></div>
          <div class="checkout-item"><span>运费</span><span id="freightText">${freight ? price(freight) : '包邮'}</span></div>
          <div class="checkout-item"><span>优惠券</span><span id="couponText">- ¥0.00</span></div>
          <div class="checkout-item" style="font-size:15px"><b>应付总额</b><b id="totalText" style="color:var(--accent)">${price(goodsAmount + freight)}</b></div>
        </div>
        <div class="modal-actions">
          <button class="btn btn-plain" data-close>再想想</button>
          <button class="btn btn-primary btn-lg" id="submitOrder" style="flex:1">提交订单并支付</button>
        </div>
      </div>`, { wide: true });

  const updateTotal = () => {
    const coupon = coupons.find(c => c.id === chosenCoupon);
    const total = goodsAmount + freight - (coupon ? coupon.amount : 0);
    m.root.querySelector('#couponText').textContent = coupon ? `- ¥${coupon.amount}.00` : '- ¥0.00';
    m.root.querySelector('#totalText').innerHTML = price(total);
  };
  const refreshAddr = () => {
    m.root.querySelectorAll('[data-action="pick-addr"]').forEach(el => el.classList.toggle('active', el.dataset.id === chosenAddr.id));
  };
  m.root.querySelectorAll('[data-action="pick-addr"]').forEach(el => el.onclick = () => {
    chosenAddr = addresses.find(a => a.id === el.dataset.id);
    refreshAddr();
  });
  m.root.querySelector('#couponSel').onchange = e => { chosenCoupon = e.target.value || null; updateTotal(); };
  m.root.querySelectorAll('[data-pay]').forEach(el => el.onclick = () => {
    m.root.querySelectorAll('[data-pay]').forEach(x => x.classList.toggle('active', x === el));
    payMethod = el.dataset.pay;
  });
  m.root.querySelector('#submitOrder').onclick = async () => {
    if (!chosenAddr) return toast('请先选择收货地址', 'error');
    const coupon = coupons.find(c => c.id === chosenCoupon) || null;
    const payload = {
      items: items.map(i => ({ productId: i.productId, sku: i.sku, qty: i.qty, price: QM_STORE.cart.unitPrice(i), title: i.product.title, img: i.img || null })),
      address: { name: chosenAddr.name, phone: chosenAddr.phone, region: chosenAddr.region, detail: chosenAddr.detail },
      coupon, payMethod, remark: m.root.querySelector('#orderRemark').value.trim()
    };
    try {
      const order = await QM_API.orders.create(payload);
      /* 下单成功后：服务端删除已购条目 → 支付（演示自动支付）→ 跳转订单页 */
      await QM_API.cart.remove(items.map(i => i.key));
      await QM_API.orders.pay(order.id);
      m.close();
      toast('下单成功！演示订单已自动支付', 'success');
      router.push('/orders');   // 原 QM_ROUTER.go('/orders')
    } catch (e) { toast(e.message, 'error'); }
  };
}

/* #cartRoot 点击事件委托（原版 mount 里 view.querySelector('#cartRoot').onclick 同款） */
async function onCartRootClick(e) {
  const t = e.target.closest('[data-action]');
  if (!t) return;
  const action = t.dataset.action;
  if (action === 'cart-check') {
    const items = QM_STORE.cart.list();
    const key = t.dataset.key;
    const item = items.find(i => i.key === key);
    if (item) { QM_STORE.cart.toggle([key], !item.checked); renderList(); }
  } else if (action === 'cart-check-all') {
    const items = QM_STORE.cart.list();
    const allChecked = items.every(i => i.checked);
    QM_STORE.cart.toggleAll(!allChecked); renderList();
  } else if (action === 'cart-del') {
    if (await confirmDialog('删除商品', '确定将该商品移出购物车吗？', '删除', true)) {
      try {
        await QM_API.cart.remove([t.dataset.key]);
        renderList();
        toast('已删除');
      } catch (e) { toast((e && e.message) || '删除失败，请稍后重试', 'error'); }
    }
  } else if (action === 'cart-batch-del') {
    const selected = QM_STORE.cart.list().filter(i => i.checked);
    if (!selected.length) return;
    if (await confirmDialog('批量删除', `确定将选中的 ${selected.length} 种商品移出购物车吗？`, '删除', true)) {
      try {
        await QM_API.cart.remove(selected.map(i => i.key));
        renderList();
        toast(`已删除 ${selected.length} 种商品`);
      } catch (e) { toast((e && e.message) || '批量删除失败，请稍后重试', 'error'); }
    }
  } else if (action === 'cart-clear') {
    if (await confirmDialog('清空购物车', '确定清空购物车中的所有商品吗？', '清空', true)) {
      try {
        await QM_API.cart.clear();
        renderList();
        toast('购物车已清空');
      } catch (e) { toast((e && e.message) || '清空失败，请稍后重试', 'error'); }
    }
  } else if (action === 'cart-sku') {
    openSkuPicker(t.dataset.key);
  } else if (action === 'cart-qty') {
    const item = QM_STORE.cart.list().find(i => i.key === t.dataset.key);
    if (item) {
      /* 数量边界钳制（1-999）：到边界后步进不再向后端发非法值（原实现会发 0 / 1000 被后端拒绝） */
      const next = item.qty + Number(t.dataset.dir);
      if (next < 1 || next > 999) return;
      try {
        await QM_API.cart.update(t.dataset.key, next);
        renderList();
      } catch (e) { toast((e && e.message) || '修改数量失败，请稍后重试', 'error'); }
    }
  } else if (action === 'cart-qty-input') {
    const input = t;
    input.onchange = async () => {
      /* 输入非法（非数字 / 0 / 超上限）时钳制到 1-999，与步进按钮同一口径 */
      const v = Math.max(1, Math.min(999, parseInt(input.value, 10) || 1));
      try {
        await QM_API.cart.update(input.dataset.key, v);
        renderList();
      } catch (e) { toast((e && e.message) || '修改数量失败，请稍后重试', 'error'); }
    };
  }
}

onMounted(async () => {
  await renderList();
  cartRootEl.value.addEventListener('click', onCartRootClick);

  /* 「立即购买」跳转而来 → 自动打开结算。
     优先按详情页记录的 productId/sku 精确匹配（原实现取 selected.slice(-1)，
     当该商品早已在购物车里时不改变位置，会误结算成购物车中的最后一条旧商品） */
  let buyNow = null;
  try { buyNow = sessionStorage.getItem('qm_v2_buynow'); } catch (e) { buyNow = null; }
  if (buyNow) {
    try { sessionStorage.removeItem('qm_v2_buynow'); } catch (e) { /* 忽略 */ }
    const selected = QM_STORE.cart.selected();
    let target = null;
    if (buyNow.startsWith('{')) {
      try {
        const want = JSON.parse(buyNow);
        target = QM_STORE.cart.list().find(i => i.productId === want.productId && (!want.sku || i.sku === want.sku))
          || QM_STORE.cart.list().find(i => i.productId === want.productId);
      } catch (e) { target = null; }
    }
    if (!target) target = selected[selected.length - 1] || null; // 兼容旧的 '1' 标记
    if (target) setTimeout(() => openCheckout([target]), 200);
  }
});

/* 原版 cart.js 未订阅任何 QM_STORE 事件（每次变更都在本页内发生并手动 renderList），
   因此这里只需解绑页面自身的 #cartRoot 委托监听即可 */
onBeforeUnmount(() => {
  if (cartRootEl.value) cartRootEl.value.removeEventListener('click', onCartRootClick);
});
</script>

<template>
  <div>
    <div class="page-head">
      <div><div class="crumb">首页 / 购物车</div><h1>购物车 <small id="cartTitle">{{ cartTitle }}</small></h1></div>
      <a class="btn btn-plain" href="#/home">继续购物 ›</a>
    </div>
    <div id="cartRoot" ref="cartRootEl"></div>
  </div>
</template>
