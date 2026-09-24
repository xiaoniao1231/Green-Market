/* =========================================================
   core/addressModal.js —— 收货地址管理弹窗（个人中心 / 购物车结算共用）
   ---------------------------------------------------------
   原先这段逻辑写在 ProfileView 组件内部，只有个人中心能打开；购物车结算弹窗
   只能给一个「管理地址」链接跳转到个人中心。抽到 core 层后两处共用同一个弹窗，
   行为完全一致（增删改、设默认、省市区三级联动、地址标签）。
   调用方通过 onSaved 回调在「保存 / 删除 / 设默认」成功后刷新自己的地址视图。
   ========================================================= */
import QM_UI from './ui.js';
import QM_STORE from './store.js';
import QM_API from './api.js';

const { esc, toast, modal, confirmDialog } = QM_UI;

export default async function openAddressModal({ onSaved } = {}) {
  const notify = () => { try { onSaved && onSaved(); } catch (e) { console.error(e); } };

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
    ({ REGIONS } = await import('./regions.js'));
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
      notify();
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
      try { await QM_API.addresses.setDefault(a.dataset.id); toast('已设为默认地址', 'success'); renderList(); notify(); }
      catch (e) { toast(e.message || '操作失败', 'error'); }
    }
    else if (a.dataset.action === 'del-addr') {
      if (await confirmDialog('删除地址', '确定删除该收货地址吗？', '删除', true)) {
        try { await QM_API.addresses.remove(a.dataset.id); toast('已删除'); renderList(); notify(); }
        catch (e) { toast(e.message || '删除失败', 'error'); }
      }
    }
  };

  return m;
}
