<script setup>
/* =========================================================
   青集市 · views/LoginView.vue —— 独立登录/注册页
   只包含登录与注册相关内容：
   · 登录窗口：账号登录 | 手机号验证码登录
   · 注册窗口：账号注册 | 手机号注册
   登录成功后按 ?redirect= 参数回跳目标页（默认回首页）。
   ========================================================= */
import { onBeforeUnmount, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import QM_CFG from '../core/config.js';
import QM_JWT from '../core/jwt.js';
import QM_API from '../core/api.js';
import QM_STORE from '../core/store.js';
import QM_UI from '../core/ui.js';
import { refreshView } from '../core/viewRefresh.js';

const { toast } = QM_UI;
const route = useRoute();
const router = useRouter();

/* 登录成功后的回跳地址（仅接受站内路径，防止开放重定向） */
const rawRedirect = route.query.redirect;
const redirect = typeof rawRedirect === 'string' && rawRedirect.startsWith('/') ? rawRedirect : '/home';

/* 窗口：login / register（/login?mode=register 直达注册窗口） */
const win = ref(route.query.mode === 'register' ? 'register' : 'login');
const loginMode = ref('acct');   // acct 账号登录 / phone 手机登录
const regMode = ref('acct');     // acct 账号注册 / phone 手机注册
const busy = ref(false);

/* ---------- 登录表单 ---------- */
const loginUserId = ref('');
const loginPassword = ref('');
const phlPhone = ref('');
const phlSms = ref('');

/* ---------- 注册表单 ---------- */
const regUserId = ref('');
const regNickname = ref('');
const regPassword = ref('');
const phPhone = ref('');
const phSmsCode = ref('');
const phPassword = ref('');
const phNickname = ref('');

/* ---------- 短信验证码：scene 区分 login / register，各自独立倒计时 ---------- */
const smsTimers = { login: null, register: null };
const smsText = ref({ login: '获取验证码', register: '获取验证码' });
const smsDisabled = ref({ login: false, register: false });
const smsHint = ref({ login: '', register: '' });

function switchWin(next) { win.value = next; }
function pickLogin(m) { loginMode.value = m; }
function pickRegister(m) { regMode.value = m; }

/* ---------- 登录成功公共处理：校验密令并把整份 data（用户信息 + 密令）写入登录态 ---------- */
async function commitLogin(data) {
  if (!data || !data.token) throw new Error('登录失败：后端未返回密令(token)，请检查后端登录接口');
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
  QM_STORE.user.set(user, data.token);
  toast(`欢迎回来，${user.nickname}` + (user.shopId ? '（已开店）' : ''), 'success');
  refreshView();
  router.replace(redirect);
}

/* ---------- 登录动作 ---------- */
async function doAcctLogin() {
  const userId = loginUserId.value.trim();
  const password = loginPassword.value;
  if (!userId || !password) return toast('请输入账号和密码', 'error');
  busy.value = true;
  try {
    /* 登录一律走服务端：只有后端签发的 token 才能通过 TokenFilter，
       /users/online、/messages/* 与 WebSocket 握手才可用。
       不再保留前端本地演示登录 —— 它的 token 为空，会让鉴权请求全部 401 */
    await commitLogin(await QM_API.auth.login({ userId, password }));
  } catch (e) {
    toast(e.message, 'error');
  }
  finally { busy.value = false; }
}

async function doPhoneLogin() {
  const phone = phlPhone.value.trim();
  const smsCode = phlSms.value.trim();
  if (!/^1\d{10}$/.test(phone)) return toast('请输入正确的 11 位手机号', 'error');
  if (!/^\d{6}$/.test(smsCode)) return toast('请输入 6 位短信验证码', 'error');
  busy.value = true;
  try { await commitLogin(await QM_API.auth.loginByPhone(phone, smsCode)); }
  catch (e) { toast(e.message, 'error'); }
  finally { busy.value = false; }
}

/* ---------- 注册动作 ---------- */
async function doAcctReg() {
  const userId = regUserId.value.trim();
  const nickname = regNickname.value.trim();
  const password = regPassword.value;
  if (!userId || !nickname || !password) return toast('请完整填写注册信息', 'error');
  busy.value = true;
  try {
    await QM_API.auth.register({ userId, nickname, password });
    toast('注册成功，请使用新账号登录', 'success');
    loginUserId.value = userId;
    switchWin('login');
    pickLogin('acct');
  } catch (e) { toast(e.message, 'error'); }
  finally { busy.value = false; }
}

async function doPhoneReg() {
  const phone = phPhone.value.trim();
  const smsCode = phSmsCode.value.trim();
  const password = phPassword.value;
  const nickname = phNickname.value.trim();
  if (!/^1\d{10}$/.test(phone)) return toast('请输入正确的 11 位手机号', 'error');
  if (!/^\d{6}$/.test(smsCode)) return toast('请输入 6 位短信验证码', 'error');
  if (!password) return toast('请设置登录密码', 'error');
  if (!nickname) return toast('请填写昵称', 'error');
  busy.value = true;
  try {
    await QM_API.auth.register({ phone, smsCode, password, nickname });
    toast('注册成功，请使用手机号登录', 'success');
    resetSmsBtn('register');
    phlPhone.value = phone;
    switchWin('login');
    pickLogin('acct');
  } catch (e) { toast(e.message, 'error'); }
  finally { busy.value = false; }
}

/* ---------- 短信验证码（后端生成 6 位数字，POST /sms-code，scene 区分业务） ---------- */
function stopSmsTimer(scene) {
  if (smsTimers[scene]) { clearInterval(smsTimers[scene]); smsTimers[scene] = null; }
}
function resetSmsBtn(scene) {
  stopSmsTimer(scene);
  smsDisabled.value[scene] = false;
  smsText.value[scene] = '获取验证码';
}
async function requestSms(scene) {
  const isLogin = scene === 'login';
  const phone = (isLogin ? phlPhone.value : phPhone.value).trim();
  if (!/^1\d{10}$/.test(phone)) return toast('请输入正确的 11 位手机号', 'error');
  smsDisabled.value[scene] = true;
  smsText.value[scene] = '发送中…';
  try {
    const data = await QM_API.auth.smsCode(phone, scene);
    smsHint.value[scene] = `验证码已发送至 ${phone}（6 位数字），5 分钟内有效`;
    let left = 60;
    smsText.value[scene] = `重新发送(${left}s)`;
    stopSmsTimer(scene);
    smsTimers[scene] = setInterval(() => {
      if (--left <= 0) resetSmsBtn(scene);
      else smsText.value[scene] = `重新发送(${left}s)`;
    }, 1000);
    /* 演示环境：后端把验证码放在 data.smsCode 返回，自动填入便于联调 */
    if (data && data.smsCode) {
      if (isLogin) phlSms.value = String(data.smsCode);
      else phSmsCode.value = String(data.smsCode);
      smsHint.value[scene] = `演示环境已自动填入验证码：${data.smsCode}（正式接入短信服务后不再返回）`;
    }
  } catch (e) {
    resetSmsBtn(scene);
    toast(e.message, 'error');
  }
}

onBeforeUnmount(() => { stopSmsTimer('login'); stopSmsTimer('register'); });
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <!-- 品牌区 -->
      <div class="login-brand">
        <span class="logo-mark">青</span>
        <h1>青集市</h1>
        <p>登录后继续你的发现之旅</p>
      </div>

      <!-- 登录 / 注册 窗口切换 -->
      <div class="login-switch">
        <button :class="{ active: win === 'login' }" @click="switchWin('login')">登 录</button>
        <button :class="{ active: win === 'register' }" @click="switchWin('register')">注 册</button>
      </div>

      <!-- ===================== 登录窗口 ===================== -->
      <template v-if="win === 'login'">
        <div class="tabs login-tabs">
          <button :class="{ active: loginMode === 'acct' }" @click="pickLogin('acct')">账号登录</button>
          <button :class="{ active: loginMode === 'phone' }" @click="pickLogin('phone')">手机登录</button>
        </div>

        <div v-if="loginMode === 'acct'">
          <div class="form-row">
            <label>登录账号</label>
            <input v-model="loginUserId" maxlength="15" placeholder="请输入账号" @keyup.enter="doAcctLogin" />
          </div>
          <div class="form-row">
            <label>密码</label>
            <input v-model="loginPassword" type="password" maxlength="15" placeholder="请输入密码" @keyup.enter="doAcctLogin" />
          </div>
          <button class="btn btn-primary btn-lg login-submit" :disabled="busy" @click="doAcctLogin">登 录</button>
        </div>

        <div v-else>
          <div class="form-row">
            <label>手机号</label>
            <input v-model="phlPhone" maxlength="11" inputmode="numeric" placeholder="11 位手机号" />
          </div>
          <div class="form-row">
            <label>短信验证码</label>
            <div class="input-flex">
              <input v-model="phlSms" maxlength="6" inputmode="numeric" placeholder="6 位数字验证码" @keyup.enter="doPhoneLogin" />
              <button type="button" class="btn btn-plain sms-btn" :disabled="smsDisabled.login" @click="requestSms('login')">{{ smsText.login }}</button>
            </div>
            <div class="hint">{{ smsHint.login }}</div>
          </div>
          <button class="btn btn-primary btn-lg login-submit" :disabled="busy" @click="doPhoneLogin">登 录</button>
        </div>

        <button type="button" class="switch-link" @click="switchWin('register')">还没有账号？立即注册</button>
      </template>

      <!-- ===================== 注册窗口 ===================== -->
      <template v-else>
        <div class="tabs login-tabs">
          <button :class="{ active: regMode === 'acct' }" @click="pickRegister('acct')">账号注册</button>
          <button :class="{ active: regMode === 'phone' }" @click="pickRegister('phone')">手机注册</button>
        </div>

        <div v-if="regMode === 'acct'">
          <div class="form-row">
            <label>账号</label>
            <input v-model="regUserId" maxlength="15" placeholder="最长 15 位" />
          </div>
          <div class="form-row">
            <label>昵称</label>
            <input v-model="regNickname" maxlength="30" placeholder="怎么称呼你" />
          </div>
          <div class="form-row">
            <label>密码</label>
            <input v-model="regPassword" type="password" maxlength="15" placeholder="最长 15 位" />
          </div>
          <button class="btn btn-primary btn-lg login-submit" :disabled="busy" @click="doAcctReg">注 册</button>
        </div>

        <div v-else>
          <div class="form-row">
            <label>手机号</label>
            <input v-model="phPhone" maxlength="11" inputmode="numeric" placeholder="11 位手机号，将作为登录账号" />
          </div>
          <div class="form-row">
            <label>短信验证码</label>
            <div class="input-flex">
              <input v-model="phSmsCode" maxlength="6" inputmode="numeric" placeholder="6 位数字验证码" />
              <button type="button" class="btn btn-plain sms-btn" :disabled="smsDisabled.register" @click="requestSms('register')">{{ smsText.register }}</button>
            </div>
            <div class="hint">{{ smsHint.register }}</div>
          </div>
          <div class="form-row">
            <label>密码</label>
            <input v-model="phPassword" type="password" maxlength="15" placeholder="最长 15 位，用于账号密码登录" />
          </div>
          <div class="form-row">
            <label>昵称</label>
            <input v-model="phNickname" maxlength="30" placeholder="怎么称呼你" />
          </div>
          <button class="btn btn-primary btn-lg login-submit" :disabled="busy" @click="doPhoneReg">注 册</button>
        </div>

        <button type="button" class="switch-link" @click="switchWin('login')">已有账号？立即登录</button>
      </template>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  padding: 48px 16px 64px;
}
.login-card {
  width: 432px;
  max-width: 100%;
  background: #fff;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  border: 1px solid var(--border);
  padding: 38px 38px 32px;
}
.login-brand {
  text-align: center;
  margin-bottom: 24px;
}
.login-brand .logo-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 54px;
  height: 54px;
  border-radius: 15px;
  background: var(--brand-grad);
  color: #fff;
  font-size: 27px;
  font-weight: 700;
  margin-bottom: 12px;
  box-shadow: var(--shadow-brand);
}
.login-brand h1 { font-size: 22px; margin-bottom: 4px; letter-spacing: -.2px; }
.login-brand p { font-size: 13px; color: var(--text-3); }
.login-switch {
  display: flex;
  margin-bottom: 20px;
  border-bottom: 2px solid var(--border);
}
.login-switch button {
  flex: 1;
  padding: 11px 0;
  font-size: 15px;
  color: var(--text-2);
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
}
.login-switch button:hover { color: var(--brand-ink); }
.login-switch button.active {
  color: var(--brand-ink);
  border-bottom-color: var(--brand);
  font-weight: 600;
}
.login-tabs { margin-bottom: 20px; }
.login-submit { width: 100%; margin-top: 6px; }
.sms-btn { flex: none; width: auto; height: 40px; padding: 0 14px; white-space: nowrap; font-size: 13px; }
.switch-link {
  display: block;
  width: 100%;
  text-align: center;
  margin-top: 18px;
  padding: 6px 0;
  font-size: 13px;
  color: var(--brand-ink);
  cursor: pointer;
  font-weight: 500;
  border-radius: var(--radius-sm);
}
.switch-link:hover { text-decoration: underline; }
.demo-tip {
  margin-top: 18px;
  padding: 13px 15px;
  background: #fff8ef;
  border: 1px solid #ffe3c8;
  border-radius: var(--radius);
  font-size: 12px;
  color: var(--text-2);
  line-height: 1.85;
  max-height: 168px;
  overflow-y: auto;
}
.demo-tip b { color: var(--brand-ink); display: block; margin-bottom: 2px; }
.demo-tip span { display: block; }
.demo-tip p { margin-top: 8px; color: var(--text-3); }
</style>
