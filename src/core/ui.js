/* =========================================================
   青集市 · ui.js —— 通用 UI 组件与格式化工具
   ========================================================= */
import QM_CFG from './config.js';
import QM_JWT from './jwt.js';
import QM_API from './api.js';
import QM_STORE from './store.js';
import { refreshView } from './viewRefresh.js';

/* 当前打开的登录/注册弹窗关闭句柄：同一时间只允许一个，避免重复叠加 */
let currentLoginModal = null;

  const $ = (sel, root) => (root || document).querySelector(sel);

  /* ---------- 格式化 ---------- */
  const esc = text => String(text === undefined || text === null ? '' : text)
    .replace(/[&<>'"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[c]));

  const price = (n, decimals) => {
    n = Number(n || 0);
    const text = decimals !== undefined ? n.toFixed(decimals) : (n % 1 === 0 ? String(n) : n.toFixed(2));
    const [int, dec] = text.split('.');
    return `<span class="price"><i>¥</i>${esc(int)}${dec ? `<em>.${dec}</em>` : ''}</span>`;
  };

  const sales = n => n >= 10000 ? (n / 10000).toFixed(1).replace(/\.0$/, '') + '万+' : String(n);

  const timeText = ts => {
    const d = new Date(ts);
    const now = new Date();
    const pad = v => String(v).padStart(2, '0');
    const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`;
    const sameDay = d.toDateString() === now.toDateString();
    const yesterday = new Date(now.getTime() - 86400e3).toDateString() === d.toDateString();
    if (sameDay) return hm;
    if (yesterday) return `昨天 ${hm}`;
    if (d.getFullYear() === now.getFullYear()) return `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  };

  const fullTime = ts => {
    const d = new Date(ts);
    const pad = v => String(v).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  };

  /* ---------- 商品图形（离线可用：表情 + 渐变；店家上传图片后优先显示真实图） ----------
     兼容两种展示图形态：
     · art.img 存在  → 显示店家上传的商品图（img.art-img 由 CSS 铺满容器）；
     · 否则          → 回退「表情 + 渐变」离线展示图（演示数据）。 */
  const artStyle = art => {
    if (art && art.img) return 'background:#f2f3f5';
    const g = (art && art.g) || ['#ffe4d3', '#ffb88c'];
    return `background:linear-gradient(135deg,${g[0]},${g[1]})`;
  };
  const artHtml = (art, fontSize) => {
    if (art && art.img) return `<img class="art-img" src="${esc(art.img)}" alt="" loading="lazy" />`;
    return `<span style="font-size:${fontSize || 'inherit'}">${esc(art && art.e ? art.e : '🛍️')}</span>`;
  };

  /* ---------- Toast ---------- */
  function toast(message, type) {
    const node = document.createElement('div');
    node.className = 'toast' + (type ? ' ' + type : '');
    node.textContent = message;
    $('#toastRoot').append(node);
    setTimeout(() => node.remove(), 3000);
  }

  /* ---------- 弹窗 ---------- */
  function modal(html, opts) {
    const root = $('#modalRoot');
    const wrap = document.createElement('div');
    wrap.className = 'modal-backdrop';
    wrap.innerHTML = `<div class="modal ${(opts && opts.wide) ? 'wide' : ''}">${html}</div>`;
    root.append(wrap);
    const close = () => wrap.remove();
    /* 默认只有点击「退出类按钮」(data-close) 才会关闭弹窗，避免误点遮罩丢内容；
       需要「点击遮罩即关闭」的弹窗才显式传 closeOnBackdrop: true */
    if (opts && opts.closeOnBackdrop === true) {
      wrap.addEventListener('click', e => { if (e.target === wrap) close(); });
    }
    wrap.querySelectorAll('[data-close]').forEach(btn => btn.addEventListener('click', close));
    return { close, root: wrap.querySelector('.modal') };
  }

  function confirmDialog(title, text, okText, danger) {
    return new Promise(resolve => {
      const m = modal(`
        <h3>${esc(title)}</h3>
        <p class="modal-sub">${esc(text)}</p>
        <div class="modal-actions">
          <button class="btn btn-plain" data-close>取消</button>
          <button class="btn ${danger ? 'btn-danger' : 'btn-primary'}" id="confirmOk">${esc(okText || '确认')}</button>
        </div>`);
      m.root.querySelector('#confirmOk').onclick = () => { m.close(); resolve(true); };
      m.root.querySelector('[data-close]').onclick = () => { m.close(); resolve(false); };
    });
  }

  /* ---------- 头像（店铺 / 用户通用）：OSS 图片 URL → img，emoji / 空 → 字符 ---------- */
  const isImage = v => typeof v === 'string' && /^(https?:|blob:)/i.test(v.trim());
  const avatarHtml = (avatar, cls, color) => {
    const c = color || '#ff6a2b';
    if (isImage(avatar)) {
      return `<span class="shop-avatar ${cls || ''}" style="background:${c}"><img class="avatar-img" src="${esc(avatar.trim())}" alt="" /></span>`;
    }
    return `<span class="shop-avatar ${cls || ''}" style="background:${c}">${esc(String(avatar || '店'))}</span>`;
  };

  /* ---------- 商品卡 ---------- */
  /* 款式价区间：优先用服务端返回的 priceMin / priceMax（= 默认价与全部款式价的最小 / 最大值），
     老数据缺失时从 skus[].values[].price 兜底计算；不构成区间（max <= min）时返回 null */
  function skuRange(p) {
    if (!p) return null;
    const hasServer = p.priceMin !== undefined && p.priceMin !== null
      && p.priceMax !== undefined && p.priceMax !== null;
    let min = hasServer ? Number(p.priceMin) : Number(p.price);
    let max = hasServer ? Number(p.priceMax) : Number(p.price);
    if (!hasServer) {
      (p.skus || []).forEach(g => (g.values || []).forEach(v => {
        const sp = Number(v && v.price);
        if (!Number.isFinite(sp) || sp <= 0) return;
        if (!Number.isFinite(min) || sp < min) min = sp;
        if (!Number.isFinite(max) || sp > max) max = sp;
      }));
    }
    if (!Number.isFinite(min) || !Number.isFinite(max) || max <= min) return null;
    return { min, max };
  }
  /** 卡片主价：有款式价区间时展示最低价（起价），否则为商品默认价 */
  const cardPrice = p => { const r = skuRange(p); return r ? r.min : Number((p && p.price) || 0); };
  const hasSkuRange = p => !!skuRange(p);

  function productCard(p, extra, shopActions, opts) {
    /* 商品状态：已删除(deleted=1) 或 已下架(onSale=0) 时，图片灰化并叠加状态标签。
       仅收藏页 / 购物车等「历史快照」场景才会出现非在售商品；
       首页/搜索/推荐等在售列表接口不会下发这两个状态，因此不影响正常卡片外观。 */
    const isDeleted = p && p.deleted === 1;
    const isOffShelf = !isDeleted && p && p.onSale === 0;
    const hideQuickCart = !!(opts && opts.hideQuickCart); // 收藏页显式关掉「＋购物车」按钮
    const statusLabel = isDeleted ? '已删除' : (isOffShelf ? '已下架' : '');
    const cardCls = (isDeleted || isOffShelf) ? 'product-card is-inactive' : 'product-card';
    const statusBadge = statusLabel
      ? `<span class="pc-status-badge">${statusLabel}</span>`
      : '';
    return `
    <div class="${cardCls}" data-action="open-product" data-id="${esc(p.id)}">
      <div class="pc-art" style="${artStyle(p.art)}">
        ${p.tag ? `<span class="pc-tag">${esc(p.tag)}</span>` : ''}
        ${statusBadge}
        ${artHtml(p.art)}
      </div>
      <div class="pc-info">
        <h3 class="ellipsis-2">${esc(p.title)}</h3>
        <div class="pc-price-row">${price(cardPrice(p))}<small class="price-from">${hasSkuRange(p) ? '起' : ''}</small><del>${price(p.original)}</del></div>
        <div class="pc-meta"><span>${sales(p.sales)}人付款</span><span>好评 ${p.shop.score}</span></div>
        <div class="pc-shop"><b class="ellipsis">${esc(QM_STORE.displayShopName(p.shop.name))}</b><span style="display:inline-flex;align-items:center;gap:8px">${shopActions || ''}${(isDeleted || isOffShelf || hideQuickCart) ? '' : `<span class="btn btn-plain" data-action="quick-add-cart" data-id="${esc(p.id)}">＋购物车</span>`}</span></div>
      </div>
      ${extra || ''}
    </div>`;
  }

  /* ---------- 空状态 ---------- */
  const emptyState = (icon, title, desc, actionHtml) => `
    <div class="empty-state">
      <div class="empty-icon">${icon || '🛍️'}</div>
      <h3>${esc(title)}</h3>
      <p>${esc(desc)}</p>
      ${actionHtml || `<a class="btn btn-primary" href="#/home">去逛逛</a>`}
    </div>`;

  /* ---------- 登录 / 注册弹窗 ----------
     弹窗分「登录窗口」与「注册窗口」两个界面：
     · 登录窗口：账号登录 | 手机号验证码登录；底部按钮跳转注册窗口
     · 注册窗口：账号注册 | 手机号注册；底部按钮跳转登录窗口
     验证码由后端生成（6 位数字），通过 POST /sms-code 获取。
     交互统一走根节点事件委托，避免个别按钮绑定失效。 */
  function openLogin(dest) {
    const startWin = dest === 'register' ? 'register' : 'login';
    const user = QM_STORE.state.user;
    if (user) { toast('您已登录：' + user.nickname); return; }
    /* 若已有登录/注册弹窗则先关闭，避免叠加 */
    if (currentLoginModal) { try { currentLoginModal(); } catch (e) { /* 忽略 */ } currentLoginModal = null; }
    const m = modal(`
      <div style="position:relative">
        <!-- ===================== 登录窗口 ===================== -->
        <div id="loginWindow">
          <h3 id="loginTitle">账号登录</h3>
          <p class="modal-sub" id="loginSub">使用账号密码登录，登录后可同步购物车、订单与聊天记录</p>
          <div class="tabs" style="margin-bottom:16px">
            <button type="button" class="active" data-login-mode="acct">账号登录</button>
            <button type="button" data-login-mode="phone">手机登录</button>
          </div>
          <div id="acctLoginPane">
            <div class="form-row"><label>登录账号</label><input id="loginUserId" maxlength="15" placeholder="请输入账号" /></div>
            <div class="form-row"><label>密码</label><input id="loginPassword" type="password" maxlength="15" placeholder="请输入密码" /></div>
            <button type="button" class="btn btn-primary btn-lg" style="width:100%" id="loginSubmit">登 录</button>
          </div>
          <div id="phoneLoginPane" class="hidden">
            <div class="form-row"><label>手机号</label><input id="phlPhone" maxlength="11" inputmode="numeric" placeholder="11 位手机号" /></div>
            <div class="form-row"><label>短信验证码</label>
              <div class="input-flex">
                <input id="phlSms" maxlength="6" inputmode="numeric" placeholder="6 位数字验证码" />
                <button type="button" class="btn btn-plain" id="phlSend" data-send-code="login" style="flex:none;width:auto;padding:7px 12px;white-space:nowrap">获取验证码</button>
              </div>
              <div class="hint" id="phlHint">验证码由后端生成并发送（6 位数字，5 分钟内有效）</div>
            </div>
            <button type="button" class="btn btn-primary btn-lg" style="width:100%" id="phlSubmit">登 录</button>
          </div>
          <a class="btn btn-plain" data-win-switch="register" style="width:100%;margin-top:14px">还没有账号？立即注册</a>
        </div>

        <!-- ===================== 注册窗口 ===================== -->
        <div id="registerWindow" class="hidden">
          <h3 id="regTitle">账号注册</h3>
          <p class="modal-sub" id="regSub">注册后即可用账号密码登录聊天与购物</p>
          <div class="tabs" style="margin-bottom:16px">
            <button type="button" class="active" data-reg-mode="acct">账号注册</button>
            <button type="button" data-reg-mode="phone">手机注册</button>
          </div>
          <div id="regAcctPane">
            <div class="form-row"><label>账号</label><input id="regUserId" maxlength="15" placeholder="最长 15 位" /></div>
            <div class="form-row"><label>昵称</label><input id="regNickname" maxlength="30" placeholder="怎么称呼你" /></div>
            <div class="form-row"><label>密码</label><input id="regPassword" type="password" maxlength="15" placeholder="最长 15 位" /></div>
            <button type="button" class="btn btn-primary btn-lg" style="width:100%" id="regSubmit">注 册</button>
          </div>
          <div id="regPhonePane" class="hidden">
            <div class="form-row"><label>手机号</label><input id="phPhone" maxlength="11" inputmode="numeric" placeholder="11 位手机号，将作为登录账号" /></div>
            <div class="form-row"><label>短信验证码</label>
              <div class="input-flex">
                <input id="phSmsCode" maxlength="6" inputmode="numeric" placeholder="6 位数字验证码" />
                <button type="button" class="btn btn-plain" id="phSendCode" data-send-code="register" style="flex:none;width:auto;padding:7px 12px;white-space:nowrap">获取验证码</button>
              </div>
              <div class="hint" id="phSmsHint">验证码由后端生成并发送（6 位数字，5 分钟内有效）</div>
            </div>
            <div class="form-row"><label>密码</label><input id="phPassword" type="password" maxlength="15" placeholder="最长 15 位，用于账号密码登录" /></div>
            <div class="form-row"><label>昵称</label><input id="phNickname" maxlength="30" placeholder="怎么称呼你" /></div>
            <button type="button" class="btn btn-primary btn-lg" style="width:100%" id="phRegSubmit">注 册</button>
          </div>
          <a class="btn btn-plain" data-win-switch="login" style="width:100%;margin-top:14px">已有账号？立即登录</a>
        </div>
        <button class="btn btn-plain" data-close style="width:100%;margin-top:14px">取消</button>
      </div>`, { closeOnBackdrop: false });
    const root = m.root;

    /* ---------- 状态：每个场景（login/register）独立验证码倒计时 ---------- */
    const smsTimers = { login: null, register: null };

    /* ---------- 窗口切换 ---------- */
    function showLogin() {
      root.querySelector('#loginWindow').classList.remove('hidden');
      root.querySelector('#registerWindow').classList.add('hidden');
    }
    function showRegister() {
      root.querySelector('#registerWindow').classList.remove('hidden');
      root.querySelector('#loginWindow').classList.add('hidden');
    }

    /* ---------- 方式切换：登录 / 注册各自独立的一组子页签 ---------- */
    const LOGIN_MODES = {
      acct:  { title: '账号登录', sub: '使用账号密码登录，登录后可同步购物车、订单与聊天记录', pane: 'acctLoginPane' },
      phone: { title: '手机号登录', sub: '使用手机号和短信验证码快捷登录（无需密码），验证码 5 分钟内有效', pane: 'phoneLoginPane' }
    };
    const REG_MODES = {
      acct:  { title: '账号注册', sub: '注册后即可用账号密码登录聊天与购物', pane: 'regAcctPane' },
      phone: { title: '手机号注册', sub: '手机号即登录账号，验证码注册后即可用手机号 + 密码登录', pane: 'regPhonePane' }
    };
    function pickMode(sel, dsKey, meta, titleSel, subSel, mode) {
      const conf = meta[mode];
      if (!conf) { console.warn('[登录/注册弹窗] 未知的登录/注册方式：', mode); return; }
      root.querySelectorAll(sel).forEach(b => b.classList.toggle('active', b.dataset[dsKey] === mode));
      Object.keys(meta).forEach(k => root.querySelector('#' + meta[k].pane).classList.toggle('hidden', k !== mode));
      root.querySelector(titleSel).textContent = conf.title;
      root.querySelector(subSel).textContent = conf.sub;
    }
    function pickLogin(mode) { pickMode('[data-login-mode]', 'loginMode', LOGIN_MODES, '#loginTitle', '#loginSub', mode); }
    function pickRegister(mode) { pickMode('[data-reg-mode]', 'regMode', REG_MODES, '#regTitle', '#regSub', mode); }

    /* ---------- 登录成功公共处理：校验密令并把整份 data（用户信息 + 密令）写入登录态，不拆开 ---------- */
    async function commitLogin(data) {
      if (!data || !data.token) throw new Error('登录失败：后端未返回密令(token)，请检查后端登录接口');
      /* 对密令做本地校验：JWT 三段式结构 + exp 有效期（配置 JWT_SECRET 时额外做 HS256 签名校验） */
      const verdict = await QM_JWT.verify(data.token, QM_CFG.JWT_SECRET || '');
      if (!verdict.ok) throw new Error('令牌验证未通过：' + verdict.reason);
      const user = {
        id: data.id,
        userId: data.username || data.userId || '',
        nickname: data.name || data.username || '用户'
      };
      if (!user.userId) throw new Error('登录失败：响应缺少账号字段(username)，请检查后端登录接口');
      /* 登录响应若带回资料字段（后端接入后返回 avatar / gender / signature / shopId）则直接采用，
         让数据库里的头像地址 / 性别 / 签名在重新登录后依然生效 */
      if (data.avatar !== undefined) user.avatar = data.avatar;
      if (data.gender !== undefined) user.gender = data.gender;
      if (data.signature !== undefined) user.signature = data.signature;
      if (data.shopId !== undefined) user.shopId = data.shopId;
      /* 是否开店完全以服务端为准：数据库未绑定店铺（后端未返回 shopId）即为未开店，
         不再用演示账号映射兜底 —— 店家中心入口与商品/订单管理据此收敛 */
      /* 密令与用户信息一并保存进同一个登录态对象 */
      QM_STORE.user.set(user, data.token);
      m.close();
      toast(`欢迎回来，${user.nickname}` + (user.shopId ? '（已开店）' : ''), 'success');
      refreshView();
    }

    /* ---------- 提交动作（由根节点事件委托触发） ---------- */
    async function doAcctLogin() {
      const userId = root.querySelector('#loginUserId').value.trim();
      const password = root.querySelector('#loginPassword').value;
      if (!userId || !password) return toast('请输入账号和密码', 'error');
      const btn = root.querySelector('#loginSubmit');
      btn.disabled = true;
      btn.textContent = '校验中…';
      try {
        /* 登录一律走服务端：只有后端签发的 token 才能通过 TokenFilter，
           鉴权接口与 WebSocket 握手才可用（不再保留前端本地演示登录） */
        await commitLogin(await QM_API.auth.login({ userId, password }));
      } catch (e) {
        toast(e.message, 'error');
      } finally { btn.disabled = false; btn.textContent = '登 录'; }
    }

    async function doPhoneLogin() {
      const phone = root.querySelector('#phlPhone').value.trim();
      const smsCode = root.querySelector('#phlSms').value.trim();
      if (!/^1\d{10}$/.test(phone)) return toast('请输入正确的 11 位手机号', 'error');
      if (!/^\d{6}$/.test(smsCode)) return toast('请输入 6 位短信验证码', 'error');
      const btn = root.querySelector('#phlSubmit');
      btn.disabled = true;
      btn.textContent = '校验中…';
      try { await commitLogin(await QM_API.auth.loginByPhone(phone, smsCode)); }
      catch (e) { toast(e.message, 'error'); }
      finally { btn.disabled = false; btn.textContent = '登 录'; }
    }

    async function doAcctReg() {
      const userId = root.querySelector('#regUserId').value.trim();
      const nickname = root.querySelector('#regNickname').value.trim();
      const password = root.querySelector('#regPassword').value;
      if (!userId || !nickname || !password) return toast('请完整填写注册信息', 'error');
      try {
        await QM_API.auth.register({ userId, nickname, password });
        toast('注册成功，请使用新账号登录', 'success');
        root.querySelector('#loginUserId').value = userId;
        showLogin();
        pickLogin('acct');
      } catch (e) { toast(e.message, 'error'); }
    }

    async function doPhoneReg() {
      const phone = root.querySelector('#phPhone').value.trim();
      const smsCode = root.querySelector('#phSmsCode').value.trim();
      const password = root.querySelector('#phPassword').value;
      const nickname = root.querySelector('#phNickname').value.trim();
      if (!/^1\d{10}$/.test(phone)) return toast('请输入正确的 11 位手机号', 'error');
      if (!/^\d{6}$/.test(smsCode)) return toast('请输入 6 位短信验证码', 'error');
      if (!password) return toast('请设置登录密码', 'error');
      if (!nickname) return toast('请填写昵称', 'error');
      const btn = root.querySelector('#phRegSubmit');
      btn.disabled = true;
      btn.textContent = '提交中…';
      try {
        await QM_API.auth.register({ phone, smsCode, password, nickname });
        toast('注册成功，请使用手机号登录', 'success');
        resetSmsBtn('register');
        root.querySelector('#loginUserId').value = phone;
        showLogin();
        pickLogin('acct');
      } catch (e) { toast(e.message, 'error'); }
      finally { btn.disabled = false; btn.textContent = '注 册'; }
    }

    /* ---------- 短信验证码：由后端生成 6 位数字，POST /sms-code（scene 区分业务） ---------- */
    const SMS_FIELDS = {
      login:    { phone: '#phlPhone', code: '#phlSms', hint: '#phlHint', btn: '#phlSend', scene: 'login' },
      register: { phone: '#phPhone', code: '#phSmsCode', hint: '#phSmsHint', btn: '#phSendCode', scene: 'register' }
    };
    function stopSmsTimer(scene) {
      if (smsTimers[scene]) { clearInterval(smsTimers[scene]); smsTimers[scene] = null; }
    }
    function resetSmsBtn(scene) {
      stopSmsTimer(scene);
      const btn = root.querySelector(SMS_FIELDS[scene].btn);
      if (btn && btn.isConnected) { btn.disabled = false; btn.textContent = '获取验证码'; }
    }
    async function requestSms(scene) {
      const cfg = SMS_FIELDS[scene];
      const phoneEl = root.querySelector(cfg.phone);
      const codeEl = root.querySelector(cfg.code);
      const hintEl = root.querySelector(cfg.hint);
      const btn = root.querySelector(cfg.btn);
      const phone = phoneEl.value.trim();
      if (!/^1\d{10}$/.test(phone)) return toast('请输入正确的 11 位手机号', 'error');
      btn.disabled = true;
      btn.textContent = '发送中…';
      try {
        const data = await QM_API.auth.smsCode(phone, cfg.scene); // 后端生成 6 位数字验证码
        hintEl.textContent = `验证码已发送至 ${phone}（6 位数字），5 分钟内有效`;
        let left = 60;
        btn.textContent = `重新发送(${left}s)`;
        stopSmsTimer(scene);
        smsTimers[scene] = setInterval(() => {
          if (!btn.isConnected) { stopSmsTimer(scene); return; } // 弹窗已关闭
          if (--left <= 0) { resetSmsBtn(scene); }
          else btn.textContent = `重新发送(${left}s)`;
        }, 1000);
        /* 演示环境：后端把验证码放在 data.smsCode 返回，自动填入便于联调 */
        if (data && data.smsCode) {
          codeEl.value = String(data.smsCode);
          hintEl.textContent = `演示环境已自动填入验证码：${data.smsCode}（正式接入短信服务后不再返回）`;
        }
      } catch (e) {
        resetSmsBtn(scene);
        toast(e.message, 'error');
      }
    }

    /* ---------- 根节点事件委托：所有弹窗交互集中分发，任意动作独立、互不影响 ---------- */
    root.addEventListener('click', e => {
      try {
        const t = e.target;
        if (!t || !t.closest) return;
        if (t.closest('#loginSubmit')) return doAcctLogin();
        if (t.closest('#phlSubmit')) return doPhoneLogin();
        if (t.closest('#regSubmit')) return doAcctReg();
        if (t.closest('#phRegSubmit')) return doPhoneReg();
        const modeBtn = t.closest('[data-login-mode]');
        if (modeBtn) return pickLogin(modeBtn.dataset.loginMode);
        const regBtn = t.closest('[data-reg-mode]');
        if (regBtn) return pickRegister(regBtn.dataset.regMode);
        const sendBtn = t.closest('[data-send-code]');
        if (sendBtn) return requestSms(sendBtn.dataset.sendCode);
        const sw = t.closest('[data-win-switch]');
        if (sw) {
          if (sw.dataset.winSwitch === 'register') { showRegister(); return pickRegister('acct'); }
          showLogin();
          return pickLogin('acct');
        }
      } catch (err) {
        console.error('[登录/注册弹窗] 操作出错：', err);
        toast('操作出错：' + (err && err.message ? err.message : err), 'error');
      }
    });

    /* 记录弹窗关闭句柄（供下次打开前清理，防止叠加） */
    currentLoginModal = m.close;

    /* ---------- 初始窗口：dest=register 时直接展示注册窗口 ---------- */
    if (startWin === 'register') { showRegister(); pickRegister('acct'); }
  }

  /* ---------- 开店弹窗：填写店铺基本信息 → POST /shops（strict） ----------
     契约见 docs/店家中心商品管理接口文档.md 2.10：
       body { name, intro, avatar } → data 创建好的店铺档案（必须含 shopId）。
     成功后就地完成三件事：① 把 shopId 绑到当前账号（user.shopId）；
     ② 写入本地店铺档案（工作台 / 商品管理 / 店铺主页随即显示真实店铺）；③ 回调 onDone 让页面刷新。
     接口未实现（404 / 网络不可达）时只提示失败，**不伪造店铺、不写本地绑定**——
     与商品管理页一致的 strict 策略；店铺名查重与评分 / 粉丝初始值由后端负责。 */
  function openShopCreate(opts) {
    const done = (opts && opts.onDone) || null;
    const user = QM_STORE.state.user;
    if (!user) { toast('请先登录后再开店', 'error'); return; }
    let avatar = '';   // 已上传到 OSS 的店铺头像地址（提交时才保存）

    const m = modal(`
      <div class="shop-create">
        <!-- 顶部品牌横幅 -->
        <div class="sc-hero">
          <span class="sc-hero-badge">🛍️</span>
          <div>
            <h3>开通我的店铺</h3>
            <p>开店即可上架商品、管理订单，全程使用当前账号</p>
          </div>
        </div>
        <div class="sc-body">
          <!-- 店主身份 + 头像上传 -->
          <div class="sc-owner">
            <span class="sc-avatar">${avatarHtml('', 'xl', '#ff6a2b')}</span>
            <div class="sc-owner-info">
              <b>店主：${esc(user.nickname || user.userId || '')}</b>
              <p>绑定账号 @${esc(user.userId || '')} · 开店后仍可买家身份下单</p>
            </div>
            <button type="button" class="btn btn-plain btn-sm" id="scPick">📷 上传头像</button>
          </div>
          <div class="sc-field">
            <label class="sc-label" for="scName"><i class="sc-num">1</i>店铺名称<em>*</em></label>
            <input id="scName" class="se-name" maxlength="20" placeholder="2-20 个字，将展示在商品与店铺主页" />
          </div>
          <div class="sc-field">
            <label class="sc-label" for="scIntro"><i class="sc-num">2</i>店铺简介</label>
            <textarea id="scIntro" class="se-intro" rows="3" maxlength="120" placeholder="一句话介绍你的店铺，如主营类目 / 发货时效 / 售后承诺（选填）"></textarea>
            <p class="sc-count"><span id="scCount">0</span>/120</p>
          </div>
          <div class="modal-actions sc-actions">
            <button type="button" class="btn btn-plain" data-close>取消</button>
            <button type="button" class="btn btn-primary" id="scSubmit">立即开店</button>
          </div>
        </div>
      </div>`, { wide: true });

    const root = m.root;
    const nameEl = root.querySelector('#scName');
    const introEl = root.querySelector('#scIntro');
    const btn = root.querySelector('#scSubmit');
    introEl.oninput = () => { root.querySelector('#scCount').textContent = introEl.value.length; };

    /* 店铺头像上传（POST /shops/avatar，与商品图同一套 OSS 链路）：成功后即时预览 */
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/*';
    input.hidden = true;
    input.onchange = async () => {
      const file = input.files && input.files[0];
      input.value = '';
      if (!file) return;
      if (file.size > 10 * 1024 * 1024) return toast('图片不能超过 10MB', 'error');
      if (!/^image\//.test(file.type)) return toast('请选择 jpg / png 等图片文件', 'error');
      try {
        toast('正在上传店铺头像…');
        const data = await QM_API.seller.uploadShopAvatar(file);
        const url = data && data.url;
        if (!url) throw new Error('上传未返回图片地址');
        avatar = url;
        root.querySelector('.sc-avatar').innerHTML = avatarHtml(url, 'xl', '#ff6a2b');
        toast('头像已上传，点击「立即开店」后生效', 'success');
      } catch (e) {
        toast('头像上传失败：' + e.message, 'error');
      }
    };
    root.querySelector('#scPick').onclick = () => input.click();

    btn.onclick = async () => {
      const name = nameEl.value.trim();
      const intro = introEl.value.trim();
      if (!name) return toast('请填写店铺名称', 'error');
      if (name.length < 2 || name.length > 20) return toast('店铺名称需 2-20 个字', 'error');
      if (intro.length > 120) return toast('店铺简介不能超过 120 字', 'error');
      btn.disabled = true;
      try {
        const payload = { name, intro };
        if (avatar) payload.avatar = avatar;
        const shop = await QM_API.seller.createShop(payload);
        const shopId = shop && (shop.shopId || shop.shop_id || shop.id);
        if (!shopId) throw new Error('后端未返回店铺标识(shopId)');
        QM_STORE.user.update({ shopId });                                   // 账号绑定新店铺
        QM_STORE.rememberShop(Object.assign({}, shop || {}, { id: shopId, name, intro }));  // 后端字段优先
        if (avatar) QM_STORE.rememberShop({ id: shopId, avatar });          // 后端未回头像时用刚上传的
        toast('店铺已开通，去上架第一件商品吧 🎉', 'success');
        m.close();
        if (done) done(shopId); else refreshView();
      } catch (e) {
        toast('开店失败：' + e.message, 'error');
      } finally {
        btn.disabled = false;
      }
    };
  }

const QM_UI = {
  $, esc, price, sales, timeText, fullTime,
  artStyle, artHtml, isImage, avatarHtml, toast, modal, confirmDialog, productCard, emptyState, openLogin, openShopCreate,
  /** 打开注册窗口（与登录窗口同属一个弹窗，直接定位到注册界面） */
  openRegister() { return openLogin('register'); }
};

export default QM_UI;
