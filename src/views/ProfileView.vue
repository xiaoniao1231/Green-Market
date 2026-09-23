<script setup>
/* =========================================================
   青集市 · views/ProfileView.vue —— 个人中心页
   移植自 mall-web/js/pages/profile.js（页面结构 / 交互逻辑不变）
   含：登录入口（data-action=open-login/logout 由 App.vue 全局代理处理）、
   地址管理弹窗（新增 / 编辑 / 删除 / 设默认，接口见 docs/地址簿接口文档.md）、
   优惠券展示弹窗（对应预留接口 /coupons）。
   ========================================================= */
import { computed, onBeforeUnmount } from 'vue';
import QM_UI from '../core/ui.js';
import QM_STORE from '../core/store.js';
import QM_API from '../core/api.js';
import { refreshView } from '../core/viewRefresh.js';

const { esc, toast, modal, confirmDialog } = QM_UI;

/* ---------- 页面数据：登录态与各项计数 ----------
   原实现是「视图创建时读一次的常量快照」：新增/删除地址、加入购物车后，
   页面上的"N 个地址 / N 个收藏"不会跟着变（store 事件也不会触发本页重渲染）。
   改为 computed + 订阅 store 事件：任一数据变化时计数自动重算。 */
const user = computed(() => QM_STORE.state.user);
const favCount = computed(() => (QM_STORE.state.favorites || []).length);
const cartCount = computed(() => (QM_STORE.state.cart || []).length);
const orderCount = computed(() => (QM_STORE.state.orders || []).length);
const couponCount = computed(() => QM_STORE.coupon.list().length);
const addrCount = computed(() => QM_STORE.addr.list().length);
/* 计数依赖的 store 事件（state 本身不是响应式的，靠这些事件驱动重算） */
const offs = [
  QM_STORE.on('cart', () => {}), QM_STORE.on('favorites', () => {}),
  QM_STORE.on('orders', () => {}), QM_STORE.on('addresses', () => {}),
  QM_STORE.on('user', () => {})
];
onBeforeUnmount(() => { offs.forEach(off => { try { off(); } catch (e) { /* 忽略 */ } }); });

/* ---------- 头像：上传阿里云 OSS ----------
   user.avatar 存的是图片完整地址（旧数据可能是 emoji 字符，显示时兼容回退；
   底色调色盘已移除，不再有 avatarColor 字段）。 */
const AVATAR_MAX_SIZE = 5 * 1024 * 1024; // 头像图片大小上限 5MB
/* 判断头像是否为图片地址：https 为 OSS 落库地址，blob: 为弹窗内本地预览地址 */
const isAvatarImage = (v) => typeof v === 'string' && /^(https?:|blob:)/i.test(v.trim());

/* 资料摘要文案（性别 / 签名），显示在封面副标题 */
function userProfileText() {
  const me = user.value;
  if (!me) return '';
  const parts = [];
  if (me.gender === 'male') parts.push('♂ 男');
  else if (me.gender === 'female') parts.push('♀ 女');
  else parts.push('保密');
  if (me.signature) parts.push('「' + me.signature + '」');
  return parts.join(' · ');
}

/* ---------- 编辑资料弹窗（昵称 / 头像 / 性别 / 个性签名） ---------- */
function profileModal() {
  const me = user.value; // computed 在 script 中不会自动解包，必须取 .value
  if (!me) return toast('请先登录', 'error');
  const m = modal(`
    <div>
      <h3>编辑资料</h3>
      <p class="modal-sub">修改昵称、头像、性别与个性签名</p>
      <div class="form-row"><label>昵称</label><input id="pfNickname" maxlength="20" /></div>
      <div class="form-row"><label>性别</label>
        <div class="gender-row">
          <label class="gender-opt"><input type="radio" name="pfGender" value="male" />男</label>
          <label class="gender-opt"><input type="radio" name="pfGender" value="female" />女</label>
          <label class="gender-opt"><input type="radio" name="pfGender" value="secret" />保密</label>
        </div>
      </div>
      <div class="form-row"><label>头像</label>
        <div class="avatar-upload">
          <span id="pfAvatarPreview" class="member-avatar big avatar-preview"></span>
          <div class="avatar-upload-actions">
            <button type="button" class="btn btn-plain" id="pfPickAvatar">选择图片</button>
            <small>支持 jpg / png / webp / gif，不超过 5MB；图片将上传至阿里云 OSS</small>
            <input type="file" id="pfAvatarFile" accept="image/*" class="hidden" />
          </div>
        </div>
      </div>
      <div class="form-row"><label>个性签名</label><input id="pfSignature" maxlength="40" placeholder="一句话介绍自己" /></div>
      <div class="modal-actions" style="margin-top:0">
        <button class="btn btn-plain" data-close>取消</button>
        <button class="btn btn-primary" id="pfSave">保存资料</button>
      </div>
    </div>`);

  let avatar = (me.avatar || '').trim();        // 当前头像（旧数据可能是 emoji）
  let pickedFile = null;                        // 本次新选的头像文件（点保存时才上传）
  let objectUrl = null;                         // 本地预览 URL（关闭 / 保存后释放）
  const preview = m.root.querySelector('#pfAvatarPreview');
  const fileInput = m.root.querySelector('#pfAvatarFile');
  const renderPreview = () => {
    preview.innerHTML = '';
    if (isAvatarImage(avatar)) preview.innerHTML = `<img src="${esc(avatar)}" alt="头像" />`;
    else preview.textContent = avatar || (me.nickname || '语').slice(0, 1);
  };
  /* 预填注册时已有的昵称 / 性别 / 签名 / 头像 */
  m.root.querySelector('#pfNickname').value = me.nickname || '';
  const gender = ['male', 'female', 'secret'].includes(me.gender) ? me.gender : 'secret';
  const g = m.root.querySelector('input[name="pfGender"][value="' + gender + '"]');
  if (g) g.checked = true;
  m.root.querySelector('#pfSignature').value = me.signature || '';
  renderPreview();
  /* 关闭弹窗（取消 / 保存）时释放本地预览 URL，避免内存泄漏 */
  m.root.addEventListener('click', e => {
    if (e.target.closest('[data-close]') && objectUrl) { URL.revokeObjectURL(objectUrl); objectUrl = null; }
  });
  m.root.querySelector('#pfPickAvatar').onclick = () => fileInput.click();
  fileInput.onchange = () => {
    const f = fileInput.files && fileInput.files[0];
    if (!f) return;
    if (!/^image\//.test(f.type)) return toast('请选择图片文件', 'error');
    if (f.size > AVATAR_MAX_SIZE) return toast('头像图片不能超过 5MB', 'error');
    if (objectUrl) URL.revokeObjectURL(objectUrl); // 换图时先释放上一张预览
    pickedFile = f;
    objectUrl = URL.createObjectURL(f);
    avatar = objectUrl;
    renderPreview();
  };
  m.root.querySelector('#pfSave').onclick = async () => {
    const nickname = m.root.querySelector('#pfNickname').value.trim();
    if (!nickname) return toast('昵称不能为空', 'error');
    const picked = m.root.querySelector('input[name="pfGender"]:checked');
    const signature = m.root.querySelector('#pfSignature').value.trim();
    const btn = m.root.querySelector('#pfSave');
    btn.disabled = true; btn.textContent = '保存中…';
    try {
      /* ① 选过新图 → 先上传到阿里云 OSS，拿到图片地址（multipart → POST /users/avatar） */
      if (pickedFile) {
        const data = await QM_API.user.uploadAvatar(pickedFile);
        const url = data && (data.url || data.avatar || data.fileUrl);
        if (!url) throw new Error('头像上传成功但未返回图片地址');
        avatar = url;
      }
      /* ② 资料（含头像地址）提交后端落库（PUT /users/profile），成功后同步本地登录态 */
      const payload = { nickname, gender: picked ? picked.value : 'secret', avatar, signature };
      await QM_API.user.updateProfile(payload);
      QM_STORE.user.update(payload);
      if (objectUrl) { URL.revokeObjectURL(objectUrl); objectUrl = null; }
      toast('资料已更新', 'success');
      m.close();
      refreshView(); // 重挂载本页刷新封面头像 / 昵称 / 性别 / 签名
    } catch (e) {
      /* 保存失败：保留弹窗与本地预览（不释放 objectUrl），便于用户重试 */
      toast(e.message || '保存失败', 'error');
    } finally {
      btn.disabled = false; btn.textContent = '保存资料';
    }
  };
}

/* ---------- 地址管理弹窗（原 addressModal，接口见 docs/地址簿接口文档.md） ----------
   打开先同步一次地址列表（QM_API.addresses.list，后端实现后为服务端数据；
   未实现时回退本地存储），增删改设默认全部走接口，成功后再重新拉取列表（写穿缓存）。 */
async function addressModal() {
  const m = modal(`
    <div>
      <h3>收货地址</h3>
      <p class="modal-sub">新增、编辑、删除与设置默认地址</p>
      <div id="addrList"></div>
      <div id="addrForm" class="hidden" style="border-top:1px dashed var(--border);padding-top:14px;margin-top:10px">
        <div class="form-row"><label>收货人</label><input id="afName" maxlength="20" placeholder="姓名" /></div>
        <div class="form-row"><label>手机号</label><input id="afPhone" maxlength="11" placeholder="11 位手机号" /></div>
        <div class="form-row">
          <label>所在地区</label>
          <div class="region-selects">
            <select id="afProv" disabled><option value="">省份</option></select>
            <select id="afCity" disabled><option value="">城市</option></select>
            <select id="afDist" disabled><option value="">区县</option></select>
          </div>
          <p class="hint" id="afRegionHint">省 / 市 / 区三级联动；台湾各县市暂无区级数据，选到市级即可</p>
        </div>
        <div class="form-row"><label>详细地址</label><input id="afDetail" maxlength="60" placeholder="街道、楼牌号等" /></div>
        <div class="form-row">
          <label>地址标签</label>
          <div class="tag-chips" id="afTags">
            <button type="button" class="tag-chip" data-tag="家">家</button>
            <button type="button" class="tag-chip" data-tag="公司">公司</button>
            <button type="button" class="tag-chip" data-tag="学校">学校</button>
            <button type="button" class="tag-chip" data-tag="其他">其他</button>
          </div>
          <input id="afTagCustom" class="hidden" maxlength="6" placeholder="自定义标签（最多 6 个字）" style="margin-top:8px" />
          <p class="hint">选填；选中标签后会在地址列表里显示，再点一次可取消</p>
        </div>
        <div class="form-row"><label><input type="checkbox" id="afDefault" style="width:auto;height:auto;margin-right:6px" />设为默认地址</label></div>
        <div class="modal-actions" style="margin-top:0">
          <button class="btn btn-plain" data-close-form>取消</button>
          <button class="btn btn-primary" id="afSave">保存地址</button>
        </div>
      </div>
      <div id="addrListFooter" class="modal-actions">
        <button class="btn btn-plain" data-close>关闭</button>
        <button class="btn btn-primary" id="addAddr">＋ 新增地址</button>
      </div>
    </div>`, { wide: true });

  let editingId = null;

  /* ---------- 所在地区：省 / 市 / 区三级联动 ----------
     行政区划数据（core/regions.js，约 56KB）只在打开地址弹窗时动态 import，不进首屏包 */
  let REGIONS = null;
  try {
    ({ REGIONS } = await import('../core/regions.js'));
  } catch (e) {
    m.root.querySelector('#afRegionHint').textContent = '地区数据加载失败，请刷新页面后重试';
  }
  const provSel = m.root.querySelector('#afProv');
  const citySel = m.root.querySelector('#afCity');
  const distSel = m.root.querySelector('#afDist');
  const nameOf = x => (typeof x === 'string' ? x : x.name);
  /* 「广东省 ↔ 广东」「南山区 ↔ 南山」都认：去掉行政区划后缀再比对，用于回填历史地址文本 */
  const stripSuffix = n => String(n).replace(/(特别行政区|维吾尔自治区|回族自治区|壮族自治区|自治区|市辖区|地区|盟|省|市|区|县|旗)$/g, '');
  const matchItem = (list, text) => {
    const t = String(text || '').trim();
    if (!t || !list || !list.length) return null;
    const exact = list.find(x => nameOf(x) === t);
    if (exact) return exact;
    const key = stripSuffix(t);
    return key ? list.find(x => stripSuffix(nameOf(x)) === key) || null : null;
  };
  const setOptions = (sel, names, placeholder) => {
    sel.innerHTML = `<option value="">${placeholder}</option>`
      + names.map(n => `<option value="${esc(n)}">${esc(n)}</option>`).join('');
  };
  /* 逐级填充：上一级没选时下一级禁用并给出占位文案；district 为空（如台湾各县市）时降级成两级选择 */
  const fillDistricts = (prov, cityName, keep) => {
    const city = prov && prov.cities.find(c => c.name === cityName);
    const names = city ? city.districts : [];
    setOptions(distSel, names, !city ? '请先选择城市' : (names.length ? '请选择区县' : '该地区暂无区县'));
    distSel.disabled = !names.length;
    distSel.value = keep && names.includes(keep) ? keep : '';
  };
  const fillCities = (provName, keepCity, keepDist) => {
    const prov = REGIONS ? REGIONS.find(p => p.name === provName) : null;
    const names = prov ? prov.cities.map(c => c.name) : [];
    setOptions(citySel, names, prov ? '请选择城市' : '请先选择省份');
    citySel.disabled = !prov;
    citySel.value = keepCity && names.includes(keepCity) ? keepCity : '';
    fillDistricts(prov, citySel.value, keepDist);
  };
  /* 回填：把地址里的 region 文本（如「广东省 深圳市 南山区」）拆开逐级匹配。
     直辖市的 region 是「北京市 东城区」（省市同名被省略过一次），所以市匹配不到时
     要把这一段当区来试 —— 这类历史数据的形态在实际存储里很常见 */
  const setRegion = (text) => {
    if (!REGIONS) return;
    const parts = String(text || '').trim().split(/[\s/、,，|]+/).filter(Boolean);
    let prov = null, city = null, dist = null;
    for (const part of parts) {
      if (!prov) { prov = matchItem(REGIONS, part); continue; }
      if (!city) {
        const c = matchItem(prov.cities, part);
        if (c) { city = c; continue; }
      }
      if (!dist && city) {
        const d = matchItem(city.districts, part);
        if (d) { dist = d; continue; }
      }
      /* 省下只有一个市的（直辖市）：这一段直接当区匹配 */
      if (!dist && !city && prov.cities.length === 1) {
        const only = prov.cities[0];
        const d = matchItem(only.districts, part);
        if (d) { city = only; dist = d; }
      }
    }
    provSel.value = prov ? prov.name : '';
    fillCities(provSel.value, city ? city.name : '', dist || '');
  };
  /* 取值：直辖市省市同名时只保留一次，避免出现「北京市 北京市 东城区」 */
  const currentRegion = () => {
    const p = provSel.value, c = citySel.value, d = distSel.value;
    return [p, c === p ? '' : c, d].filter(Boolean).join(' ');
  };
  /* 省下只有一个市（直辖市）时自动选中该市，省得用户再点一次「北京市」 */
  provSel.onchange = () => {
    const prov = REGIONS ? REGIONS.find(p => p.name === provSel.value) : null;
    fillCities(provSel.value, prov && prov.cities.length === 1 ? prov.cities[0].name : '', '');
  };
  citySel.onchange = () => fillDistricts(REGIONS ? REGIONS.find(p => p.name === provSel.value) : null, citySel.value, '');
  /* 省级列表一次性填充并开放交互；市 / 区保持禁用，等用户逐级选择后再启用 */
  if (REGIONS) {
    setOptions(provSel, REGIONS.map(p => p.name), '请选择省份');
    provSel.disabled = false;
    fillCities('', '', '');
  }

  /* ---------- 地址标签（家 / 公司 / 学校 / 其他；选「其他」时可自定义，再点一次取消） ---------- */
  const tagChips = Array.from(m.root.querySelectorAll('#afTags .tag-chip'));
  const tagCustom = m.root.querySelector('#afTagCustom');
  const presetTags = tagChips.map(c => c.dataset.tag);
  let currentTag = '';
  const paintTags = () => {
    tagChips.forEach(c => c.classList.toggle('active', currentTag === c.dataset.tag));
    tagCustom.classList.toggle('hidden', currentTag !== '其他');
  };
  const setTag = (tag) => {
    const t = String(tag || '').trim();
    if (!t) { currentTag = ''; tagCustom.value = ''; }
    else if (presetTags.includes(t) && t !== '其他') { currentTag = t; tagCustom.value = ''; }
    else { currentTag = '其他'; tagCustom.value = t.slice(0, 6); }
    paintTags();
  };
  const currentTagValue = () => (currentTag === '其他' ? tagCustom.value.trim().slice(0, 6) : currentTag);
  tagChips.forEach(chip => {
    chip.onclick = () => {
      const t = chip.dataset.tag;
      if (currentTag === t) { currentTag = ''; tagCustom.value = ''; }
      else { currentTag = t; if (t !== '其他') tagCustom.value = ''; }
      paintTags();
      if (currentTag === '其他') tagCustom.focus();
    };
  });
  tagCustom.oninput = () => { tagCustom.value = tagCustom.value.slice(0, 6); };

  /* 地址列表加载失败的原因：addresses 是 strict 接口，失败时如实提示，不用本地缓存冒充 */
  let loadError = '';
  const renderList = () => {
    const list = QM_STORE.addr.list();
    m.root.querySelector('#addrList').innerHTML = list.length ? list.map(a => `
      <div class="addr-option">
        <b>${esc(a.name)} ${esc(a.phone)}${a.isDefault ? ' <span class="pill pill-orange">默认</span>' : ''}${a.tag ? ` <span class="pill pill-gray">${esc(a.tag)}</span>` : ''}</b>
        <small>${esc(a.region)} ${esc(a.detail)}</small>
        <span class="addr-actions">
          <button type="button" class="link-btn" data-action="edit-addr" data-id="${esc(a.id)}">编辑</button>
          ${a.isDefault ? '' : `<button type="button" class="link-btn" data-action="default-addr" data-id="${esc(a.id)}">设默认</button>`}
          <button type="button" class="link-btn danger" data-action="del-addr" data-id="${esc(a.id)}">删除</button>
        </span>
      </div>`).join('') : (loadError
        ? `<p class="hint" style="color:var(--accent-ink)">地址加载失败：${esc(loadError)}</p>`
        : '<p class="modal-sub">暂无收货地址</p>');
  };
  const openForm = (addr) => {
    editingId = addr ? addr.id : null;
    m.root.querySelector('#addrForm').classList.remove('hidden');
    m.root.querySelector('#addrListFooter').classList.add('hidden');
    m.root.querySelector('#afName').value = addr ? addr.name : '';
    m.root.querySelector('#afPhone').value = addr ? addr.phone : '';
    setRegion(addr ? addr.region : '');
    m.root.querySelector('#afDetail').value = addr ? addr.detail : '';
    setTag(addr ? addr.tag : '');
    m.root.querySelector('#afDefault').checked = addr ? addr.isDefault : false;
  };
  const closeForm = () => {
    m.root.querySelector('#addrForm').classList.add('hidden');
    m.root.querySelector('#addrListFooter').classList.remove('hidden');
  };
  /* 打开弹窗先同步一次地址列表：strict 接口，拉取失败时列表区与 toast 都要说明原因，
     绝不能拿浏览器里的旧缓存冒充后端数据（会导致「看着有地址、后端其实没有」） */
  try { await QM_API.addresses.list(); }
  catch (e) { loadError = (e && e.message) || '地址列表加载失败'; toast(loadError, 'error'); }
  renderList();
  m.root.querySelector('#addAddr').onclick = () => openForm(null);
  m.root.querySelector('[data-close-form]').onclick = () => closeForm();
  m.root.querySelector('#afSave').onclick = async () => {
    const data = {
      name: m.root.querySelector('#afName').value.trim(),
      phone: m.root.querySelector('#afPhone').value.trim(),
      region: currentRegion(),
      detail: m.root.querySelector('#afDetail').value.trim(),
      tag: currentTagValue(),
      isDefault: m.root.querySelector('#afDefault').checked
    };
    if (!data.name || !data.phone || !data.detail) return toast('请完整填写地址信息', 'error');
    if (!provSel.value || (!citySel.disabled && !citySel.value)) return toast('请选择完整的省 / 市', 'error');
    if (!distSel.disabled && !distSel.value) return toast('请选择区县', 'error');
    if (!/^1\d{10}$/.test(data.phone)) return toast('请输入正确的 11 位手机号', 'error');
    const btn = m.root.querySelector('#afSave');
    btn.disabled = true; btn.textContent = '保存中…';
    try {
      if (editingId) await QM_API.addresses.update(editingId, data);
      else await QM_API.addresses.create(data);
      toast('地址已保存', 'success');
      closeForm();
      renderList();
    } catch (e) {
      toast(e.message || '保存失败', 'error');
    } finally {
      btn.disabled = false; btn.textContent = '保存地址';
    }
  };
  m.root.querySelector('#addrList').onclick = async e => {
    const a = e.target.closest('[data-action]');
    if (!a) return;
    const addr = QM_STORE.addr.list().find(x => x.id === a.dataset.id);
    if (a.dataset.action === 'edit-addr') openForm(addr);
    else if (a.dataset.action === 'default-addr') {
      try { await QM_API.addresses.setDefault(a.dataset.id); toast('已设为默认地址', 'success'); renderList(); }
      catch (e) { toast(e.message || '操作失败', 'error'); }
    }
    else if (a.dataset.action === 'del-addr') {
      if (await confirmDialog('删除地址', '确定删除该收货地址吗？', '删除', true)) {
        try { await QM_API.addresses.remove(a.dataset.id); toast('已删除'); renderList(); }
        catch (e) { toast(e.message || '删除失败', 'error'); }
      }
    }
  };
}

/* ---------- 优惠券弹窗（原 couponModal，对应预留接口 /coupons） ---------- */
function couponModal() {
  const m = modal(`
    <div>
      <h3>我的优惠券</h3>
      <p class="modal-sub">领取与使用状态（对应预留接口 /coupons）</p>
      <div id="couponList"></div>
      <div class="modal-actions"><button class="btn btn-plain" data-close>关闭</button></div>
    </div>`, { wide: true });
  const renderList = () => {
    const list = QM_STORE.coupon.list();
    m.root.querySelector('#couponList').innerHTML = list.map(c => `
      <div class="addr-option" style="display:flex;align-items:center;gap:12px">
        <div style="flex:none;width:86px;text-align:center;background:var(--brand-soft);border-radius:8px;padding:10px 0">
          <b style="color:var(--brand);font-size:20px">¥${c.amount}</b>
          <small style="display:block;color:var(--text-3)">满 ${c.threshold} 可用</small>
        </div>
        <div style="flex:1">
          <b>${esc(c.title)}</b>
          <small style="display:block;color:var(--text-3)">有效期至 ${esc(c.expire)}</small>
        </div>
        <span class="pill ${c.status === 'used' ? 'pill-gray' : 'pill-green'}">${c.status === 'used' ? '已使用' : '未使用'}</span>
      </div>`).join('');
  };
  renderList();
}
</script>

<template>
  <div>
    <div class="page-head"><div><div class="crumb">首页 / 个人中心</div><h1>我的青集市</h1></div></div>
    <div class="profile-cover">
      <span class="member-avatar big"><img v-if="user && isAvatarImage(user.avatar)" :src="user.avatar" alt="头像" /><template v-else>{{ user ? ((user.avatar && user.avatar.trim()) ? user.avatar : user.nickname.slice(0, 1)) : '语' }}</template></span>
      <div>
        <h1>{{ user ? user.nickname : '轻语用户' }}</h1>
        <p>{{ user ? '账号 @' + user.userId + (userProfileText() ? ' · ' + userProfileText() : '') : '登录后享受完整服务 · 记录每一次心动的发现' }}</p>
      </div>
      <div class="cover-actions">
        <button v-if="user" class="btn" @click="profileModal">编辑资料</button>
        <button v-if="user" class="btn btn-plain" data-action="logout">退出登录</button>
        <button v-else class="btn" data-action="open-login">立即登录</button>
      </div>
    </div>
    <div class="profile-stats">
      <button data-action="goto-fav"><b>{{ favCount }}</b><small>收藏商品</small></button>
      <button data-action="goto-cart"><b>{{ cartCount }}</b><small>购物车</small></button>
      <button data-action="goto-orders"><b>{{ orderCount }}</b><small>全部订单</small></button>
      <button data-action="goto-coupon" @click="couponModal"><b>{{ couponCount }}</b><small>优惠券</small></button>
    </div>
    <div class="profile-panel">
      <h3>⌁ 我的订单</h3>
      <div class="service-grid">
        <button data-action="goto-orders" data-id="pending"><span class="s-icon">◴</span><b>待付款</b><small>及时付款不错过好价</small></button>
        <button data-action="goto-orders" data-id="paid"><span class="s-icon">▣</span><b>待发货</b><small>卖家正在准备</small></button>
        <button data-action="goto-orders" data-id="shipped"><span class="s-icon">▤</span><b>待收货</b><small>物流实时可查</small></button>
        <button data-action="goto-orders" data-id="done"><span class="s-icon">♧</span><b>评价晒单</b><small>分享你的体验</small></button>
      </div>
    </div>
    <div class="profile-panel">
      <h3>✦ 我的服务</h3>
      <div class="service-grid">
        <button data-action="goto-fav"><span class="s-icon">♡</span><b>我的收藏</b><small>{{ favCount }} 件商品</small></button>
        <button data-action="open-addr" @click="addressModal"><span class="s-icon">⌂</span><b>收货地址</b><small>{{ addrCount }} 个地址</small></button>
        <button data-action="open-coupon" @click="couponModal"><span class="s-icon">🎫</span><b>优惠券</b><small>{{ couponCount }} 张可用</small></button>
        <button data-action="goto-chat"><span class="s-icon">◌</span><b>联系卖家</b><small>从商品详情页发起咨询</small></button>
        <button data-action="goto-seller"><span class="s-icon">🏪</span><b>我的店铺</b><small>{{ user && user.shopId ? '管理我的店铺' : '一个账号，既能买也能卖' }}</small></button>
        <button data-action="goto-placeholder" data-id="浏览足迹"><span class="s-icon">👣</span><b>浏览足迹</b><small>功能预留</small></button>
        <button data-action="goto-placeholder" data-id="账户设置"><span class="s-icon">⚙</span><b>账户设置</b><small>功能预留</small></button>
        <button data-action="goto-placeholder" data-id="售后服务"><span class="s-icon">📋</span><b>售后服务</b><small>功能预留</small></button>
      </div>
    </div>
  </div>
</template>
