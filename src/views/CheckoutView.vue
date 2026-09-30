<script setup>
/* =========================================================
   玉子市场 · views/CheckoutView.vue —— 确认订单（独立页面 /checkout）
   替代原购物车页内的「结算弹窗」，并且只负责「确认」这一件事：
   · 结算信息（地址 / 支付方式 / 备注）在这里填；提交订单成功后跳到
     收银台（/pay，见 PayView.vue）完成付款；
   · 优惠券整单统一一张：达到整单商品金额门槛即可用，抵扣不超整单商品金额
     （后端按店铺拆单后按各店商品金额占比分摊，各子订单抵扣之和等于券额）；
   · 提交成功即把本次结算的商品移出购物车（后端在下单事务里删除），订单进入
     「待付款」—— 未付款也能稍后回收银台继续支付，与真实下单一致。
   ========================================================= */
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import QM_API from '../core/api.js';
import QM_STORE from '../core/store.js';
import QM_UI from '../core/ui.js';
import openAddressModal from '../core/addressModal.js';

const { price, artStyle, artHtml, toast } = QM_UI;
const router = useRouter();

const PAY_METHODS = ['支付宝', '微信支付', '银行卡'];

/* ---------- 状态 ---------- */
const loaded = ref(false);
const settleItems = ref([]);      // 本次结算的商品条目（购物车勾选 或 立即购买目标）
const groups = ref([]);           // 按店铺分组：{key,name,shopId,items,amount,freight,qty}
const addresses = ref([]);
const addrError = ref('');
const couponError = ref('');     // 优惠券列表加载失败原因（券由平台在数据库配置）
const chosenAddr = ref(null);
const payMethod = ref('支付宝');
const remark = ref('');
const submitting = ref(false);

/* ---------- 工具 ---------- */
const fmt = n => (Number.isInteger(Number(n)) ? String(Number(n)) : Number(n).toFixed(2));
const itemArt = it => (it.img && { img: it.img }) || (it.product && it.product.art) || null;

/* 按店铺分组（保持首次出现顺序；无店铺信息归入「其他店铺」） */
function groupByShop(items) {
  const list = [];
  const idx = new Map();
  items.forEach(it => {
    const shop = (it.product && it.product.shop) || {};
    const name = shop.name || '其他店铺';
    const key = String(shop.id || name);
    if (!idx.has(key)) { idx.set(key, list.length); list.push({ key, name, shopId: shop.id || '', items: [] }); }
    list[idx.get(key)].items.push(it);
  });
  return list;
}

/* 每店金额 / 运费口径必须与后端拆单一致：单店满 50 包邮，否则运费 5 元 */
function buildGroups(items) {
  return groupByShop(items).map(g => {
    const amount = g.items.reduce((s, i) => s + QM_STORE.cart.subTotal(i), 0);
    return Object.assign(g, {
      amount,
      freight: amount >= 50 ? 0 : 5,
      qty: g.items.reduce((s, i) => s + i.qty, 0)
    });
  });
}

/* ---------- 优惠券（整单统一一张） ---------- */
const unusedCoupons = computed(() => QM_STORE.coupon.list().filter(c => c.status === 'unused'));
/* 当前选中的券 id（整单只能用一张） */
const chosenCouponId = ref('');
const chosenCoupon = computed(() => unusedCoupons.value.find(c => c.id === chosenCouponId.value) || null);

/* ---------- 汇总 ---------- */
const totalGoods = computed(() => groups.value.reduce((s, g) => s + g.amount, 0));
const totalFreight = computed(() => groups.value.reduce((s, g) => s + g.freight, 0));
/* 可选券：整单商品金额达到券门槛、且未过期才可用 */
const usableCoupons = computed(() => unusedCoupons.value.filter(
  c => totalGoods.value >= Number(c.threshold || 0) && !QM_STORE.coupon.isExpired(c)
));
const totalDiscount = computed(() => {
  const c = chosenCoupon.value;
  return c ? Math.min(Number(c.amount) || 0, totalGoods.value) : 0;
});
const totalPay = computed(() => totalGoods.value + totalFreight.value - totalDiscount.value);

/* ---------- 地址 ---------- */
async function loadAddresses() {
  try {
    await QM_API.addresses.list();
    addresses.value = QM_STORE.addr.list();
    addrError.value = '';
  } catch (e) {
    addrError.value = (e && e.message) || '收货地址加载失败';
  }
  if (!addresses.value.some(a => chosenAddr.value && a.id === chosenAddr.value.id)) {
    chosenAddr.value = addresses.value.find(a => a.isDefault) || addresses.value[0] || null;
  }
}

/* 添加 / 管理地址：与个人中心共用同一弹窗，保存后重新拉取 */
function openAddrModal() {
  openAddressModal({ onSaved: loadAddresses });
}

/* ---------- 初始化 ---------- */
async function init() {
  /* 购物车一律以服务端为准：失败如实报错并显示空态 */
  try {
    await QM_API.cart.list();
  } catch (e) {
    QM_STORE.state.cart = [];
    QM_STORE.saveNow();
    QM_STORE.emit('cart');
    toast((e && e.message) || '购物车加载失败', 'error');
  }
  const items = QM_STORE.cart.list();

  /* 本次结算范围：「立即购买」按详情页记录的目标精确结算；否则取购物车勾选条目 */
  let target = null;
  try {
    const raw = sessionStorage.getItem('qm_v2_buynow');
    if (raw) {
      sessionStorage.removeItem('qm_v2_buynow');
      if (raw.startsWith('{')) {
        const want = JSON.parse(raw);
        target = items.find(i => i.productId === want.productId && (!want.sku || i.sku === want.sku))
              || items.find(i => i.productId === want.productId);
      }
    }
  } catch (e) { target = null; }
  settleItems.value = target ? [target] : QM_STORE.cart.selected();

  if (!settleItems.value.length) { loaded.value = true; return; }
  groups.value = buildGroups(settleItems.value);
  /* 优惠券：平台在数据库配置、前端只读；strict —— 接口失败不展示旧缓存，如实提示 */
  try {
    await QM_API.coupons.list();
    couponError.value = '';
  } catch (e) {
    QM_STORE.state.coupons = [];
    QM_STORE.emit('coupons');
    couponError.value = (e && e.message) || '优惠券加载失败';
  }
  await loadAddresses();
  loaded.value = true;
}

/* ---------- 提交订单（付款在独立的收银台页 /pay 完成） ----------
   一次下单：全部条目 + 整单选中的那张优惠券一起提交，后端按店铺拆单后按各店商品金额
   占比分摊券额，同批子订单共享一个 payNo，到收银台一次付清。与真实下单一致：
   提交成功即把本次结算的商品移出购物车，订单进入「待付款」，没付款也可以稍后继续支付。
   · 建单失败：后端事务回滚，订单不成立、购物车不动，用户留在本页修改后重试；
   · 建单成功未付款：订单保留为 pending，可在收银台或订单列表继续支付或取消。 */
async function submit() {
  if (!chosenAddr.value) return toast('请先选择收货地址', 'error');
  if (submitting.value) return;
  submitting.value = true;
  const addr = chosenAddr.value;

  try {
    const res = await QM_API.orders.create({
      items: settleItems.value.map(i => ({
        productId: i.productId, sku: i.sku, qty: i.qty,
        price: QM_STORE.cart.unitPrice(i), title: i.product.title, img: i.img || null
      })),
      coupon: chosenCoupon.value,
      address: { name: addr.name, phone: addr.phone, region: addr.region, detail: addr.detail },
      payMethod: payMethod.value,
      remark: remark.value.trim()
    });

    /* 提交成功 → 本次结算的商品移出购物车：后端在下单事务里已经删除，
       这里再同步一次前端缓存（后端尚未更新时也保证购物车不再显示这些商品）；
       删除请求失败就直接清本地缓存，购物车列表与角标立即刷新 */
    const cartKeys = settleItems.value.map(i => i.key);
    try {
      await QM_API.cart.remove(cartKeys);
    } catch (e) {
      QM_STORE.cart.remove(cartKeys);
    }

    /* 待支付上下文：本次下单的订单 id，收银台据此定位这一批待付款订单
       （存储不可用时收银台会列出账号下全部待付款订单） */
    try {
      sessionStorage.setItem('qm_v2_pay', JSON.stringify({
        orderIds: (res.orders || []).map(o => o.id).filter(Boolean),
        createdAt: Date.now()
      }));
    } catch (e) { /* 忽略：不影响下单与支付 */ }

    const count = res.orderCount || (res.orders || []).length;
    submitting.value = false;
    toast(count > 1
      ? `订单已提交：已按 ${count} 家店铺拆成 ${count} 笔，请在收银台完成付款`
      : '订单已提交，请在收银台完成付款', 'success');
    router.push('/pay');
  } catch (e) {
    submitting.value = false;
    toast(e.message, 'error');
  }
}

onMounted(init);
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 购物车 / 确认订单</div>
        <h1>确认订单</h1>
      </div>
      <a class="btn btn-plain" href="#/cart">返回购物车 ›</a>
    </div>

    <p v-if="!loaded" class="hint" style="padding:24px 0">正在加载结算信息…</p>

    <div v-else-if="!settleItems.length" class="empty-state" style="padding:60px 0">
      <div class="empty-icon">📋</div>
      <h3>没有需要结算的商品</h3>
      <p>请先在购物车勾选要购买的商品</p>
      <a class="btn btn-primary" href="#/cart">去购物车</a>
    </div>

    <template v-else>
      <!-- 收货地址 -->
      <section class="ck-card">
        <div class="ck-card-title">收货地址
          <button type="button" class="link-btn" style="float:right" @click="openAddrModal">添加地址</button>
        </div>
        <p v-if="addrError" class="hint" style="color:var(--accent-ink)">收货地址加载失败：{{ addrError }}</p>
        <template v-else-if="addresses.length">
          <div v-for="a in addresses" :key="a.id" class="addr-option"
               :class="{ active: chosenAddr && chosenAddr.id === a.id }" @click="chosenAddr = a">
            <b>{{ a.name }} {{ a.phone }}
              <span v-if="a.isDefault" class="pill pill-orange">默认</span>
              <span v-if="a.tag" class="pill pill-gray">{{ a.tag }}</span>
            </b>
            <small>{{ a.region }} {{ a.detail }}</small>
          </div>
        </template>
        <p v-else class="hint">还没有收货地址，点右上「添加地址」</p>
      </section>

      <!-- 店铺分组：按店铺展示本次结算的商品与金额（发货 / 运费按店独立计算） -->
      <section v-for="g in groups" :key="g.key" class="ck-card">
        <div class="ck-card-title">
          <span class="ck-shop-name">{{ g.name }}</span>
          <small>共 {{ g.qty }} 件</small>
        </div>
        <div class="ck-items">
          <div v-for="it in g.items" :key="it.key" class="ck-item">
            <span class="ck-art" :style="artStyle(itemArt(it))" v-html="artHtml(itemArt(it))"></span>
            <div class="ck-item-info">
              <h4>{{ it.product.title }}</h4>
              <small>{{ it.sku }} · ×{{ it.qty }}</small>
            </div>
            <b v-html="price(QM_STORE.cart.subTotal(it))"></b>
          </div>
        </div>
        <div class="ck-g-sum">
          <div class="checkout-item"><span>商品金额</span><span v-html="price(g.amount)"></span></div>
          <div class="checkout-item"><span>运费</span><span v-html="g.freight ? price(g.freight) : '包邮'"></span></div>
          <div class="checkout-item" style="font-size:14px">
            <span><b>店铺小计</b></span>
            <b v-html="price(g.amount + g.freight)" style="color:var(--accent)"></b>
          </div>
        </div>
      </section>

      <!-- 优惠券：整单统一一张（券由平台在数据库配置，前端只读、无增删入口） -->
      <section class="ck-card">
        <div class="ck-card-title">优惠券<small>{{ usableCoupons.length }} 张可用</small></div>
        <div class="form-row">
          <select v-model="chosenCouponId" :disabled="!usableCoupons.length">
            <option value="">不使用优惠券</option>
            <option v-for="c in usableCoupons" :key="c.id" :value="c.id">
              {{ c.title }}（满 {{ c.threshold }} 减 {{ c.amount }}）
            </option>
          </select>
          <p v-if="couponError" class="hint" style="color:var(--accent-ink)">优惠券加载失败：{{ couponError }}</p>
          <p v-else-if="!usableCoupons.length" class="hint">暂无可用优惠券</p>
        </div>
      </section>

      <!-- 支付方式 / 备注：支付方式随订单一起提交，真正的付款在收银台（/pay）完成 -->
      <section class="ck-card">
        <div class="ck-card-title">支付方式</div>
        <div class="pay-methods">
          <div v-for="p in PAY_METHODS" :key="p" class="pay-method" :class="{ active: payMethod === p }" @click="payMethod = p">{{ p }}</div>
        </div>
        <div class="form-row" style="margin-top:14px">
          <label>订单备注</label>
          <input v-model="remark" placeholder="选填，给卖家留言（50 字内）" maxlength="50" />
        </div>
      </section>

      <!-- 汇总 / 提交订单 -->
      <section class="ck-card">
        <div v-if="groups.length > 1" class="hint" style="margin:0 0 8px">
          🧾 共 {{ groups.length }} 家店铺，将拆成 <b>{{ groups.length }}</b> 笔订单
        </div>
        <div class="checkout-item"><span>商品金额</span><span v-html="price(totalGoods)"></span></div>
        <div class="checkout-item"><span>运费</span><span v-html="totalFreight ? price(totalFreight) : '包邮'"></span></div>
        <div class="checkout-item"><span>优惠券</span><span v-html="totalDiscount ? '− ¥' + fmt(totalDiscount) : '− ¥0.00'"></span></div>
        <div class="checkout-item" style="font-size:15px"><span><b>应付总额</b></span><b v-html="price(totalPay)" style="color:var(--accent)"></b></div>
        <button class="btn btn-primary btn-lg" style="width:100%;margin-top:14px" :disabled="submitting" @click="submit">
          {{ submitting ? '提交中…' : '提交订单，去支付' }}
        </button>
      </section>
    </template>
  </div>
</template>

