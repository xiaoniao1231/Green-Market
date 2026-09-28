<script setup>
/* =========================================================
   青集市 · views/AccountView.vue —— 账户设置页（#/account）
   ---------------------------------------------------------
   账户设置 = 资料 / 手机号 / 密码 / 收货地址，四块能力集中在同一页
   （契约见 docs/账户设置接口文档.md）：
   · 基本资料：**页面内嵌表单**直接改昵称 / 头像 / 性别 / 签名
     （原「编辑资料」弹窗已整合进本页，个人中心不再单独提供编辑入口）；
   · 手机号：绑定 / 换绑（`scene=bind` 验证码；换绑还需当前登录密码）；
   · 登录密码：修改密码（改密后令牌由后端重签发，本机无需重新登录）；
   · 收货地址：复用 core/addressModal.js 的地址簿弹窗。
   数据源：GET /users/me（strict）。接口不可用时只展示本机登录态里已有的信息，
   并把失败原因如实显示在顶部提示条 —— 不伪造手机号、不假装保存成功。
   ========================================================= */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import QM_UI from '../core/ui.js';
import QM_STORE from '../core/store.js';
import QM_API from '../core/api.js';
import openAddressModal from '../core/addressModal.js';
import { openPhoneModal, openPasswordModal, maskPhone } from '../core/accountModals.js';

const { toast, confirmDialog } = QM_UI;
const router = useRouter();

/** 头像图片大小上限 5MB：前端提前拦截（后端上限 10MB） */
const AVATAR_MAX_SIZE = 5 * 1024 * 1024;
const isAvatarImage = v => typeof v === 'string' && /^(https?:|blob:)/i.test(v.trim());

/* ---------- 账号资料：GET /users/me ---------- */
const me = ref(null);                 // 服务端返回的完整账号资料
const phase = ref('loading');         // loading | ready | error
const errorMsg = ref('');
const user = computed(() => QM_STORE.state.user);
const addrCount = computed(() => QM_STORE.addr.list().length);

/**
 * 拉取账号资料。
 * @param {{ silent?: boolean }} [opts] silent=true 时不清空页面（保存资料后静默刷新用），
 *        避免整页闪回「正在加载」骨架。
 */
async function load({ silent = false } = {}) {
  if (!silent) phase.value = 'loading';
  errorMsg.value = '';
  try {
    me.value = await QM_API.user.me();
    phase.value = 'ready';
  } catch (e) {
    me.value = null;
    errorMsg.value = (e && e.message) || '账号资料加载失败';
    phase.value = 'error';
  }
  /* 服务端资料（或失败时的本机兜底）就绪后，把内嵌表单重置为这份真值 */
  resetForm();
}
onMounted(() => { load(); });

/* ---------- 展示口径：服务端资料优先，接口不可用时回退本机登录态 ---------- */
const GENDER_TEXT = { male: '男 ♂', female: '女 ♀', secret: '保密' };
const account = computed(() => {
  const m = me.value || {};
  const u = user.value || {};
  const genderRaw = ['male', 'female', 'secret'].includes(m.gender) ? m.gender
    : (['male', 'female', 'secret'].includes(u.gender) ? u.gender : 'secret');
  return {
    userId: m.userId || u.userId || '',
    nickname: m.nickname || u.nickname || '',
    /* 头像可能是 OSS 地址，也可能是历史 emoji 字符 */
    avatar: (m.avatar !== undefined && m.avatar !== null && m.avatar !== '') ? m.avatar : (u.avatar || ''),
    gender: GENDER_TEXT[genderRaw],
    genderRaw,
    signature: (m.signature !== undefined && m.signature !== null) ? m.signature : (u.signature || ''),
    phoneNumber: m.phoneNumber || u.phoneNumber || '',
    phoneBoundAt: m.phoneBoundAt || '',
    createdAt: m.createdAt || '',
    lastPwdChangeAt: m.lastPwdChangeAt || '',
    shopId: m.shopId || u.shopId || ''
  };
});
const dateOnly = v => { const s = String(v || '').trim(); return s ? s.slice(0, 10) : ''; };
const phoneText = computed(() => {
  if (account.value.phoneNumber) return maskPhone(account.value.phoneNumber);
  return phase.value === 'error' ? '—（资料接口不可用）' : '未绑定';
});

/* =========================================================
   基本资料：页面内嵌表单（原「编辑资料」弹窗已整合到这里，不再以弹窗形式出现）
   ---------------------------------------------------------
   · 头像：选图只做本地 blob 预览，「保存资料」时才上传 OSS（避免取消时产生垃圾图）；
   · 保存成功后静默重拉 GET /users/me，以服务端真值回填表单；
   · 账号名 / 手机号 / 密码不在这张表单里（各有专用入口，防越权改写）。
   ========================================================= */
const form = ref({ nickname: '', gender: 'secret', signature: '' });
const formAvatar = ref('');            // 表单当前展示的头像（OSS 地址 / 历史 emoji / blob: 预览）
const pickedAvatar = ref(null);        // 本次新选的图片文件（保存时才上传）
const savingProfile = ref(false);
const fileInput = ref(null);
let objectUrl = null;                  // 本地预览 URL（保存 / 撤销 / 卸载时释放）

/** 把表单重置为服务端（或本机兜底）真值 */
function resetForm() {
  const a = account.value;
  form.value = { nickname: a.nickname || '', gender: a.genderRaw, signature: a.signature || '' };
  pickedAvatar.value = null;
  releasePreview();
  formAvatar.value = a.avatar || '';
}
function releasePreview() {
  if (objectUrl) { URL.revokeObjectURL(objectUrl); objectUrl = null; }
}
function pickAvatar() {
  if (fileInput.value) fileInput.value.click();
}
function onAvatarChange(e) {
  const f = e.target.files && e.target.files[0];
  if (!f) return;
  if (!/^image\//.test(f.type)) return toast('请选择图片文件', 'error');
  if (f.size > AVATAR_MAX_SIZE) return toast('头像图片不能超过 5MB', 'error');
  releasePreview();                    // 换图时先释放上一张预览
  pickedAvatar.value = f;
  objectUrl = URL.createObjectURL(f);
  formAvatar.value = objectUrl;
}
async function saveProfile() {
  const nickname = (form.value.nickname || '').trim();
  if (!nickname) return toast('昵称不能为空', 'error');
  if (nickname.length > 20) return toast('昵称最长 20 字', 'error');
  if ((form.value.signature || '').length > 40) return toast('个性签名最长 40 字', 'error');
  savingProfile.value = true;
  try {
    /* ① 选过新图 → 先上传阿里云 OSS 拿地址（multipart → POST /users/avatar） */
    let avatar = formAvatar.value;
    if (pickedAvatar.value) {
      const data = await QM_API.user.uploadAvatar(pickedAvatar.value);
      const url = data && (data.url || data.avatar || data.fileUrl);
      if (!url) throw new Error('头像上传成功但未返回图片地址');
      avatar = url;
    }
    /* ② 资料落库（PUT /users/profile），成功后同步本地登录态并静默重拉真值 */
    const payload = {
      nickname,
      gender: form.value.gender || 'secret',
      avatar,
      signature: (form.value.signature || '').trim()
    };
    await QM_API.user.updateProfile(payload);
    QM_STORE.user.update(payload);
    releasePreview();
    pickedAvatar.value = null;
    toast('资料已更新', 'success');
    await load({ silent: true });
  } catch (e) {
    /* 失败保留表单与本地预览，便于用户重试 */
    toast(e.message || '保存失败', 'error');
  } finally {
    savingProfile.value = false;
  }
}
onBeforeUnmount(releasePreview);

/* ---------- 手机号 / 密码 / 地址：走 core 层共用弹窗，成功后重新拉账号资料 ---------- */
function phoneModal() {
  openPhoneModal({ current: me.value || {}, onSaved: () => load({ silent: true }) });
}
function passwordModal() {
  openPasswordModal({
    onSaved: ({ reloginRequired }) => {
      /* 后端未随响应重发令牌（兼容路径）：本地令牌已因 pwd_version 变化失效，必须重新登录 */
      if (reloginRequired) {
        QM_STORE.user.logout();
        router.push({ path: '/login', query: { redirect: '/account' } });
        return;
      }
      load({ silent: true });
    }
  });
}
async function addressModal() {
  await openAddressModal({ onSaved: () => { /* 地址计数走 store 事件自动重算 */ } });
}
async function logout() {
  if (await confirmDialog('退出登录', '确定退出当前账号吗？', '退出', true)) {
    QM_API.auth.logout();
    QM_STORE.user.logout();
    toast('已退出登录');
    router.push('/home');
  }
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 账户设置</div>
        <h1>账户设置</h1>
      </div>
      <a class="btn btn-plain" href="#/profile">← 返回个人中心</a>
    </div>

    <!-- 加载中 -->
    <div v-if="phase === 'loading'" class="order-card">
      <div class="empty-state">
        <div class="empty-icon">⏳</div>
        <h3>正在加载账号资料…</h3>
      </div>
    </div>

    <template v-else>
      <!-- 接口失败：如实提示（后端未实现 / 未启动），下方仍展示本机登录态里的信息 -->
      <div v-if="phase === 'error'" class="acct-alert">
        <b>账号资料接口暂不可用</b>
        <p>{{ errorMsg }}。下方昵称 / 头像来自本机登录态；保存资料、修改手机号、修改密码等写操作需要后端接口可用后才能成功。</p>
        <button class="btn btn-plain btn-sm" @click="load()">重新加载</button>
      </div>

      <div class="acct-layout">
        <!-- ===== 左侧：账号概览 ===== -->
        <div class="acct-side">
          <span class="member-avatar big">
            <img v-if="isAvatarImage(account.avatar)" :src="account.avatar" alt="头像" />
            <template v-else>{{ account.avatar || (account.nickname || '语').slice(0, 1) }}</template>
          </span>
          <h3>{{ account.nickname || '青集市用户' }}</h3>
          <p class="acct-account">账号 @{{ account.userId || '—' }}</p>
          <p class="acct-sign">{{ account.signature || '还没有个性签名' }}</p>
          <div class="acct-tags">
            <span class="pill pill-gray">{{ account.gender }}</span>
            <span v-if="account.shopId" class="pill pill-orange">已开店</span>
          </div>
          <button class="btn btn-plain" @click="logout">退出登录</button>
        </div>

        <!-- ===== 右侧：各项设置 ===== -->
        <div class="acct-main">
          <!-- ① 基本资料：内嵌表单（原「编辑资料」弹窗已整合到此） -->
          <div class="profile-panel">
            <h3>✦ 基本资料 <small class="panel-tip">昵称 / 头像 / 性别 / 签名</small></h3>
            <div class="acct-form">
              <div class="form-row">
                <label>昵称</label>
                <input v-model="form.nickname" maxlength="20" placeholder="怎么称呼你" />
              </div>
              <div class="form-row">
                <label>性别</label>
                <div class="gender-row">
                  <label class="gender-opt"><input type="radio" name="acctGender" value="male" v-model="form.gender" />男</label>
                  <label class="gender-opt"><input type="radio" name="acctGender" value="female" v-model="form.gender" />女</label>
                  <label class="gender-opt"><input type="radio" name="acctGender" value="secret" v-model="form.gender" />保密</label>
                </div>
              </div>
              <div class="form-row">
                <label>头像</label>
                <div class="avatar-upload">
                  <span class="member-avatar big avatar-preview">
                    <img v-if="isAvatarImage(formAvatar)" :src="formAvatar" alt="头像" />
                    <template v-else>{{ formAvatar || (form.nickname || '语').slice(0, 1) }}</template>
                  </span>
                  <div class="avatar-upload-actions">
                    <button type="button" class="btn btn-plain" @click="pickAvatar">选择图片</button>
                    <small>支持 jpg / png / webp / gif，不超过 5MB；点「保存资料」时上传至阿里云 OSS</small>
                    <input ref="fileInput" type="file" accept="image/*" class="hidden" @change="onAvatarChange" />
                  </div>
                </div>
              </div>
              <div class="form-row">
                <label>个性签名</label>
                <input v-model="form.signature" maxlength="40" placeholder="一句话介绍自己" />
              </div>
              <div class="acct-form-actions">
                <button class="btn btn-plain" :disabled="savingProfile" @click="resetForm">撤销修改</button>
                <button class="btn btn-primary" :disabled="savingProfile" @click="saveProfile">
                  {{ savingProfile ? '保存中…' : '保存资料' }}
                </button>
              </div>
            </div>
          </div>

          <!-- ② 手机号 -->
          <div class="profile-panel">
            <h3>✦ 手机号 <small class="panel-tip">短信登录 · 找回密码 · 身份验证</small></h3>
            <ul class="acct-rows">
              <li>
                <span class="acct-label">手机号</span>
                <b class="acct-value">{{ phoneText }}</b>
                <button class="btn btn-plain btn-sm" @click="phoneModal">
                  {{ account.phoneNumber ? '修改手机号' : '绑定手机号' }}
                </button>
              </li>
              <li v-if="account.phoneBoundAt">
                <span class="acct-label">绑定时间</span>
                <b class="acct-value">{{ dateOnly(account.phoneBoundAt) }}</b>
                <em class="acct-note"></em>
              </li>
            </ul>
            <p class="acct-tip">修改手机号需要「新手机号 + 短信验证码」，并验证当前登录密码；一个手机号只能绑定一个账号。</p>
          </div>

          <!-- ③ 登录密码 -->
          <div class="profile-panel">
            <h3>✦ 登录密码 <small class="panel-tip">修改后其它设备需重新登录</small></h3>
            <ul class="acct-rows">
              <li>
                <span class="acct-label">最近修改</span>
                <b class="acct-value">{{ dateOnly(account.lastPwdChangeAt) || '—' }}</b>
                <button class="btn btn-plain btn-sm" @click="passwordModal">修改密码</button>
              </li>
            </ul>
            <p class="acct-tip">密码以 BCrypt 哈希入库，服务端无法还原；忘记密码可在登录页用绑定手机号 + 短信验证码重置。</p>
          </div>

          <!-- ④ 收货地址 -->
          <div class="profile-panel">
            <h3>✦ 收货地址 <small class="panel-tip">{{ addrCount }} 个地址</small></h3>
            <ul class="acct-rows">
              <li>
                <span class="acct-label">地址簿</span>
                <b class="acct-value">新增 / 编辑 / 删除 / 设为默认</b>
                <button class="btn btn-plain btn-sm" @click="addressModal">管理收货地址</button>
              </li>
              <li>
                <span class="acct-label">注册时间</span>
                <b class="acct-value">{{ dateOnly(account.createdAt) || '—' }}</b>
                <em class="acct-note"></em>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
