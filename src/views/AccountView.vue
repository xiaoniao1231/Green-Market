<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import QM_UI from '../core/ui.js';
import QM_STORE from '../core/store.js';
import QM_API from '../core/api.js';
import openAddressModal from '../core/addressModal.js';
import { openPhoneModal, openPasswordModal, maskPhone } from '../core/accountModals.js';

const { toast, confirmDialog } = QM_UI;
const router = useRouter();

const AVATAR_MAX_SIZE = 100 * 1024 * 1024;
const isAvatarImage = v => typeof v === 'string' && /^(https?:|blob:)/i.test(v.trim());

const me = ref(null);
const phase = ref('loading');
const errorMsg = ref('');
const user = computed(() => QM_STORE.state.user);

const addrItems = ref([]);
const addrError = ref('');
const addrCount = computed(() => addrItems.value.length);
async function loadAddresses() {
  try {
    await QM_API.addresses.list();
    addrError.value = '';
  } catch (e) {
    addrError.value = (e && e.message) || '地址加载失败';
  }
  addrItems.value = QM_STORE.addr.list().slice();
}
const offAddresses = QM_STORE.on('addresses', () => { addrItems.value = QM_STORE.addr.list().slice(); });

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
  resetForm();
}
onMounted(() => { load(); loadAddresses(); });

const GENDER_TEXT = { male: '男 ♂', female: '女 ♀', secret: '保密' };
const account = computed(() => {
  const m = me.value || {};
  const u = user.value || {};
  const genderRaw = ['male', 'female', 'secret'].includes(m.gender) ? m.gender
    : (['male', 'female', 'secret'].includes(u.gender) ? u.gender : 'secret');
  return {
    userId: m.userId || u.userId || '',
    nickname: m.nickname || u.nickname || '',
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
const dateTimeText = v => {
  const s = String(v || '').trim();
  if (!s) return '';
  return s.replace('T', ' ').slice(0, 16);
};
const phoneText = computed(() => {
  if (account.value.phoneNumber) return maskPhone(account.value.phoneNumber);
  return phase.value === 'error' ? '—（资料接口不可用）' : '未绑定';
});

const form = ref({ nickname: '', gender: 'secret', signature: '' });
const formAvatar = ref('');
const pickedAvatar = ref(null);
const savingProfile = ref(false);
const fileInput = ref(null);
let objectUrl = null;

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
  if (f.size > AVATAR_MAX_SIZE) return toast('头像图片不能超过 100MB', 'error');
  releasePreview();
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
    let avatar = formAvatar.value;
    if (pickedAvatar.value) {
      const data = await QM_API.user.uploadAvatar(pickedAvatar.value);
      const url = data && (data.url || data.avatar || data.fileUrl);
      if (!url) throw new Error('头像上传成功但未返回图片地址');
      avatar = url;
    }
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
    toast(e.message || '保存失败', 'error');
  } finally {
    savingProfile.value = false;
  }
}
onBeforeUnmount(() => { releasePreview(); offAddresses(); });

function phoneModal() {
  openPhoneModal({ current: me.value || {}, onSaved: () => load({ silent: true }) });
}
function passwordModal() {
  openPasswordModal({
    onSaved: ({ reloginRequired }) => {
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
  await openAddressModal({ onSaved: () => { loadAddresses(); } });
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

    <div v-if="phase === 'loading'" class="order-card">
      <div class="empty-state">
        <div class="empty-icon">⏳</div>
        <h3>正在加载账号资料…</h3>
      </div>
    </div>

    <template v-else>
      <div v-if="phase === 'error'" class="acct-alert">
        <b>账号资料接口暂不可用</b>
        <p>{{ errorMsg }}，写操作暂不可用。</p>
        <button class="btn btn-plain btn-sm" @click="load()">重新加载</button>
      </div>

      <div class="acct-layout">
        <div class="acct-side">
          <span class="member-avatar big">
            <img v-if="isAvatarImage(account.avatar)" :src="account.avatar" alt="头像" />
            <template v-else>{{ account.avatar || (account.nickname || '语').slice(0, 1) }}</template>
          </span>
          <h3>{{ account.nickname || '玉子市场用户' }}</h3>
          <p class="acct-account">账号 @{{ account.userId || '—' }}</p>
          <p class="acct-sign">{{ account.signature || '还没有个性签名' }}</p>
          <div class="acct-tags">
            <span class="pill pill-gray">{{ account.gender }}</span>
            <span v-if="account.shopId" class="pill pill-orange">已开店</span>
          </div>
          <button class="btn btn-plain" @click="logout">退出登录</button>
        </div>

        <div class="acct-main">
          <div class="profile-panel">
            <h3>✦ 基本资料</h3>
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
                    <small>支持 jpg / png / webp / gif，不超过 100MB</small>
                    <input ref="fileInput" type="file" accept="image/*" class="hidden" @change="onAvatarChange" />
                  </div>
                </div>
              </div>
              <div class="form-row">
                <label>个性签名</label>
                <input v-model="form.signature" maxlength="40" placeholder="一句话介绍自己" />
              </div>
              <div class="acct-form-actions">
                <button class="btn btn-primary" :disabled="savingProfile" @click="saveProfile">
                  {{ savingProfile ? '保存中…' : '保存资料' }}
                </button>
              </div>
            </div>
          </div>

          <div class="profile-panel">
            <h3>✦ 手机号</h3>
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
                <b class="acct-value">{{ dateTimeText(account.phoneBoundAt) || '—' }}</b>
                <em class="acct-note"></em>
              </li>
            </ul>
          </div>

          <div class="profile-panel">
            <h3>✦ 登录密码</h3>
            <ul class="acct-rows">
              <li>
                <span class="acct-label">最近修改时间</span>
                <b class="acct-value">{{ dateTimeText(account.lastPwdChangeAt) || '—' }}</b>
                <button class="btn btn-plain btn-sm" @click="passwordModal">修改密码</button>
              </li>
            </ul>
          </div>

          <div class="profile-panel acct-addr-panel">
            <div class="acct-addr-main">
              <h3>✦ 收货地址 <small class="panel-tip">{{ addrCount }} 个地址</small></h3>
              <div v-if="addrItems.length" class="acct-addr-list">
                <div v-for="a in addrItems" :key="a.id" class="acct-addr-item">
                  <div class="acct-addr-head">
                    <b class="acct-addr-name">{{ a.name }}</b>
                    <span class="acct-addr-phone">{{ a.phone }}</span>
                    <span v-if="a.isDefault" class="pill pill-orange">默认</span>
                    <span v-if="a.tag" class="pill pill-gray">{{ a.tag }}</span>
                  </div>
                  <p class="acct-addr-detail">{{ a.region }} {{ a.detail }}</p>
                </div>
              </div>
              <p v-else class="acct-empty">{{ addrError || '还没有收货地址' }}</p>
            </div>
            <button class="btn btn-plain btn-sm acct-addr-manage" @click="addressModal">管理收货地址</button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
