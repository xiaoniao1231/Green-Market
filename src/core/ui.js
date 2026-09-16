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

  /* ---------- 商品图形（离线可用：表情 + 渐变） ---------- */
  const artStyle = art => {
    const g = (art && art.g) || ['#ffe4d3', '#ffb88c'];
    return `background:linear-gradient(135deg,${g[0]},${g[1]})`;
  };
  const artHtml = (art, fontSize) => `<span style="font-size:${fontSize || 'inherit'}">${esc(art && art.e ? art.e : '🛍️')}</span>`;

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
    /* closeOnBackdrop 默认为 true；传入 false 时仅允许通过 × 按钮(data-close)关闭 */
    if (!opts || opts.closeOnBackdrop !== false) {
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

  /* ---------- 商品卡 ---------- */
  function productCard(p, extra) {
    return `
    <div class="product-card" data-action="open-product" data-id="${esc(p.id)}">
      <div class="pc-art" style="${artStyle(p.art)}">
        ${p.tag ? `<span class="pc-tag">${esc(p.tag)}</span>` : ''}
        ${artHtml(p.art)}
      </div>
      <div class="pc-info">
        <h3 class="ellipsis-2">${esc(p.title)}</h3>
        <div class="pc-price-row">${price(p.price)}<del>${price(p.original)}</del></div>
        <div class="pc-meta"><span>${sales(p.sales)}人付款</span><span>好评 ${p.shop.score}</span></div>
        <div class="pc-shop"><b class="ellipsis">${esc(p.shop.name)}</b><span class="btn btn-plain" data-action="quick-add-cart" data-id="${esc(p.id)}">＋购物车</span></div>
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
        <button class="modal-close" data-close>×</button>

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
      /* 账号若已开店则附带店铺绑定 shopId（演示映射见 mock.shopOwners，后端接入后由服务端返回） */
      const owner = QM_MOCK.shopOf(user.userId);
      if (owner) {
        const svc = QM_MOCK.serviceById(owner.shopId);
        user.shopId = owner.shopId;
        user.avatar = (svc && svc.avatar) || '';
        user.avatarColor = (svc && svc.color) || '#ff6a2b';
      }
      /* 密令与用户信息一并保存进同一个登录态对象 */
      QM_STORE.user.set(user, data.token);
      m.close();
      toast(`欢迎回来，${user.nickname}` + (owner ? '（已开店）' : ''), 'success');
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

const QM_UI = {
  $, esc, price, sales, timeText, fullTime,
  artStyle, artHtml, toast, modal, confirmDialog, productCard, emptyState, openLogin,
  /** 打开注册窗口（与登录窗口同属一个弹窗，直接定位到注册界面） */
  openRegister() { return openLogin('register'); }
};

export default QM_UI;
