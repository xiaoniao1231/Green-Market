<script setup>
/* =========================================================
   青集市 · views/SellerProductsView.vue —— 我的店铺 · 商品管理
   2026-09 改版：视觉与买家端「店铺主页商品展示」保持一致（卡片网格），
   卡片上直接改价 / 上下架，支持编辑完整商品信息与新增商品。
   · 数据走 QM_API.seller.*（strict 严格直连后端，契约见 docs/店家中心商品管理接口文档.md）：
     后端未实现（404 / 网络不可达）时提示错误 + 空态，不回退本地演示数据；
   · 分类 / 子类下拉取 mock.js 的 categories（与后端 category / sub 同一套取值）；
   · 展示图自动取第一个带图的 SKU 值，不再单独上传展示图；无图时用「表情 + 渐变」占位外观；
   · 规格 SKU 每个款式值可配一张图，详情页点击款式展示图跟随切换；
   · 图文详情（多段落文字 + 图片）独立弹窗编辑，不占用编辑表单；
   · 编辑 / 图文详情先拉 GET /seller/products/{id} 取全量字段（列表接口不含 detail，
     直接用列表数据保存会把原有图文详情清空）。
   ========================================================= */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import QM_UI from '../core/ui.js';
import QM_API from '../core/api.js';
import { CATEGORIES, bySub } from '../core/catalog.js';
import QM_STORE from '../core/store.js';

const { esc, artStyle, artHtml, sales, toast, modal, confirmDialog, openShopCreate } = QM_UI;
const router = useRouter();

/* store 不是响应式对象：用 shopTick 版本号驱动「当前店铺」重算（开店 / 档案更新后自增） */
const shopTick = ref(0);
const svc = computed(() => { shopTick.value; return QM_STORE.seller.current(); });
const shopId = computed(() => (svc.value ? svc.value.id : ''));

let offShopProfile = null;

/* 未开店账号可直接在本页开店 */
function openShop() {
  if (!QM_STORE.state.user) { router.push({ path: '/login', query: { redirect: '/seller/products' } }); return; }
  openShopCreate({ onDone: () => { shopTick.value++; refresh(); } });
}

/* ---------- 列表与筛选排序（store 非响应式，页面自行拉取） ---------- */
const listData = ref([]);       // 店铺全部商品
const loading = ref(false);
const filter = ref('all');      // all 全部 / on 在售 / off 已下架
const sort = ref('default');    // default 综合 / sales 销量 / priceAsc 价格↑ / priceDesc 价格↓
const keyword = ref('');

const onSaleCount = computed(() => listData.value.filter(p => p.onSale !== false).length);
const offSaleCount = computed(() => listData.value.filter(p => p.onSale === false).length);

const filtered = computed(() => {
  let list = listData.value.slice();
  if (filter.value === 'on') list = list.filter(p => p.onSale !== false);
  if (filter.value === 'off') list = list.filter(p => p.onSale === false);
  const kw = keyword.value.trim().toLowerCase();
  if (kw) list = list.filter(p => String(p.title || '').toLowerCase().includes(kw));
  if (sort.value === 'sales') list.sort((a, b) => b.sales - a.sales);
  else if (sort.value === 'priceAsc') list.sort((a, b) => a.price - b.price);
  else if (sort.value === 'priceDesc') list.sort((a, b) => b.price - a.price);
  return list;
});

async function refresh() {
  if (!shopId.value) return;
  loading.value = true;
  try {
    const data = await QM_API.seller.products({ page: 1, size: 100 });
    listData.value = (data && data.list) || [];
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    loading.value = false;
  }
}

/* ---------- 卡片操作：上下架 / 直接改价 / 编辑 / 图文详情 ---------- */
async function toggle(p) {
  try {
    const res = await QM_API.seller.setStatus(p.id, p.onSale === false);
    toast(res && res.onSale ? '已上架' : '已下架', 'success');
    refresh();
  } catch (e) { toast(e.message, 'error'); }
}

async function onPrice(p, e) {
  const raw = Number(e.target.value);
  if (!isFinite(raw) || raw < 1 || raw > 99999) { e.target.value = p.price; return toast('请输入有效价格（1-99999）', 'error'); }
  const price = Math.round(raw * 100) / 100;   // 对齐后端 DECIMAL(10,2)：最多两位小数
  try {
    await QM_API.seller.update(p.id, { price });
    e.target.value = price;
    toast('价格已更新', 'success');
    refresh();
  } catch (err) { toast(err.message, 'error'); }
}

/* ---------- 价格两段式展示（复刻详情页 price() 结构） ---------- */
function priceParts(n) {
  n = Number(n || 0);
  const text = n % 1 === 0 ? String(n) : n.toFixed(2);
  const [int, dec] = text.split('.');
  return { int, dec: dec !== undefined ? '.' + dec : '' };
}
const pPrice = p => priceParts(p.price);
const pOrig = p => priceParts(p.original);

/* ---------- 款式价区间 ----------
   后端列表/详情会返回 priceMin / priceMax（按「商品默认价 + 全部款式价」计算），
   老数据或本地计算缺失时，这里从 skus 兜底算一遍。 */
function moneyText(n) {
  const v = Number(n);
  return Number.isInteger(v) ? String(v) : v.toFixed(2);
}
function skuRange(p) {
  const hasServerRange = p.priceMin !== undefined && p.priceMin !== null
    && p.priceMax !== undefined && p.priceMax !== null;
  let min = hasServerRange ? Number(p.priceMin) : Number(p.price);
  let max = hasServerRange ? Number(p.priceMax) : Number(p.price);
  if (!hasServerRange) {
    (p.skus || []).forEach(g => (g.values || []).forEach(v => {
      const sp = Number(v && v.price);
      if (!Number.isFinite(sp) || sp <= 0) return;
      if (!Number.isFinite(min) || sp < min) min = sp;
      if (!Number.isFinite(max) || sp > max) max = sp;
    }));
  }
  if (!Number.isFinite(min) || !Number.isFinite(max)) return null;
  return { min, max };
}
const pHasRange = p => { const r = skuRange(p); return !!r && r.max > r.min; };
const pRangeText = p => { const r = skuRange(p); return r ? moneyText(r.min) + ' - ' + moneyText(r.max) : ''; };

/* ---------- 图片上传（POST /products/image → OSS 地址；strict：失败只提示，图片不落本地） ---------- */
function pickImage(fileInput, done) {
  const file = fileInput && fileInput.files && fileInput.files[0];
  if (!file) return;
  if (!/^image\//.test(file.type)) return toast('请选择图片文件（jpg / png / webp）', 'error');
  if (file.size > 10 * 1024 * 1024) return toast('图片不能超过 10MB', 'error');
  QM_API.products.uploadImage(file).then(res => {
    const url = res && res.url;
    if (!url) return toast('上传未返回图片地址', 'error');
    done(url);
  }).catch(e => toast('图片上传失败：' + e.message, 'error'));
}

/* ---------- 规格值行 / 规格组行 的 HTML ---------- */
function skuValueRowHtml(v) {
  const text = typeof v === 'string' ? v : ((v && v.v) || '');
  const img = (v && v.img) || '';
  /* 款式价：选填；留空 = 沿用商品售价（后端同样接受空值，不做强制） */
  const price = (v && typeof v === 'object' && v.price !== undefined && v.price !== null) ? v.price : '';
  return `
  <div class="sku-value-row">
    <input class="sku-val" placeholder="款式名，如 曜石黑" value="${esc(text)}" />
    <span class="sku-price-box" title="选填：该款式单独定价，留空沿用商品售价"><i>¥</i><input class="sku-price" type="number" min="1" max="99999" step="0.01" placeholder="款式价" value="${esc(price)}" /></span>
    <span class="sku-img-box">${img ? `<img class="sku-img" src="${esc(img)}" alt="" />` : '<span class="sku-img-placeholder">图</span>'}</span>
    <button type="button" class="btn btn-plain btn-sm" data-pick-sku-img>${img ? '换图' : '传图'}</button>
    <input type="file" class="hidden" accept="image/*" />
    <button type="button" class="btn btn-plain btn-sm" data-rm-sku-val>删除</button>
  </div>`;
}
function skuGroupRowHtml(g) {
  const name = (g && g.name) || '';
  const values = (g && g.values) || [];
  return `
  <div class="sku-group-row">
    <div class="sku-group-head">
      <input class="sku-name" placeholder="规格名，如 颜色" value="${esc(name)}" />
      <button type="button" class="btn btn-plain btn-sm" data-add-sku-val>＋ 添加款式</button>
      <button type="button" class="btn btn-plain btn-sm" data-rm-sku>删除组</button>
    </div>
    <div class="sku-value-rows">${values.map(skuValueRowHtml).join('')}</div>
  </div>`;
}

/* ---------- 取商品全量字段（编辑回显）：GET /seller/products/{id} ----------
   列表接口（文档 2.1）不含 detail 等字段，直接拿列表数据编辑再保存会把图文详情清空；
   fallback=true（编辑）时接口未实现则回退列表数据，fallback=false（图文详情）则不打开。 */
async function fetchFull(p, fallback) {
  try {
    const full = await QM_API.seller.get(p.id);
    return Object.assign({}, p, full || {});
  } catch (e) {
    toast('商品详情加载失败：' + e.message, 'error');
    return fallback ? p : null;
  }
}

/* ---------- 编辑 / 新增商品弹窗（基础信息 + 规格SKU含图 + 规格参数） ---------- */
function addNew() { openEditor(null); }
async function edit(p) {
  const full = await fetchFull(p, true);
  if (!full) return;
  openEditor(full);
}

function openEditor(p) {
  const isNew = !p;
  const base = p || {};
  /* 分类 / 子类下拉：编辑取商品自身分类，新增取第一个分类
     —— 否则新增时子类下拉是空的（只能手动切一次分类才会填充） */
  const curCat = base.category || (CATEGORIES[0] || {}).id || '';
  const cats = CATEGORIES.map(c =>
    `<option value="${esc(c.id)}" ${curCat === c.id ? 'selected' : ''}>${esc(c.id)}</option>`).join('');
  const subs = (bySub(curCat) || []).map(s =>
    `<option value="${esc(s)}" ${base.sub === s ? 'selected' : ''}>${esc(s)}</option>`).join('');
  const skuGroups = (base.skus || []).map(skuGroupRowHtml).join('');
  const paramRows = (base.params || []).map(pr =>
    `<div class="param-row input-flex">
       <input class="param-name" placeholder="参数名，如 品牌" value="${esc(pr[0] || '')}" />
       <input class="param-value" placeholder="参数值，如 青禾" value="${esc(pr[1] || '')}" />
       <button type="button" class="btn btn-plain btn-sm" data-rm-param>删除</button>
     </div>`).join('');
  const artImg = (base.art && base.art.img) || '';

  const m = modal(`
    <h3>${isNew ? '新增商品' : '编辑商品'}</h3>
    <p class="modal-sub">${isNew ? '填写商品信息后上架，买家即可在店铺主页看到' : '修改任意字段后保存，买家端立即生效'}</p>

    <h4 class="editor-sec">基础信息</h4>
    <div class="form-row"><label>商品标题 *</label><input id="f_title" maxlength="80" placeholder="简洁描述商品卖点，建议 10-40 字" value="${esc(base.title || '')}" /></div>
    <div class="editor-grid">
      <div class="form-row"><label>售价（元）*</label><input id="f_price" type="number" min="1" max="99999" placeholder="现价" value="${base.price || ''}" /></div>
      <div class="form-row"><label>划线价（元）</label><input id="f_original" type="number" min="0" max="99999" placeholder="原价（可留空）" value="${base.original || ''}" /></div>
      <div class="form-row"><label>库存（件）</label><input id="f_stock" type="number" min="0" max="999999" placeholder="库存" value="${base.stock || 0}" /></div>
      <div class="form-row"><label>商品标签</label><input id="f_tag" maxlength="10" placeholder="如 包邮 / 次日达" value="${esc(base.tag || '')}" /></div>
    </div>
    <div class="editor-grid">
      <div class="form-row"><label>分类</label><select id="f_category">${cats}</select></div>
      <div class="form-row"><label>子类</label><select id="f_sub">${subs}</select></div>
    </div>

    <h4 class="editor-sec">规格 SKU <small>每个款式可单独配图与定价；款式价留空 = 沿用商品售价</small></h4>
    <div id="skuRows">${skuGroups}</div>
    <button type="button" class="btn btn-plain btn-sm" data-add-sku>＋ 添加规格组</button>

    <h4 class="editor-sec">规格参数 <small>如 品牌 / 材质 / 尺寸</small></h4>
    <div id="paramRows">${paramRows}</div>
    <button type="button" class="btn btn-plain btn-sm" data-add-param>＋ 添加参数</button>

    <div class="modal-actions">
      ${isNew ? '' : '<button type="button" class="btn btn-danger" id="removeProduct">删除商品</button>'}
      <button type="button" class="btn btn-plain" data-close>取消</button>
      <button type="button" class="btn btn-primary" id="saveProduct">保 存</button>
    </div>`, { wide: true });

  const root = m.root;

  /* 分类联动：切换大类时重建子类下拉（保留仍在列表中的子类） */
  root.querySelector('#f_category').addEventListener('change', () => {
    const cat = root.querySelector('#f_category').value;
    const cur = root.querySelector('#f_sub').value;
    root.querySelector('#f_sub').innerHTML = (bySub(cat) || [])
      .map(s => `<option value="${esc(s)}" ${s === cur ? 'selected' : ''}>${esc(s)}</option>`).join('');
  });

  /* 根节点事件委托：动态行增删 / 图片上传 / 保存 / 删除 */
  root.addEventListener('click', e => {
    const t = e.target;
    if (!t || !t.closest) return;
    if (t.closest('[data-add-sku]')) {
      root.querySelector('#skuRows').insertAdjacentHTML('beforeend', skuGroupRowHtml({ name: '', values: [''] }));
      return;
    }
    if (t.closest('[data-add-sku-val]')) {
      const g = t.closest('.sku-group-row');
      if (g) g.querySelector('.sku-value-rows').insertAdjacentHTML('beforeend', skuValueRowHtml(''));
      return;
    }
    if (t.closest('[data-rm-sku]')) { const g = t.closest('.sku-group-row'); if (g) g.remove(); return; }
    if (t.closest('[data-rm-sku-val]')) { const r = t.closest('.sku-value-row'); if (r) r.remove(); return; }
    if (t.closest('[data-add-param]')) {
      root.querySelector('#paramRows').insertAdjacentHTML('beforeend',
        `<div class="param-row input-flex">
           <input class="param-name" placeholder="参数名，如 品牌" />
           <input class="param-value" placeholder="参数值，如 青禾" />
           <button type="button" class="btn btn-plain btn-sm" data-rm-param>删除</button>
         </div>`);
      return;
    }
    if (t.closest('[data-rm-param]')) { const r = t.closest('.param-row'); if (r) r.remove(); return; }
    /* 规格值图上传 */
    if (t.closest('[data-pick-sku-img]')) {
      const row = t.closest('.sku-value-row');
      const input = row && row.querySelector('input[type=file]');
      if (input) input.click();
      return;
    }
    if (t.closest('#saveProduct')) return save();
    if (t.closest('#removeProduct')) return removeItem();
  });

  /* 文件选择 → 上传 → 回填预览 */
  root.addEventListener('change', e => {
    const input = e.target;
    if (!input || input.type !== 'file') return;
    const row = input.closest('.sku-value-row');
    pickImage(input, url => {
      if (row) {
        row.querySelector('.sku-img-box').innerHTML = `<img class="sku-img" src="${esc(url)}" alt="" />`;
        const btn = row.querySelector('[data-pick-sku-img]');
        if (btn) btn.textContent = '换图';
      }
    });
    input.value = '';
  });

  /* ---------- 收集表单 → payload ---------- */
  function collect() {
    const $v = sel => { const el = root.querySelector(sel); return el ? el.value.trim() : ''; };
    const price = Number($v('#f_price'));
    const skus = [...root.querySelectorAll('#skuRows .sku-group-row')].map(g => ({
      name: g.querySelector('.sku-name').value.trim(),
      values: [...g.querySelectorAll('.sku-value-row')].map(r => {
        const v = r.querySelector('.sku-val').value.trim();
        const imgEl = r.querySelector('.sku-img');
        const priceEl = r.querySelector('.sku-price');
        const rawPrice = priceEl ? String(priceEl.value).trim() : '';
        /* 统一输出对象形态 { v, img?, price? }：后端 normalizeSkus 也按这个结构入库 */
        const out = { v };
        if (imgEl && imgEl.src) out.img = imgEl.src;
        if (rawPrice !== '') out.price = Number(rawPrice);
        return out;
      }).filter(v => v.v)
    })).filter(s => s.name && s.values.length);
    /* 展示图自动取第一个带图的 SKU 值，不再单独上传展示图 */
    let firstImg = '';
    outer: for (const g of skus) {
      for (const val of g.values) {
        if (val.img) { firstImg = val.img; break outer; }
      }
    }
    const art = firstImg ? { img: firstImg }
      : (base.art || { e: '🛍️', g: ['#ffe4d3', '#ffb88c'] });
    const params = [...root.querySelectorAll('#paramRows .param-row')]
      .map(r => [r.querySelector('.param-name').value.trim(), r.querySelector('.param-value').value.trim()])
      .filter(pr => pr[0] && pr[1]);
    return {
      title: $v('#f_title'),
      price,
      original: Number($v('#f_original')) || 0,
      stock: Math.max(0, parseInt($v('#f_stock'), 10) || 0),
      category: $v('#f_category'),
      sub: $v('#f_sub'),
      tag: $v('#f_tag'),
      art, skus, params
    };
  }

  async function save() {
    const payload = collect();
    if (!payload.title) return toast('请填写商品标题', 'error');
    if (!payload.category || !payload.sub) return toast('请选择商品分类与子类', 'error');
    if (!payload.price || payload.price < 1 || payload.price > 99999) return toast('请输入有效售价（1-99999）', 'error');
    /* 款式价：留空 = 沿用商品售价；一旦填写就必须合法（后端 ProductServiceImpl 同样会拒绝） */
    for (const g of payload.skus) {
      for (const v of g.values) {
        if (v.price === undefined) continue;
        if (!isFinite(v.price) || v.price < 1 || v.price > 99999) {
          return toast('款式「' + v.v + '」的价格需在 1-99999 元之间（留空则沿用商品售价）', 'error');
        }
        v.price = Math.round(v.price * 100) / 100;
      }
    }
    const btn = root.querySelector('#saveProduct');
    btn.disabled = true;
    try {
      if (isNew) await QM_API.seller.create(payload);
      else await QM_API.seller.update(base.id, payload);
      toast(isNew ? '商品已上架 🎉' : '商品信息已更新', 'success');
      m.close();
      refresh();
    } catch (e) {
      toast(e.message, 'error');
    } finally {
      btn.disabled = false;
    }
  }

  async function removeItem() {
    if (!await confirmDialog('删除商品', `确定删除「${base.title}」吗？删除后买家将无法看到该商品，且不可恢复。`, '删除', true)) return;
    try {
      await QM_API.seller.remove(base.id);
      toast('商品已删除', 'success');
      m.close();
      refresh();
    } catch (e) { toast(e.message, 'error'); }
  }
}

/* ---------- 图文详情编辑弹窗（多段落文字 + 图片，独立编辑） ----------
   先取商品全量字段：列表数据不含 detail，直接保存会把原有图文详情覆盖成空数组 */
async function openDetail(p) {
  const full = await fetchFull(p, false);
  if (!full) {
    toast('无法打开图文详情：详情接口暂时不可达，直接保存会覆盖原有内容', 'error');
    return;
  }
  renderDetail(full);
}

function renderDetail(p) {
  const base = p || {};
  const blocks = (base.detail || []).slice();
  const desc = base.desc || '';

  function detailRowHtml(b) {
    if (b.type === 'img') return `
      <div class="detail-row" data-kind="img">
        <div class="detail-row-head">
          <span class="detail-kind">图片</span>
          <div class="detail-row-actions">
            <button type="button" class="btn btn-plain btn-sm" data-move-detail data-dir="-1">上移</button>
            <button type="button" class="btn btn-plain btn-sm" data-move-detail data-dir="1">下移</button>
            <button type="button" class="btn btn-plain btn-sm" data-rm-detail>删除</button>
          </div>
        </div>
        <div class="detail-img-box">${b.url ? `<img src="${esc(b.url)}" alt="" />` : '<span class="detail-img-placeholder">未选择图片</span>'}</div>
        <button type="button" class="btn btn-plain btn-sm" data-pick-detail-img>${b.url ? '换图' : '上传图片'}</button>
        <input type="file" class="hidden" accept="image/*" />
      </div>`;
    return `
      <div class="detail-row" data-kind="text">
        <div class="detail-row-head">
          <span class="detail-kind">文字</span>
          <div class="detail-row-actions">
            <button type="button" class="btn btn-plain btn-sm" data-move-detail data-dir="-1">上移</button>
            <button type="button" class="btn btn-plain btn-sm" data-move-detail data-dir="1">下移</button>
            <button type="button" class="btn btn-plain btn-sm" data-rm-detail>删除</button>
          </div>
        </div>
        <textarea class="detail-text" rows="3" maxlength="500" placeholder="输入一段图文详情文字">${esc(b.text || '')}</textarea>
      </div>`;
  }

  const m = modal(`
    <h3>图文详情</h3>
    <p class="modal-sub">按顺序展示在商品详情页「图文详情」Tab：文字与图片可穿插、排序（先写文字再插图片即可）</p>

    <div id="detailRows">${blocks.map(detailRowHtml).join('')}</div>
    <div class="detail-add-actions">
      <button type="button" class="btn btn-plain btn-sm" data-add-detail-text>＋ 添加文字段落</button>
      <button type="button" class="btn btn-plain btn-sm" data-add-detail-img>＋ 添加图片</button>
    </div>

    <h4 class="editor-sec">商品简介 <small>详情页首段展示，留空时自动取第一段文字</small></h4>
    <div class="form-row"><textarea id="f_desc" rows="2" maxlength="200" placeholder="一句话简介">${esc(desc)}</textarea></div>

    <div class="modal-actions">
      <button type="button" class="btn btn-plain" data-close>取消</button>
      <button type="button" class="btn btn-primary" id="saveDetail">保 存</button>
    </div>`, { wide: true });

  const root = m.root;

  root.addEventListener('click', e => {
    const t = e.target;
    if (!t || !t.closest) return;
    if (t.closest('[data-add-detail-text]')) {
      root.querySelector('#detailRows').insertAdjacentHTML('beforeend', detailRowHtml({ type: 'text' }));
      return;
    }
    if (t.closest('[data-add-detail-img]')) {
      root.querySelector('#detailRows').insertAdjacentHTML('beforeend', detailRowHtml({ type: 'img' }));
      return;
    }
    if (t.closest('[data-rm-detail]')) { const r = t.closest('.detail-row'); if (r) r.remove(); return; }
    if (t.closest('[data-move-detail]')) {
      const row = t.closest('.detail-row');
      const dir = Number(t.closest('[data-move-detail]').dataset.dir);
      if (dir < 0 && row.previousElementSibling) root.querySelector('#detailRows').insertBefore(row, row.previousElementSibling);
      else if (dir > 0 && row.nextElementSibling) root.querySelector('#detailRows').insertBefore(row.nextElementSibling, row);
      return;
    }
    if (t.closest('[data-pick-detail-img]')) {
      const row = t.closest('.detail-row');
      const input = row && row.querySelector('input[type=file]');
      if (input) input.click();
      return;
    }
    if (t.closest('#saveDetail')) return save();
  });

  root.addEventListener('change', e => {
    const input = e.target;
    if (!input || input.type !== 'file') return;
    const row = input.closest('.detail-row');
    pickImage(input, url => {
      if (row) {
        row.querySelector('.detail-img-box').innerHTML = `<img src="${esc(url)}" alt="" />`;
        const btn = row.querySelector('[data-pick-detail-img]');
        if (btn) btn.textContent = '换图';
      }
    });
    input.value = '';
  });

  async function save() {
    const rows = [...root.querySelectorAll('#detailRows .detail-row')];
    const detail = rows.map(r => r.dataset.kind === 'img'
      ? { type: 'img', url: r.querySelector('.detail-img-box img') ? r.querySelector('.detail-img-box img').src : '' }
      : { type: 'text', text: r.querySelector('.detail-text').value.trim() })
      .filter(b => (b.type === 'img' && b.url) || (b.type === 'text' && b.text));
    const descShort = root.querySelector('#f_desc').value.trim();
    const firstText = detail.find(b => b.type === 'text');
    const payload = {
      detail,
      desc: descShort || (firstText ? firstText.text.slice(0, 120) : '')
    };
    const btn = root.querySelector('#saveDetail');
    btn.disabled = true;
    try {
      await QM_API.seller.update(base.id, payload);
      toast('图文详情已更新', 'success');
      m.close();
      refresh();
    } catch (err) { toast(err.message, 'error'); }
    finally { btn.disabled = false; }
  }
}

onMounted(() => {
  offShopProfile = QM_STORE.on('shopProfile', () => { shopTick.value++; });
  refresh();
});
onBeforeUnmount(() => { if (offShopProfile) { offShopProfile(); offShopProfile = null; } });
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 我的店铺 / 商品管理</div>
        <h1>商品管理 <small>PRODUCTS</small></h1>
      </div>
      <div class="page-head-actions">
        <button v-if="svc" class="btn btn-primary" @click="addNew">＋ 新增商品</button>
        <a class="btn btn-plain" href="#/seller">返回我的店铺</a>
      </div>
    </div>

    <template v-if="svc">
      <div class="seller-tip">💡 当前店铺：<b>{{ svc.shopName }}</b>，共 {{ listData.length }} 件商品（在售 {{ onSaleCount }} · 已下架 {{ offSaleCount }}）。点击卡片展示图可预览买家视角，卡片上可直接改价 / 上下架，「编辑」修改商品信息与款式图，「详情」编辑图文详情。</div>

      <!-- 工具栏：筛选 + 排序 + 搜索（与店铺主页商品区同款控件风格） -->
      <div class="seller-product-toolbar">
        <div class="tabs seller-filter-tabs">
          <button :class="{ active: filter === 'all' }" @click="filter = 'all'">全部 {{ listData.length }}</button>
          <button :class="{ active: filter === 'on' }" @click="filter = 'on'">在售 {{ onSaleCount }}</button>
          <button :class="{ active: filter === 'off' }" @click="filter = 'off'">已下架 {{ offSaleCount }}</button>
        </div>
        <div class="seller-sorts">
          <button :class="{ active: sort === 'default' }" @click="sort = 'default'">综合</button>
          <button :class="{ active: sort === 'sales' }" @click="sort = 'sales'">销量</button>
          <button :class="{ active: sort === 'priceAsc' }" @click="sort = 'priceAsc'">价格↑</button>
          <button :class="{ active: sort === 'priceDesc' }" @click="sort = 'priceDesc'">价格↓</button>
        </div>
        <input class="seller-search" v-model="keyword" placeholder="搜索商品标题…" maxlength="30" />
      </div>

      <!-- 商品卡片网格（与买家端 product-card 同款视觉） -->
      <div v-if="loading" class="empty-state">
        <div class="empty-icon">⏳</div>
        <h3>正在加载商品…</h3>
      </div>
      <div v-else-if="filtered.length" class="product-grid seller-product-grid">
        <div v-for="p in filtered" :key="p.id" class="product-card sm-card" :class="{ 'is-off': p.onSale === false }">
          <a class="pc-art sm-art" :style="artStyle(p.art)" :href="'#/detail/' + encodeURIComponent(p.id)" :title="'预览：' + p.title">
            <span v-if="p.tag" class="pc-tag">{{ p.tag }}</span>
            <span v-if="p.onSale === false" class="sm-flag">已下架</span>
            <img v-if="p.art && p.art.img" class="art-img" :src="p.art.img" alt="" loading="lazy" />
            <span v-else style="font-size: inherit">{{ p.art && p.art.e ? p.art.e : '🛍️' }}</span>
          </a>
          <div class="pc-info">
            <h3 class="ellipsis-2">{{ p.title }}</h3>
            <div class="pc-price-row">
              <span class="price" :title="pHasRange(p) ? '款式价区间 ¥' + pRangeText(p) : ''"><i>¥</i>{{ pPrice(p).int }}<em v-if="pPrice(p).dec">{{ pPrice(p).dec }}</em><small v-if="pHasRange(p)" class="price-from">起</small></span>
              <del v-if="p.original"><span class="price"><i>¥</i>{{ pOrig(p).int }}<em v-if="pOrig(p).dec">{{ pOrig(p).dec }}</em></span></del>
            </div>
            <div class="pc-meta"><span>销量 {{ sales(p.sales) }}</span><span>库存 {{ p.stock }} 件</span></div>
            <div class="sm-meta">
              <span>{{ p.category }} · {{ p.sub }}</span>
              <span class="sm-status" :class="p.onSale === false ? 'off' : 'on'">{{ p.onSale === false ? '已下架' : '在售' }}</span>
            </div>
            <div v-if="pHasRange(p)" class="sm-sku-range">款式价 ¥{{ pRangeText(p) }}</div>
            <div class="sm-ops">
              <span class="sm-price-box"><i>¥</i><input class="sm-price-input" type="number" :value="p.price" min="1" max="99999" step="0.01" @change="onPrice(p, $event)" title="点击修改售价" /></span>
              <button class="btn btn-sm btn-plain" @click="openDetail(p)">详情</button>
              <button class="btn btn-sm btn-plain" @click="edit(p)">编辑</button>
              <button class="btn btn-sm" :class="p.onSale === false ? 'btn-ghost' : 'btn-danger'" @click="toggle(p)">{{ p.onSale === false ? '上架' : '下架' }}</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 无商品 / 筛选无结果 -->
      <div v-else class="empty-state">
        <div class="empty-icon">🛍️</div>
        <h3>{{ listData.length ? '没有符合条件的商品' : '店铺还没有商品' }}</h3>
        <p>{{ listData.length ? '换个筛选条件或搜索关键词试试' : '点击右上角「新增商品」上架第一件好物' }}</p>
        <button v-if="!listData.length" class="btn btn-primary" @click="addNew">＋ 新增商品</button>
      </div>
    </template>

    <!-- 已登录但未开店：可直接开店 -->
    <div v-else-if="QM_STORE.state.user" class="empty-state">
      <div class="empty-icon">🏪</div>
      <h3>你还没有店铺</h3>
      <p>当前账号「{{ QM_STORE.state.user.nickname }}」名下还没有店铺。先开通店铺，即可上架与管理商品。</p>
      <button class="btn btn-primary" @click="openShop">立即开店</button>
      <a class="btn btn-plain" href="#/home">先逛逛</a>
    </div>

    <!-- 未登录 -->
    <div v-else class="empty-state">
      <div class="empty-icon">🔐</div>
      <h3>请先登录</h3>
      <p>全站只有一套用户账号，登录后即可管理自己店铺的商品。</p>
      <a class="btn btn-primary" href="#/login?redirect=%2Fseller%2Fproducts">去登录</a>
    </div>
  </div>
</template>
