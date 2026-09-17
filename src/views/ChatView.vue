<script setup>
/* =========================================================
   青集市 · views/ChatView.vue —— 消息中心（完整聊天功能页）
   移植自 mall-web/js/pages/chat.js（页面结构 / 交互逻辑不变）
   · 登录 + 后端在线：对接真实接口（/users/online
     /messages/history /messages/private + WebSocket /ws 实时推送）
   · WebSocket 实时通道由 App.vue 登录时全局建立（登录即在线），
     本页挂载时订阅其实时帧、卸载时退订，不再自建 / 自关连接；
   · 会话列表 / 聊天窗口 / 文本·商品·文件消息 / 未读角标、
     route.query.peer 自动开聊 / 聊天面板随 openPeer 重建（等价 innerHTML 整体替换）
   · open-product / quick-add-cart（消息里商品气泡）由 App.vue 全局事件代理处理，
     本组件仅保留 data-action 属性、不重复实现（见移植规范第 4 条）。
   ========================================================= */
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import QM_UI from '../core/ui.js';
import QM_MOCK from '../core/mock.js';
import QM_API from '../core/api.js';
import QM_CFG from '../core/config.js';
import QM_STORE from '../core/store.js';
import QM_CHAT_SOCKET from '../core/chatSocket.js';
import useRouteCompat from '../composables/useRouteCompat.js';

const { esc, price, artStyle, artHtml, toast, confirmDialog } = QM_UI;
const route = useRouteCompat();

const EMOJIS = ['😀', '😂', '😍', '🤔', '😭', '😅', '👍', '🙏', '🎉', '❤️', '🌞', '🌙', '☕', '🍰', '🎁', '🛍️', '👌', '💪', '🔥', '⭐', '🐱', '🌸', '🎵', '🏃'];

/* 原版 chat.js 的局部可变状态：demo 仅为功能标记（原版仅置位、未读取），
   activePeer / emojiOpen 需驱动模板，ws 保存当前 WebSocket 连接 */
const state = reactive({
  demo: false,
  activePeer: null,
  ws: null,
  emojiOpen: false,
  /* 文件上传中：上传期间禁用「📎 发送文件」按钮、显示进度提示，避免重复点击传多份 */
  uploading: false,
  /* 图片放大预览灯箱：{ kind:'image', url, name, size } | null —— 点气泡里的图片即打开 */
  preview: null,
  /* 图片/视频卡片右上角 ⋮ 的弹出菜单：{ url, name, top, left } | null */
  mediaMenu: null,
  /* 在线状态刷新计数：QM_STORE.state 不是响应式对象，靠它驱动 peer computed 重算，
     否则收到 PRESENCE 广播后左侧绿点会更新，聊天面板头部的「● 在线」却不变 */
  presenceTick: 0
});

const chatInputEl = ref(null);     // 文本输入框（原 #chatInput）
const fileInputEl = ref(null);     // 隐藏文件选择（原 #fileInput）
const contactsHtml = ref('');      // 会话列表（#chatContacts 内容，等价 renderContacts 的 innerHTML）
const messagesBoxHtml = ref('');   // 聊天消息（#chatMessages 内容，等价 messagesHtml() 的 innerHTML）

/* ---------- 统一消息中心：用户与用户之间聊天 ----------
   消息就是当前登录用户直接以自己的用户账号发出（from=user.userId），
   没有「买家 / 卖家」身份切换：谁登录，谁就以自己的用户账号说话。 */

let peerTimer = null; // route.query.peer 自动开聊的 80ms 延时（离开页面 / 重新进入时清理）
let docEmojiHandler = null; // 点击空白关闭表情面板（原版 bindCompose 里的 document once 监听，Vue 版改为常驻）
let alive = false;    // 组件是否仍挂载：防止卸载后异步返回再建 WebSocket（原版 remount 竞态的清理）

/* 已知在线账号集合：由首屏快照（loadRealData 的 /users/online）与 PRESENCE 实时广播共同维护，
   供新建联系人时判断初始在线状态（原先硬编码 online: true，会导致离线对端也显示「在线」） */
let knownOnline = new Set();

/* 历史消息分页状态：peerId 当前会话、page 已加载页数、loaded 已加载条数、
   total 服务端总数、loading 请求中、hasMore 是否还有更早消息（滚动到顶部继续加载） */
const hist = { peerId: null, page: 0, size: 50, total: 0, loaded: 0, loading: false, hasMore: true };

/* 服务端已读同步：打开会话 / 正在查看时收到新消息 → 通知后端记录 last_read_time（2 秒节流） */
const readSyncAt = {};
function syncRead(peerId) {
  if (!peerId) return;
  const now = Date.now();
  if (readSyncAt[peerId] && now - readSyncAt[peerId] < 2000) return;
  readSyncAt[peerId] = now;
  QM_API.chat.read(peerId).catch(() => { /* 后端未实现时静默忽略，下次打开再同步 */ });
}

/* 当前会话对象（对端显示信息）：原 activePanelHtml 中 peer.name / peer.color / peer.online */
const peer = computed(() => {
  void state.presenceTick; // 建立响应式依赖：有人上下线时重算，刷新「● 在线 / ○ 离线」
  const c = state.activePeer ? contactById(state.activePeer) : null;
  const id = state.activePeer || '';
  const name = (c && (c.name || c.id)) || id;
  return {
    id,
    name,
    color: (c && c.color) || '#6b6bdf',
    online: !!(c && c.online)
  };
});

/* ---------- 工具（原版逐行对应） ---------- */
function contactById(id) { return QM_STORE.chat.contacts().find(c => c.id === id) || null; }

function unreadOf(id) {
  /* 未读数以会话（联系人）维度为准：restoreConversations 从服务端 unreadCount 同步，
     实时收到消息时 pushLocal 自增，打开会话时 markRead 清零 */
  const c = contactById(id);
  return (c && typeof c.unread === 'number') ? c.unread : 0;
}

function lastMsg(id) {
  const list = QM_STORE.chat.messages(id);
  return list[list.length - 1] || null;
}

function sortedContacts(query) {
  const list = QM_STORE.chat.contacts().filter(c => {
    if (!query) return true;
    return (c.name || c.id).toLowerCase().includes(query.toLowerCase());
  });
  const timeOf = c => { const last = lastMsg(c.id); return last ? last.time : 0; };
  return list.sort((a, b) => (Number(b.pinned || 0) - Number(a.pinned || 0)) || (timeOf(b) - timeOf(a)));
}

/* ---------- 渲染（字符串与原版一致） ---------- */
function contactRow(c, active) {
  const unread = unreadOf(c.id);
  const last = lastMsg(c.id);
  const preview = last
    ? (last.type === 'goods' ? '[商品推荐] ' : last.from === 'me' ? '我：' : '') + (last.content || '')
    : (c.intro || '打个招呼吧');
  /* 会话对应的店铺是自己的店铺时加「我的店铺」徽标（便于识别自己店铺收到的咨询会话） */
  const u = QM_STORE.state.user;
  const isMyShop = !!(u && u.shopId && (() => {
    const svc = QM_MOCK.serviceById(c.id);
    return svc && svc.id === u.shopId;
  })());
  const badge = isMyShop ? '<i class="pin">我的店铺</i>' : '';
  return `
  <button class="contact-item ${active ? 'active' : ''}" data-action="chat-open" data-id="${esc(c.id)}">
    <span class="avatar" style="background:${esc(c.color || '#6b6bdf')}">
      ${esc((c.name || c.id).slice(0, 1))}
      <span class="presence ${c.online ? 'online' : ''}"></span>
    </span>
    <span class="contact-detail">
      <strong>${esc(c.name || c.id)}${badge}${c.pinned ? '<i class="pin">置顶</i>' : ''}</strong>
      <small class="ellipsis">${esc(preview)}</small>
    </span>
    <span class="contact-time">${last ? esc(QM_UI.timeText(last.time)) : ''}</span>
    ${unread ? `<span class="contact-unread">${unread > 99 ? '99+' : unread}</span>` : ''}
  </button>`;
}

function dayDivider(ts) {
  const d = new Date(ts);
  const now = new Date();
  const pad = v => String(v).padStart(2, '0');
  let label;
  if (d.toDateString() === now.toDateString()) label = '今天';
  else if (new Date(now.getTime() - 86400e3).toDateString() === d.toDateString()) label = '昨天';
  else label = `${d.getMonth() + 1}月${d.getDate()}日`;
  return `<div class="day-divider"><span>${label}</span></div>`;
}

/* ---------- 文件消息工具 ---------- */
function fmtSize(bytes) {
  if (!bytes && bytes !== 0) return '';
  if (bytes < 1024) return bytes + 'B';
  if (bytes < 1024 * 1024) return Math.ceil(bytes / 1024) + 'KB';
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + 'MB';
  return (bytes / 1024 / 1024 / 1024).toFixed(1) + 'GB';
}

/* 文件消息的文本形态（用于会话预览 / 历史去重，与老版本文本气泡口径一致） */
function fileMsgText(name, size) {
  return `[文件] ${name}（${fmtSize(size) || '0KB'}）`;
}

/* ---------- 文件消息：按类型决定预览方式 ----------
   类型判定依据文件名扩展名（后端 content 列存的就是原始文件名）；文件名没有
   扩展名时退化为用 OSS 地址里的扩展名判断；都不匹配则只提供下载。
     · 图片 / 视频 / 音频 → 直接在聊天里预览
     · PDF / 纯文本 / Office 文档 → 点「预览」在弹出窗口里看（不用先下载） */
const IMAGE_EXT = /\.(png|jpe?g|gif|webp|bmp|svg|avif|ico)(\?|#|$)/i;
const VIDEO_EXT = /\.(mp4|webm|ogv|mov|m4v|mkv)(\?|#|$)/i;
const AUDIO_EXT = /\.(mp3|wav|ogg|m4a|aac|flac|opus)(\?|#|$)/i;
const PDF_EXT = /\.pdf(\?|#|$)/i;
const TEXT_EXT = /\.(txt|md|markdown|csv|json|log|xml|yml|yaml|ini|conf|properties|sql|sh|bat|java|js|ts|vue|py|c|cpp|h|css)(\?|#|$)/i;
const OFFICE_EXT = /\.(docx?|xlsx?|pptx?|odt|ods|odp|rtf)(\?|#|$)/i;

/* 可以在弹窗里预览的类型（图片/视频走媒体弹窗，文档走 iframe 弹窗） */
const DOC_KINDS = ['pdf', 'text', 'office'];

function fileKind(name, url) {
  const hay = String(name || '') + ' ' + String(url || '');
  if (IMAGE_EXT.test(hay)) return 'image';
  if (VIDEO_EXT.test(hay)) return 'video';
  if (AUDIO_EXT.test(hay)) return 'audio';
  if (PDF_EXT.test(hay)) return 'pdf';
  if (OFFICE_EXT.test(hay)) return 'office';
  if (TEXT_EXT.test(hay)) return 'text';
  return 'file';
}

const isDocKind = kind => DOC_KINDS.includes(kind);

/* Office 文档用微软在线预览服务渲染（OSS 地址本来就是公网可直链，满足它的取件要求） */
function officeViewerUrl(url) {
  return 'https://view.officeapps.live.com/op/embed.aspx?src=' + encodeURIComponent(url);
}

function kindIcon(kind) {
  if (kind === 'pdf') return '📕';
  if (kind === 'text') return '📃';
  if (kind === 'office') return '📘';
  return '📄';
}

/* 文件气泡 HTML：
   · 图片 / 视频 → 内容卡片（Telegram 风格）：整块是媒体本身，左上角一个「⬇ 时长 / 大小」胶囊
     （下载入口就在胶囊上），右上角 ⋮ 菜单，视频中间一个圆形播放按钮；图片点一下即放大；
   · 音频 → 气泡内嵌播放条（左侧仍是圆形下载按钮，右上角大小角标）；
   · 其它文件 → 图标 + 文件名 + 大小 + 右侧「下载」按钮（最早的样子，未改动）。 */
function fileBubbleHtml(name, size, url, kind) {
  const meta = esc(size || '');
  const info = `
        <span class="file-ico">${kindIcon(kind)}</span>
        <span class="file-info">
          <span class="file-name" title="${esc(name)}">${esc(name)}</span>
          <span class="file-meta">${meta}</span>
        </span>`;
  /* 老数据 / 上传失败：没有地址就不给下载入口，只留文件名 */
  if (!url) {
    return `<div class="bubble file-bubble">${info}</div>`;
  }

  /* ---------- 图片 / 视频：内容卡片 ---------- */
  if (kind === 'image' || kind === 'video') {
    const tag = `
        <a class="file-card-tag" href="${esc(url)}" target="_blank" rel="noopener"
           download="${esc(name)}" title="下载 ${esc(name)}">
          <span class="fct-ico">⬇</span>
          <span class="fct-lines">
            ${kind === 'video' ? '<b class="fct-dur" hidden>0:00</b>' : ''}
            <i class="fct-size">${meta || '文件'}</i>
          </span>
        </a>`;
    const more = `
        <button class="file-card-more" type="button" title="更多操作" aria-label="更多操作"
                data-action="media-menu" data-url="${esc(url)}" data-name="${esc(name)}">⋮</button>`;
    const media = kind === 'image'
      ? `<img class="file-thumb" src="${esc(url)}" alt="${esc(name)}" title="${esc(name)} · 点击放大"
            loading="lazy" data-action="preview-media" data-kind="image" data-size="${meta}"
            data-url="${esc(url)}" data-name="${esc(name)}" />`
      : `<video class="file-video" src="${esc(url)}" title="${esc(name)}" preload="metadata" playsinline></video>
          <button class="file-play" type="button" data-action="play-video" data-kind="video"
                  data-url="${esc(url)}" data-name="${esc(name)}" data-size="${meta}"
                  aria-label="播放视频">▶</button>`;
    return `<div class="bubble file-bubble media media-card"><div class="file-card">${media}${tag}${more}</div></div>`;
  }

  /* ---------- 音频：内嵌播放条 ---------- */
  if (kind === 'audio') {
    const download = `<a class="file-dl-side" href="${esc(url)}" target="_blank" rel="noopener"
        download="${esc(name)}" title="下载 ${esc(name)}" aria-label="下载 ${esc(name)}">⬇</a>`;
    const sizeTag = meta ? `<span class="file-size-tag">${meta}</span>` : '';
    return `<div class="file-row">${download}
      <div class="bubble file-bubble media media-audio">
        <div class="file-media-box is-audio">
          <audio class="file-audio" src="${esc(url)}" title="${esc(name)}" controls preload="metadata"></audio>
          ${sizeTag}
        </div>
      </div>
    </div>`;
  }

  /* ---------- 其它文件：图标 + 文件名 + 大小 +（可预览的文档）预览 + 下载 ---------- */
  const previewBtn = isDocKind(kind)
    ? `<button class="file-dl ghost" type="button" data-action="preview-doc" data-kind="${kind}"
          data-url="${esc(url)}" data-name="${esc(name)}" data-size="${meta}">预览</button>`
    : '';
  return `
      <div class="bubble file-bubble">
        ${info}
        ${previewBtn}
        <a class="file-dl" href="${esc(url)}" target="_blank" rel="noopener" download="${esc(name)}">下载</a>
      </div>`;
}

/** 视频时长（秒 → m:ss）：拿到元数据后回填到卡片胶囊的第一行 */
function fmtDuration(sec) {
  if (!isFinite(sec) || sec <= 0) return '';
  const m = Math.floor(sec / 60);
  const s = Math.round(sec % 60);
  return m + ':' + String(s).padStart(2, '0');
}

/** 渲染后回填视频时长（元数据可能还没加载完，等 loadedmetadata 再填） */
function hydrateVideoMeta() {
  const box = document.getElementById('chatMessages');
  if (!box) return;
  box.querySelectorAll('.file-card').forEach(card => {
    const v = card.querySelector('video');
    const dur = card.querySelector('.fct-dur');
    if (!v || !dur) return;
    const apply = () => {
      const text = fmtDuration(v.duration);
      if (text) { dur.textContent = text; dur.hidden = false; }
    };
    if (v.readyState >= 1) apply();
    else v.addEventListener('loadedmetadata', apply, { once: true });
  });
}

function msgHtml(m, peerObj) {
  if (m.type === 'sys') {
    return `<div class="msg-row msg-sys"><div class="bubble">${esc(m.content)}</div></div>`;
  }
  /* 用户与用户之间聊天：from === 当前用户 id（或旧数据 'me'）即为「我」发出的消息 */
  const u = QM_STORE.state.user;
  const mine = m.from === 'me' || (u && m.from === u.userId);
  let name, color;
  if (mine) {
    name = (u && u.nickname) || '我';
    color = '#ff6a2b';
  } else if (m.from === peerObj.id) {
    /* 对端用户发的消息 */
    name = (peerObj && peerObj.name) || m.from;
    color = (peerObj && peerObj.color) || '#6b6bdf';
  } else {
    /* 会话中的其他用户（多方会话时可能出现） */
    name = m.from || '对方';
    color = '#6b6bdf';
  }
  let bubble;
  if (m.type === 'goods') {
    const p = QM_MOCK.byId(m.goods);
    bubble = p ? `
      <div class="bubble goods-bubble" data-action="open-product" data-id="${esc(p.id)}">
        <div class="gb-art" style="${artStyle(p.art)}">${artHtml(p.art)}</div>
        <div class="gb-info"><h5 class="ellipsis-2">${esc(p.title)}</h5>${price(p.price)}<br/>
        <span class="btn btn-primary" data-action="quick-add-cart" data-id="${esc(p.id)}">加入购物车</span></div>
      </div>` : `<div class="bubble">${esc(m.content)}</div>`;
  } else if (m.type === 'file') {
    /* 文件消息：图片 / 视频 / 音频免下载直接预览（缩略图、内嵌播放器），其他文件维持下载入口 */
    const name = m.name || m.content || '文件';
    const url = m.url || '';
    bubble = fileBubbleHtml(name, fmtSize(m.size), url, fileKind(name, url));
  } else {
    bubble = `<div class="bubble">${esc(m.content)}</div>`;
  }
  return `
  <div class="msg-row ${mine ? 'mine' : ''}">
    <span class="avatar" style="background:${esc(color)}">${esc(name.slice(0, 1))}</span>
    <div class="msg-main">
      <span class="msg-meta">${esc(name)} · ${esc(QM_UI.timeText(m.time))}</span>
      ${bubble}
    </div>
  </div>`;
}

function messagesHtml(peerId, peerObj) {
  const list = QM_STORE.chat.messages(peerId);
  let html = '';
  let lastDay = '';
  list.forEach(m => {
    const day = new Date(m.time).toDateString();
    if (day !== lastDay) { html += dayDivider(m.time); lastDay = day; }
    html += msgHtml(m, peerObj);
  });
  return html || `<div class="chat-welcome"><div class="w-orb">◌</div><h3>和 ${esc(peerObj ? peerObj.name : '对方')} 打个招呼吧</h3><p>输入消息，按 Enter 发送</p></div>`;
}

/* ---------- 发送消息（原版逐行对应） ---------- */
function pushLocal(peerId, msg) {
  /* 服务端未读模型：对方发来的消息，在未打开该会话时累计未读数（打开时 markRead 清零）；
     先累计再 push（push 内部会 emit 'chat' 刷新顶部角标） */
  if (msg.from !== 'me' && state.activePeer !== peerId) {
    const c = contactById(peerId);
    if (c) c.unread = (c.unread || 0) + 1;
  }
  QM_STORE.chat.push(peerId, msg);
}

async function sendCurrent() {
  const input = chatInputEl.value;
  const content = (input && input.value.trim()) || '';
  const peerId = state.activePeer;
  if (!content || !peerId) return;
  if (input) input.value = '';
  /* 用户与用户之间聊天：直接以当前登录用户的账号发出（无身份切换概念） */
  const from = (QM_STORE.state.user && QM_STORE.state.user.userId) || 'me';
  try {
    const data = await QM_API.chat.send(peerId, content);
    /* 发送成功必须把这条消息补进本地会话并重绘：否则发送方自己的气泡不会出现
       （原实现只在 catch 降级分支里渲染，成功后界面看不到刚发的内容）；
       带上服务端 msgId，后续历史合并时可精确去重 */
    const sent = { from, type: 'text', content, time: Date.now() };
    if (data && data.msgId) sent.id = data.msgId;
    pushLocal(peerId, sent);
    renderActive();
    renderContacts();
  } catch (e) {
    /* 后端消息接口缺失（404）时降级为本地演示消息，保证聊天闭环可测试；
       其余错误（令牌失效 / 接收方不存在 / 网络中断）必须如实提示，不能伪装成"已发送" */
    if (/404|后端返回错误/.test(String(e && e.message))) {
      pushLocal(peerId, { from, type: 'text', content, time: Date.now() });
      renderActive();
      renderContacts();
      toast('后端消息接口未实现，已存为本地演示消息', 'success');
    } else {
      toast('发送失败：' + (e && e.message ? e.message : '未知错误'), 'error');
    }
  }
}

/* ---------- 会话切换（原版 openPeer：重建面板 → 绑定 → 刷列表 → 滚动到底） ---------- */
function scrollActiveMessages(force) {
  nextTick(() => {
    const box = document.getElementById('chatMessages');
    if (box && (force || box.scrollHeight - box.scrollTop - box.clientHeight < 80)) {
      box.scrollTop = box.scrollHeight;
    }
  });
}

function openPeer(peerId) {
  if (!alive) return;
  state.emojiOpen = false; // 原版每次重建面板都会把表情面板恢复为隐藏
  state.activePeer = peerId;
  /* 会话只能由商品详情「联系卖家」进入：联系人不存在时按对应店铺的开店用户元数据创建
     （role=shop 仅表示这是店铺会话；聊天全程发生在两个用户账号之间） */
  let peerObj = contactById(peerId);
  if (!peerObj) {
    const svc = QM_MOCK.serviceById(peerId);
    /* 新联系人的初始在线状态取自已知在线集合（首屏快照 + PRESENCE 广播），
       不再硬编码 true：对方离线时进会话不会先闪一下「在线」 */
    if (svc) {
      peerObj = QM_STORE.chat.ensureContact(svc.userId, svc.name, {
        role: 'shop', online: knownOnline.has(svc.userId), color: svc.color, intro: svc.intro
      });
    } else {
      peerObj = QM_STORE.chat.ensureContact(peerId, peerId, {
        role: 'shop', online: knownOnline.has(peerId)
      });
    }
  }
  QM_STORE.chat.markRead(peerId);
  syncRead(peerId); // 打开会话即通知服务端记录已读
  messagesBoxHtml.value = messagesHtml(peerId, peerObj);
  renderContacts();
  scrollActiveMessages(true);
  /* 原版 openPeer 会用 innerHTML 整体重建聊天面板（草稿、文件选择一并清空）；
     Vue 面板 DOM 常驻，这里显式复位有状态的输入控件以还原该行为 */
  nextTick(() => {
    const input = chatInputEl.value;
    if (input) { input.value = ''; input.style.height = ''; }
    const fi = fileInputEl.value;
    if (fi) fi.value = '';
    hydrateVideoMeta(); // 视频卡片回填时长
  });
  /* 打开会话即加载第一页历史（点击会话 & 商品详情「联系卖家」?peer= 自动开聊都走这里） */
  loadHistory(peerId);
}

/* 只重绘 #chatMessages（发消息 / 收到消息 / 清空后）；保持"贴底才自动滚动"的原版手感 */
function renderActive() {
  if (!state.activePeer) return;
  const peerObj = contactById(state.activePeer);
  const box = document.getElementById('chatMessages');
  if (box) {
    const atBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 80;
    messagesBoxHtml.value = messagesHtml(state.activePeer, peerObj);
    if (atBottom) scrollActiveMessages(true);
    nextTick(hydrateVideoMeta); // 视频卡片回填时长（元数据可能晚于渲染到达）
  }
}

function renderContacts() {
  const query = (document.getElementById('chatSearch') || {}).value || '';
  const contacts = sortedContacts(query);
  contactsHtml.value = contacts.length
    ? contacts.map(c => contactRow(c, c.id === state.activePeer)).join('')
    : '<div class="chat-empty-side">暂无会话，去商品详情页点「联系卖家」开始咨询</div>';
}

/* ---------- 输入区交互（原版 bindCompose，DOM 常驻模板 → 事件绑定一次即可） ---------- */
function onInputKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendCurrent(); }
}

function resizeChatInput() {
  const input = chatInputEl.value;
  if (!input) return;
  input.style.height = 'auto';
  input.style.height = Math.min(input.scrollHeight, 110) + 'px';
}

function toggleEmoji() {
  state.emojiOpen = !state.emojiOpen;
}

/* 原版：每次 openPeer 在 bindCompose 里注册一个 document once 点击监听用于关闭表情面板。
   Vue 版改为常驻委托监听，效果一致：点击任意非 #emojiBtn 位置都会收起面板
   （emojiBtn 点击已 stopPropagation，等同原版不会触发关闭）。 */
function onDocEmojiClick(e) {
  if (!e.target || !e.target.closest) return;
  /* 点别处关闭 ⋮ 菜单（点菜单自身与 ⋮ 按钮时不关，各自有处理） */
  if (state.mediaMenu && !e.target.closest('.media-menu') && !e.target.closest('[data-action="media-menu"]')) {
    state.mediaMenu = null;
  }
  if (e.target.closest('#emojiBtn')) return;
  if (state.emojiOpen) state.emojiOpen = false;
}

function onEmojiPanelClick(e) {
  const btn = e.target.closest('[data-emoji]');
  if (btn) {
    const input = chatInputEl.value;
    if (input) { input.value += btn.dataset.emoji; input.focus(); }
  }
}

function onFileBtnClick() {
  if (state.uploading) return; // 上一个文件还在上传，忽略重复点击
  const fi = fileInputEl.value;
  if (fi) fi.click();
}

async function onFileInputChange(e) {
  const file = e.target.files && e.target.files[0];
  e.target.value = ''; // 立即复位，连续选择同一个文件也能再次触发 change
  if (!file) return;
  if (!state.activePeer) { toast('请先选择一位联系人再发送文件', 'error'); return; }

  /* 前端预检：超限文件不必白传一趟（上限见 core/config.js · UPLOAD_MAX_SIZE） */
  const max = QM_CFG.UPLOAD_MAX_SIZE || 0;
  if (max && file.size > max) {
    toast('文件不能超过 ' + fmtSize(max) + '（当前 ' + fmtSize(file.size) + '）', 'error');
    return;
  }

  const peerId = state.activePeer; // 固定本次发送的会话：上传耗时较长，期间用户切换会话也不会发错人
  state.uploading = true;
  toast('正在上传「' + file.name + '」' + (fmtSize(file.size) ? '（' + fmtSize(file.size) + '）' : '') + '，请稍候…');
  try {
    /* 上传到阿里云 OSS + 落库（数据库 file_url 存的就是 OSS 地址）；
       上传方式见 core/config.js · UPLOAD_MODE，data 含 fileName / fileSize / fileUrl / msgId */
    const data = await QM_API.chat.sendFile(file, peerId);
    const name = (data && data.fileName) || file.name;
    const size = (data && data.fileSize !== undefined && data.fileSize !== null) ? data.fileSize : file.size;
    const url = (data && data.fileUrl) || '';
    const sent = { from: 'me', type: 'file', name, size, url, content: fileMsgText(name, size), time: Date.now() };
    if (data && data.msgId) sent.id = data.msgId; // 带服务端 msgId，历史合并时可精确去重
    pushLocal(peerId, sent);
    renderActive(); renderContacts();
    if (!url) toast('文件已发送，但后端未返回 OSS 地址（气泡里将显示「无地址」）', 'error');
    else toast('已发送「' + name + '」');
  } catch (err) {
    toast(err.message || '文件发送失败', 'error');
  } finally {
    state.uploading = false;
  }
}

/* ---------- 会话区事件委托（原版 mount 里 #chatPanel / #chatContacts 的 onclick） ---------- */
/* 注：消息气泡里的 open-product / quick-add-cart 由 App.vue 全局代理处理，这里只处理
   chat-clear 这类页面特有动作，避免重复加购 / 重复跳转（见移植规范第 4 条）。 */
/* ---------- 预览窗口（图片放大 / 视频播放 / 文档预览共用） ----------
   关闭方式只有两种：点窗口里的「关闭」按钮，或按 Esc —— 点窗口以外的区域不会退出。 */
function openPreview(kind, url, name, size) {
  if (!url) return;
  const k = kind || 'image';
  /* 文档（PDF / 文本 / Office）：先探测后端内联代理是否可用，再决定窗口里放什么地址 */
  if (isDocKind(k)) {
    state.preview = { kind: k, url, name: name || '文件', size: size || '', frameSrc: '', proxyOk: null };
    resolveDocFrame(k, url);
    return;
  }
  state.preview = { kind: k, url, name: name || '文件', size: size || '' };
}

/* 后端内联代理地址（同源）：绕开 OSS 对象自带的 Content-Disposition: attachment 与跨域限制 */
function inlinePreviewUrl(url) {
  return String(QM_CFG.API_BASE).replace(/\/+$/, '') + '/files/preview?url=' + encodeURIComponent(url);
}

/* 探测后端是否实现了 /files/preview（结果缓存一次；后端未实现时返回 404，前端自动降级） */
let inlineProxyAvailable = null;
async function probeInlineProxy() {
  if (inlineProxyAvailable !== null) return inlineProxyAvailable;
  const base = String(QM_CFG.API_BASE).replace(/\/+$/, '');
  try {
    const r = await fetch(base + '/files/preview?probe=1&url=' + encodeURIComponent('probe'));
    inlineProxyAvailable = r.ok;
  } catch (e) {
    inlineProxyAvailable = false;
  }
  return inlineProxyAvailable;
}

/* 决定文档预览窗口里的地址：
   · 代理可用 → 同源代理（PDF / 文本 / Office 都能直接看）；
   · 代理不可用 → PDF / Office 退化为微软在线预览（由微软服务器取件，不受浏览器限制），
     纯文本没有可靠兜底，窗口里给出说明与下载入口。 */
async function resolveDocFrame(kind, url) {
  const ok = await probeInlineProxy();
  if (!state.preview || state.preview.url !== url) return; // 探测期间窗口已被关闭
  state.preview.proxyOk = ok;
  state.preview.frameSrc = ok ? inlinePreviewUrl(url) : (kind === 'text' ? '' : officeViewerUrl(url));
}
function closePreview() {
  state.preview = null;
}
/* 消息区点击委托：v-html 渲染出来的气泡用 data-action 声明意图（与页面其他委托口径一致） */
function onMessagesClick(e) {
  const el = e.target.closest ? e.target.closest('[data-action]') : null;
  if (!el) return;
  const action = el.dataset.action;

  /* 视频中央播放键 / 文档「预览」 / 图片：统一在弹出窗口里打开
     （以前视频是原地切原生控件，用户容易顺手点到全屏；现在一律走窗口） */
  if (action === 'play-video' || action === 'preview-doc' || action === 'preview-media') {
    e.preventDefault();
    openPreview(el.dataset.kind, el.dataset.url, el.dataset.name, el.dataset.size);
    return;
  }

  /* 卡片右上角 ⋮：弹出「在新标签打开 / 下载」菜单（fixed 定位，贴着按钮下方） */
  if (action === 'media-menu') {
    e.preventDefault();
    e.stopPropagation();
    const r = el.getBoundingClientRect();
    state.mediaMenu = {
      url: el.dataset.url,
      name: el.dataset.name,
      top: Math.round(r.bottom + 6),
      left: Math.round(Math.max(8, Math.min(r.right - 168, window.innerWidth - 176)))
    };
    return;
  }
}
/* 图片加载失败（Bucket 私有读 / 地址失效）：把缩略图换成可读提示，而不是只留一个破图图标。
   注意 error 事件不冒泡，必须用捕获阶段监听。 */
function onMediaError(e) {
  const el = e.target;
  if (!el || el.tagName !== 'IMG' || !el.classList || !el.classList.contains('file-thumb')) return;
  const box = el.parentElement;
  if (!box || box.querySelector('.file-thumb-fail')) return;
  const tip = document.createElement('div');
  tip.className = 'file-thumb-fail';
  tip.textContent = '图片加载失败（可能是 Bucket 非公共读或地址已失效），可点「下载」查看';
  el.replaceWith(tip);
}
/* Esc 关闭预览灯箱 / ⋮ 菜单 */
function onPreviewKeydown(e) {
  if (e.key !== 'Escape') return;
  if (state.preview) closePreview();
  if (state.mediaMenu) state.mediaMenu = null;
}

function onPanelClick(e) {
  const t = e.target.closest ? e.target.closest('[data-action]') : null;
  if (!t) return;
  if (t.dataset.action === 'chat-clear') {
    confirmDialog('清空记录', '清空与对方的本地聊天记录？（服务器记录不受影响）', '清空', true).then(ok => {
      if (ok) { QM_STORE.state.chats[state.activePeer] = []; QM_STORE.saveNow(); renderActive(); renderContacts(); }
    });
  }
}

function onContactsClick(e) {
  const t = e.target.closest('[data-action="chat-open"]');
  if (!t) return;
  openPeer(t.dataset.id);
}

/* ---------- 实时模式：加载后端数据（原版 loadRealData） ---------- */
/* 实时通道（WebSocket）由 App.vue 在登录时全局建立（登录即在线，常驻保持）：
   本页不再自建连接 / 心跳，只做两件事：
   ① 首屏同步一次在线状态快照（GET /users/online）+ 恢复会话列表；
   ② 挂载期间订阅全局通道的实时帧（PRESENCE 在线广播 / 实时消息），卸载时退订。 */

async function loadRealData() {
  try {
    /* 消息中心只加载店铺会话：不再加载好友列表（没有独立的好友体系），
       联系人由商品详情「联系卖家」创建，这里只同步在线状态 */
    const online = await QM_API.chat.onlineUsers();
    if (!alive) return; // 组件已卸载：不再建连接
    knownOnline = new Set(online.onlineUsers || []);
    QM_STORE.chat.contacts().forEach(c => { c.online = knownOnline.has(c.id); });
    state.presenceTick++;
    renderContacts();
    /* 登录后从后端恢复会话列表：本地存储（sessionStorage）一旦被清空
       （重新登录 / 关闭标签页），消息中心会变成空白；这里把数据库里的
       历史会话（对端 + 最后一条消息）重建为会话入口，保证登录即有会话。 */
    try {
      await restoreConversations();
    } catch (e) { /* 会话列表接口不可用时保持本地现状，不阻断登录 */ }
    if (!alive) return;
    /* 复用全局实时通道（登录时已在 App 层建立）：已连接则直接取用；
       若连接已断开（后端重启 / 网络中断），ensure() 会顺手重建，等价原「刷新」重连 */
    state.ws = await QM_CHAT_SOCKET.ensure();
  } catch (e) {
    /* 后端未实现消息相关接口时静默降级为本地聊天（演示模式） */
  }
}

/* ---------- 全局实时通道帧处理 ----------
   PRESENCE：服务端在有人上线/下线时推送全量快照，覆盖式更新左侧绿点与当前会话头的在线状态；
   COMM_MES / TO_ALL / FILE_MES：实时消息，写入本地会话并刷新未读角标。 */
function handleSocketFrame(payload) {
  if (!alive) return;
  const msg = payload.message || {};
  if (payload.type === 'PONG') return;
  if (payload.type === 'PRESENCE') {
    knownOnline = new Set(msg.onlineUsers || []);
    QM_STORE.chat.contacts().forEach(c => { c.online = knownOnline.has(c.id); });
    state.presenceTick++;
    renderContacts();
    return;
  }
  if (['COMM_MES', 'TO_ALL', 'FILE_MES'].includes(payload.type)) {
    const myId = QM_STORE.state.user ? QM_STORE.state.user.userId : '';
    const peerId = msg.senderId === myId ? msg.receiverId : msg.senderId;
    if (!peerId) return;
    knownOnline.add(peerId); // 能发来消息说明对方此刻在线
    QM_STORE.chat.ensureContact(peerId, msg.senderNickname || peerId, { online: true });
    const ts = msg.sendTime ? new Date(String(msg.sendTime).replace(' ', 'T')).getTime() : Date.now();
    const name = msg.content || msg.fileName || '文件';
    const live = {
      from: msg.senderId === myId ? 'me' : peerId,
      type: msg.fileUrl ? 'file' : 'text',
      content: msg.fileUrl ? fileMsgText(name, msg.fileSize) : (msg.content || msg.fileName || ''),
      time: ts
    };
    if (msg.fileUrl) { live.name = name; live.size = msg.fileSize; live.url = msg.fileUrl; }
    if (msg.msgId) live.id = msg.msgId; // 带上服务端 msgId，历史合并时精确去重
    pushLocal(peerId, live);
    if (state.activePeer === peerId) { renderActive(); QM_STORE.chat.markRead(peerId); syncRead(peerId); }
    renderContacts();
  }
}

/* 本地消息与服务端消息是否同一条：优先 id（msgId）精确比对；
   旧本地消息没有 msgId 时，按 发送者+内容+时间相近（3 秒内）兜底，避免合并后重复气泡 */
function sameMsg(lm, sm) {
  if (lm.id && sm.id && lm.id === sm.id) return true;
  return lm.from === sm.from && lm.content === sm.content && Math.abs(lm.time - sm.time) < 3000;
}

/* 服务端历史消息 → 本地消息对象：文件消息带 OSS 地址时渲染为可下载的文件气泡；
   老记录（无 fileUrl，如后端未加 file_url 字段前的存量数据）仍按文本气泡展示。 */
function mapHistoryMsg(m, me, peerId) {
  const base = {
    id: m.msgId || ('h-' + Math.random().toString(36).slice(2)),
    from: m.senderId === me ? 'me' : peerId,
    time: m.sendTime ? new Date(String(m.sendTime).replace(' ', 'T')).getTime() : Date.now()
  };
  if (m.recalled) {
    base.type = 'sys';
    base.content = '对方撤回了一条消息';
  } else if (m.fileUrl) {
    base.type = 'file';
    base.name = m.content || m.fileName || '文件';
    base.size = m.fileSize;
    base.url = m.fileUrl;
    base.content = fileMsgText(base.name, base.size);
  } else {
    base.type = 'text';
    base.content = m.content || '';
  }
  return base;
}

/* 历史消息分页：第一页（最新 50 条）拉取，并与本地记录合并 */
async function loadHistory(peerId) {
  /* 每次进入会话重置分页状态，从第一页重新拉取 */
  hist.peerId = peerId;
  hist.page = 0;
  hist.loaded = 0;
  hist.total = 0;
  hist.hasMore = true;
  hist.loading = true;
  try {
    const data = await QM_API.chat.history(peerId, 1, hist.size);
    const me = QM_STORE.state.user ? QM_STORE.state.user.userId : 'me';
    const list = (data.list || []).slice().reverse().map(m => mapHistoryMsg(m, me, peerId));
    /* 与服务端历史合并而非整体覆盖：保留本地已收发的消息与未读标记，
       只补充服务端有而本地没有的消息（按 id / 发送者+内容+时间去重） */
    const cur = QM_STORE.chat.messages(peerId);
    const fresh = list.filter(sm => !cur.some(lm => sameMsg(lm, sm)));
    if (fresh.length) {
      QM_STORE.state.chats[peerId] = cur.concat(fresh).sort((a, b) => a.time - b.time);
    }
    hist.page = 1;
    hist.loaded = list.length;      // 分页计数只统计服务端已加载条数，保证还能继续翻更早的页
    hist.total = (data.total != null ? data.total : list.length);
    hist.hasMore = hist.loaded < hist.total;
    QM_STORE.saveNow();
    renderActive();
  } catch (e) {
    /* 后端未实现历史接口时保留本地演示记录（静默降级） */
  } finally {
    hist.loading = false;
  }
}

/* 分页加载更早的消息：滚动到顶部触发，旧消息插到列表头部并保持视口不跳动 */
async function loadOlder() {
  if (hist.loading || !hist.hasMore || !hist.peerId) return;
  const box = document.getElementById('chatMessages');
  if (!box) return;
  const prevHeight = box.scrollHeight;
  const prevTop = box.scrollTop;
  hist.loading = true;
  hist.page += 1;
  try {
    const data = await QM_API.chat.history(hist.peerId, hist.page, hist.size);
    const me = QM_STORE.state.user ? QM_STORE.state.user.userId : 'me';
    const older = (data.list || []).slice().reverse().map(m => mapHistoryMsg(m, me, hist.peerId));
    hist.total = (data.total != null ? data.total : hist.total);
    if (!older.length) { // 服务端没有更多了，停止继续请求
      hist.hasMore = false;
      hist.page -= 1;
      return;
    }
    hist.loaded += older.length; // 分页计数统计服务端已加载条数
    const cur = QM_STORE.chat.messages(hist.peerId);
    const fresh = older.filter(sm => !cur.some(lm => sameMsg(lm, sm)));
    if (fresh.length) {
      QM_STORE.state.chats[hist.peerId] = fresh.concat(cur).sort((a, b) => a.time - b.time);
      QM_STORE.saveNow();
      /* 用户已切换会话时不重绘当前窗口，但数据仍按会话写入，回来即完整 */
      if (state.activePeer === hist.peerId) {
        renderActive();
        nextTick(() => {
          const b = document.getElementById('chatMessages');
          if (b) b.scrollTop = prevTop + (b.scrollHeight - prevHeight);
        });
      }
    }
    hist.hasMore = hist.loaded < hist.total;
  } catch (e) {
    hist.page -= 1; // 拉取失败回退页码，滚动到顶部可重试
  } finally {
    hist.loading = false;
  }
}

/* 聊天记录滚动监听：滚到接近顶部时自动加载更早一页 */
function onMessagesScroll() {
  const box = document.getElementById('chatMessages');
  if (!box || box.scrollTop > 40) return;
  loadOlder();
}

/* 登录后重建会话列表：GET /messages/conversations 返回每个会话的对端与最后一条消息，
   据此 ensureContact 创建会话入口；本地没有该会话消息时写入最后一条作预览
   （点开会话仍走 loadHistory 拉全量，避免覆盖本地已有的完整历史）。 */
async function restoreConversations() {
  const convs = await QM_API.chat.conversations();
  if (!Array.isArray(convs) || !convs.length) return;
  const me = QM_STORE.state.user ? QM_STORE.state.user.userId : 'me';
  convs.forEach(conv => {
    const peerId = conv.peerId;
    if (!peerId) return;
    const contact = QM_STORE.chat.ensureContact(peerId, conv.peerName || peerId, { online: knownOnline.has(peerId) });
    /* 服务端未读数 → 本地会话角标（打开会话时 markRead 清零） */
    contact.unread = conv.unreadCount || 0;
    const msgs = QM_STORE.chat.messages(peerId);
    msgs.forEach(m => { m.unread = false; }); // 清掉历史遗留的消息级标记，避免重复计数
    if (msgs.length) return; // 本地已有历史，不覆盖
    if (conv.lastContent == null) return;     // 无最后消息（理论上不会发生）
    const ts = conv.lastTime ? Date.parse(String(conv.lastTime).replace(' ', 'T')) : NaN;
    const preview = {
      from: conv.lastSenderId === me ? 'me' : peerId,
      type: conv.lastRecalled ? 'sys' : 'text',
      content: conv.lastRecalled ? '对方撤回了一条消息' : (conv.lastContent || ''),
      time: Number.isNaN(ts) ? Date.now() : ts
    };
    if (conv.lastMsgId) preview.id = conv.lastMsgId; // 带 msgId 便于历史合并去重
    QM_STORE.chat.push(peerId, preview);
  });
  QM_STORE.saveNow();
  QM_STORE.emit('chat'); // 刷新顶部「消息中心」总未读角标
  renderContacts();
}

/* ---------- 页面入口（原版 mount / unmount 对应） ---------- */
/* 原版每次 hash 变化都会整页重挂载（unmount 旧 → mount 新）；Vue 里 ChatView 复用时
   由下面的 watch 触发同样的"软重挂载"：复位状态 → 重新加载（全局实时通道保持常驻）。 */
async function initView() {
  if (peerTimer) { clearTimeout(peerTimer); peerTimer = null; }
  state.demo = false;
  state.emojiOpen = false;
  state.activePeer = null;
  state.ws = null; // 实时通道状态由 loadRealData 从全局连接取回，先复位避免显示残留

  /* 登录用户 + 后端在线时加载真实聊天数据 */
  const user = QM_STORE.state.user;
  if (user) {
    if (QM_API.online === null || QM_API.online === false) await QM_API.health();
    if (!alive) return; // 等待期间组件已卸载
    if (QM_API.online) {
      await loadRealData();
    } else {
      toast('无法连接后端服务，请确认 Spring Boot 已启动', 'error');
    }
  }

  if (!alive) return;
  renderContacts();

  /* 从商品详情「联系卖家」进入（route.query.peer） */
  const peerId = route.value.query.peer;
  if (peerId) peerTimer = setTimeout(() => openPeer(peerId), 80);
}

async function onRefresh() {
  await loadRealData();
  toast('已刷新联系人');
}

/* 全局实时通道订阅（连接在 App.vue 登录时已建立）：挂载时订阅消息帧与断线提示 */
let offSocketMessage = null;
let offSocketClose = null;

onMounted(() => {
  alive = true;
  docEmojiHandler = onDocEmojiClick;
  document.addEventListener('click', docEmojiHandler);
  document.addEventListener('keydown', onPreviewKeydown);
  /* 捕获阶段监听图片加载失败（error 事件不冒泡） */
  document.addEventListener('error', onMediaError, true);
  offSocketMessage = QM_CHAT_SOCKET.onMessage(handleSocketFrame);
  offSocketClose = QM_CHAT_SOCKET.onClose(() => {
    if (!alive) return;
    const wasConnected = !!state.ws;
    state.ws = null;
    if (wasConnected) toast('实时通道已断开，点击右上「↻ 刷新」重连', 'error');
  });
  initView();
});

/* 同一组件在 /chat 与 /chat?peer=xxx 之间复用（点击顶部"消息中心"等）时重挂载页面 */
watch(() => route.value.query.peer, () => { initView(); });

onBeforeUnmount(() => {
  alive = false;
  if (peerTimer) { clearTimeout(peerTimer); peerTimer = null; }
  if (offSocketMessage) offSocketMessage();
  if (offSocketClose) offSocketClose();
  if (docEmojiHandler) { document.removeEventListener('click', docEmojiHandler); docEmojiHandler = null; }
  document.removeEventListener('keydown', onPreviewKeydown);
  document.removeEventListener('error', onMediaError, true);
  /* 注意：这里不关闭全局实时通道 —— 连接在 App.vue 层随登录态存亡，
     离开消息中心页用户仍保持在线（原实现在此 closeSocket，导致一离开就下线） */
});
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="crumb">首页 / 消息中心</div>
        <h1>消息中心 <small>QING CHAT</small></h1>
      </div>
    </div>
    <div class="chat-layout">
      <aside class="chat-sidebar">
        <div class="chat-side-head">
          <h2>会话列表 <a href="#/chat" id="refreshChat" @click.prevent="onRefresh">↻ 刷新</a></h2>
          <div class="chat-search"><span>⌕</span><input id="chatSearch" placeholder="搜索联系人" @input="renderContacts" /></div>
        </div>
        <div class="chat-contacts" id="chatContacts" @click="onContactsClick" v-html="contactsHtml"></div>
      </aside>
      <section class="chat-panel" id="chatPanel" @click="onPanelClick">
        <!-- 未选择会话：原版渲染骨架里 #chatPanel 内的默认欢迎区 -->
        <div v-if="!state.activePeer" class="chat-welcome">
          <div class="w-orb">◌</div>
          <h3>选择一位联系人开始聊天</h3>
          <p>在商品详情页点「联系卖家」，就能和开这家店的用户直接沟通商品、物流与售后</p>
        </div>
        <template v-else>
          <!-- 已选择会话：对应原版 activePanelHtml 的聊天窗口
               （openPeer 时按原版语义复位输入控件 / 表情面板，见 openPeer） -->
          <header class="chat-peer">
            <span class="avatar" :style="{ background: peer.color }">{{ peer.name.slice(0, 1) }}</span>
            <div>
              <b>{{ peer.name }}</b>
              <small v-if="peer.online">● 在线 · 消息实时送达</small>
              <small v-else>○ 离线 · 消息将保存到服务器</small>
            </div>
            <div class="peer-actions">
              <button class="icon-btn" title="清空本地记录" data-action="chat-clear">🗑</button>
            </div>
          </header>
          <div class="chat-modebar" :class="state.ws ? 'online' : 'offline'">
            <b>{{ state.ws ? '已连接后端' : '未连接实时通道' }}</b>
            · {{ state.ws ? 'WebSocket 实时消息通道开启中' : '点击右上「↻ 刷新」重连（消息仍可发送并存入服务器）' }}
          </div>
          <div class="chat-messages" id="chatMessages" v-html="messagesBoxHtml" @scroll="onMessagesScroll" @click="onMessagesClick"></div>
          <footer class="chat-compose">
            <div class="compose-tools" style="position:relative">
              <button class="icon-btn" id="emojiBtn" title="表情" @click.stop="toggleEmoji">😊</button>
              <button class="icon-btn" id="fileBtn" :disabled="state.uploading"
                      :title="state.uploading ? '文件上传中，请稍候…' : '发送文件'"
                      @click="onFileBtnClick">{{ state.uploading ? '⏳' : '📎' }}</button>
              <input type="file" id="fileInput" class="hidden" @change="onFileInputChange" ref="fileInputEl" />
              <span class="compose-hint">Enter 发送 · Shift+Enter 换行 · 最多 10000 字</span>
              <div class="emoji-panel" id="emojiPanel" :class="{ hidden: !state.emojiOpen }" @click="onEmojiPanelClick">
                <button v-for="e in EMOJIS" :key="e" type="button" :data-emoji="e">{{ e }}</button>
              </div>
            </div>
            <div class="compose-row">
              <textarea id="chatInput" ref="chatInputEl" rows="1" maxlength="10000" placeholder="输入消息…" @keydown="onInputKeydown" @input="resizeChatInput"></textarea>
              <button class="send-btn" id="sendBtn" @click="sendCurrent">发送 ↗</button>
            </div>
          </footer>
        </template>
      </section>
    </div>
    <!-- 图片/视频卡片右上角 ⋮ 的弹出菜单（fixed 定位，点别处或 Esc 关闭） -->
    <div v-if="state.mediaMenu" class="media-menu"
         :style="{ top: state.mediaMenu.top + 'px', left: state.mediaMenu.left + 'px' }"
         @click="state.mediaMenu = null">
      <a :href="state.mediaMenu.url" target="_blank" rel="noopener">在新标签打开</a>
      <a :href="state.mediaMenu.url" :download="state.mediaMenu.name">下载文件</a>
    </div>
    <!-- 预览窗口（图片放大 / 视频播放 / PDF·文本·Office 文档预览）：
         点窗口以外的区域不会关闭，必须点「关闭」按钮（或按 Esc）才退出 -->
    <div v-if="state.preview" class="media-lightbox">
      <div class="ml-body">
        <img v-if="state.preview.kind === 'image'" :src="state.preview.url" :alt="state.preview.name" />
        <video v-else-if="state.preview.kind === 'video'" :src="state.preview.url" controls autoplay playsinline></video>
        <audio v-else-if="state.preview.kind === 'audio'" :src="state.preview.url" controls autoplay></audio>
        <iframe v-else-if="state.preview.frameSrc" class="ml-frame"
                :src="state.preview.frameSrc" :title="state.preview.name"></iframe>
        <div v-else class="ml-doc-fallback">
          <b>这个文档暂时不能在窗口里直接预览</b>
          <p>后端还没有提供文件内联预览接口（<code>/files/preview</code>）。OSS 上的对象被强制以「下载」方式响应，
             浏览器无法内嵌显示纯文本内容。</p>
          <p>请先点下面的「新标签打开」或「下载」；后端补上该接口后这里就能直接看文档。</p>
        </div>
      </div>
      <div class="ml-bar">
        <span class="ml-name" :title="state.preview.name">{{ state.preview.name }}<i v-if="state.preview.size" class="ml-size"> · {{ state.preview.size }}</i></span>
        <span v-if="state.preview.kind === 'office'" class="ml-tip">Office 文档由微软在线预览，打不开请点「下载」</span>
        <a class="ml-btn" :href="state.preview.url" target="_blank" rel="noopener">新标签打开</a>
        <a class="ml-btn" :href="state.preview.url" :download="state.preview.name">下载</a>
        <button class="ml-btn primary" @click="closePreview">关闭（Esc）</button>
      </div>
    </div>
  </div>
</template>
