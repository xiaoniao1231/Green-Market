import QM_UI from './ui.js';
import QM_STORE from './store.js';
import QM_API from './api.js';

const { esc, toast, modal } = QM_UI;

export function maskPhone(phone) {
  const p = String(phone || '').trim();
  if (!/^\d{11}$/.test(p)) return p;
  return p.slice(0, 3) + '****' + p.slice(7);
}

function createSmsHelper(root, { phoneSel, codeSel, btnSel, hintSel, scene }) {
  let timer = null;
  const btn = root.querySelector(btnSel);
  const hint = root.querySelector(hintSel);
  const stop = () => { if (timer) { clearInterval(timer); timer = null; } };
  const reset = () => { stop(); if (btn.isConnected) { btn.disabled = false; btn.textContent = '获取验证码'; } };
  const send = async () => {
    const phone = root.querySelector(phoneSel).value.trim();
    if (!/^1\d{10}$/.test(phone)) return toast('请输入正确的 11 位手机号', 'error');
    btn.disabled = true;
    btn.textContent = '发送中…';
    try {
      const data = await QM_API.auth.smsCode(phone, scene);
      hint.textContent = `验证码已发送至 ${maskPhone(phone)}（6 位数字），5 分钟内有效`;
      let left = 60;
      btn.textContent = `重新发送(${left}s)`;
      stop();
      timer = setInterval(() => {
        if (!btn.isConnected) { stop(); return; }
        if (--left <= 0) reset();
        else btn.textContent = `重新发送(${left}s)`;
      }, 1000);
      if (data && data.smsCode) {
        root.querySelector(codeSel).value = String(data.smsCode);
        hint.textContent = `验证码已自动填入：${data.smsCode}`;
      }
    } catch (e) {
      reset();
      toast(e.message || '验证码发送失败', 'error');
    }
  };
  btn.onclick = send;
  return { stop, reset };
}

export function openPhoneModal({ current, onSaved } = {}) {
  const me = QM_STORE.state.user;
  if (!me) return toast('请先登录', 'error');
  const profile = current || {};
  const currentPhone = String(profile.phoneNumber || me.phoneNumber || '').trim();
  const bound = !!currentPhone;

  const m = modal(`
    <div>
      <h3>${bound ? '修改手机号' : '绑定手机号'}</h3>
      <p class="modal-sub">一个手机号只能绑定一个账号</p>
      ${bound ? `
      <div class="as-info" style="padding-top:0">
        <div><span>当前手机号</span><b>${esc(maskPhone(currentPhone))}</b></div>
      </div>` : ''}
      <div class="form-row">
        <label>新手机号</label>
        <input id="apPhone" maxlength="11" inputmode="numeric" placeholder="11 位手机号" />
      </div>
      <div class="form-row">
        <label>短信验证码</label>
        <div class="input-flex">
          <input id="apSms" maxlength="6" inputmode="numeric" placeholder="6 位数字验证码" />
          <button type="button" class="btn btn-plain sms-btn" id="apSend">获取验证码</button>
        </div>
        <p class="hint" id="apHint">验证码发送到「新手机号」，5 分钟内有效</p>
      </div>
      ${bound ? `
      <div class="form-row">
        <label>当前登录密码</label>
        <input id="apPwd" type="password" maxlength="15" placeholder="请输入当前密码" />
        <p class="hint">修改手机号需验证当前登录密码</p>
      </div>` : ''}
      <div class="modal-actions" style="margin-top:0">
        <button class="btn btn-plain" data-close id="apCancel">取消</button>
        <button class="btn btn-primary" id="apSave">${bound ? '确认修改' : '确认绑定'}</button>
      </div>
    </div>`);

  const sms = createSmsHelper(m.root, {
    phoneSel: '#apPhone', codeSel: '#apSms', btnSel: '#apSend', hintSel: '#apHint', scene: 'bind'
  });
  m.root.querySelector('#apCancel').onclick = () => sms.stop();

  m.root.querySelector('#apSave').onclick = async () => {
    const phone = m.root.querySelector('#apPhone').value.trim();
    const smsCode = m.root.querySelector('#apSms').value.trim();
    const pwdEl = m.root.querySelector('#apPwd');
    const password = pwdEl ? pwdEl.value : '';
    if (!/^1[3-9]\d{9}$/.test(phone)) return toast('请输入正确的 11 位手机号', 'error');
    if (!/^\d{6}$/.test(smsCode)) return toast('请输入 6 位短信验证码', 'error');
    if (bound && phone === currentPhone) return toast('新手机号与当前绑定手机号相同', 'error');
    if (bound && !password) return toast('请输入当前登录密码', 'error');
    const btn = m.root.querySelector('#apSave');
    btn.disabled = true; btn.textContent = '提交中…';
    try {
      const data = await QM_API.user.bindPhone({ phone, smsCode, password });
      sms.reset();
      sms.stop();
      toast(bound ? '手机号已修改' : '手机号绑定成功', 'success');
      m.close();
      try { onSaved && onSaved(data); } catch (e) { console.error(e); }
    } catch (e) {
      toast(e.message || (bound ? '修改失败，请稍后重试' : '绑定失败，请稍后重试'), 'error');
    } finally {
      btn.disabled = false; btn.textContent = bound ? '确认修改' : '确认绑定';
    }
  };

  return m;
}

export function openPasswordModal({ onSaved } = {}) {
  const me = QM_STORE.state.user;
  if (!me) return toast('请先登录', 'error');

  const m = modal(`
    <div>
      <h3>修改密码</h3>
      <p class="modal-sub">修改后其它设备需重新登录</p>
      <div class="form-row">
        <label>当前密码</label>
        <input id="pwOld" type="password" maxlength="15" placeholder="请输入当前密码" autocomplete="current-password" />
      </div>
      <div class="form-row">
        <label>新密码</label>
        <input id="pwNew" type="password" maxlength="15" placeholder="6-15 位" autocomplete="new-password" />
      </div>
      <div class="form-row">
        <label>确认新密码</label>
        <input id="pwNew2" type="password" maxlength="15" placeholder="再输入一次" autocomplete="new-password" />
      </div>
      <div class="modal-actions" style="margin-top:0">
        <button class="btn btn-plain" data-close>取消</button>
        <button class="btn btn-primary" id="pwSave">确认修改</button>
      </div>
    </div>`);

  m.root.querySelector('#pwSave').onclick = async () => {
    const oldPassword = m.root.querySelector('#pwOld').value;
    const newPassword = m.root.querySelector('#pwNew').value;
    const confirm = m.root.querySelector('#pwNew2').value;
    if (!oldPassword) return toast('请输入当前密码', 'error');
    if (!newPassword || newPassword.length < 6 || newPassword.length > 15) return toast('新密码长度需为 6-15 位', 'error');
    if (newPassword === oldPassword) return toast('新密码不能与当前密码相同', 'error');
    if (newPassword !== confirm) return toast('两次输入的新密码不一致', 'error');
    const btn = m.root.querySelector('#pwSave');
    btn.disabled = true; btn.textContent = '提交中…';
    try {
      const res = await QM_API.user.changePassword({ oldPassword, newPassword });
      m.close();
      if (res.token) {
        toast('密码已更新，本机登录态已自动续期；其它设备需重新登录', 'success');
      } else {
        toast('密码已更新，请使用新密码重新登录', 'success');
      }
      try { onSaved && onSaved({ token: res.token, reloginRequired: !res.token }); } catch (e) { console.error(e); }
    } catch (e) {
      toast(e.message || '修改失败，请稍后重试', 'error');
      btn.disabled = false; btn.textContent = '确认修改';
    }
  };

  return m;
}
