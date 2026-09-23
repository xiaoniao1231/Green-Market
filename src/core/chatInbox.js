/* =========================================================
   core/chatInbox.js —— 全局聊天收件箱（会话未读同步 + 实时消息落库）
   ---------------------------------------------------------
   修复背景：原先「会话未读数同步」与「实时消息未读累加」都只写在 ChatView 内部：
   · 登录后不进消息中心 → 从不调用 /messages/conversations → 顶部「消息中心」角标
     永远是 0，首页会员卡的「消息」入口也没有任何未读提示；
   · 停留在首页 / 商品页时收到消息 → 没有页面累加未读，角标不涨。
   本模块与 chatSocket.js 同一思路，把「数据落库」提升到应用级（App.vue 驱动），
   ChatView 负责页面内的渲染与已读上报：

   · syncConversations()：服务端会话列表 → 本地（未读数 / 会话入口 / 最后一条预览）
   · handleFrame(frame) ：实时帧兜底落库。消息中心页挂载时由 ChatView 自行处理
     （它要一边落库一边重绘），此时本函数直接返回，避免同一条消息被计两次未读。
   ========================================================= */
import QM_API from './api.js';
import QM_STORE from './store.js';

/* 当前打开的会话：由 ChatView 在 openPeer / 离开页面时同步。
   正在看的会话不累加未读（打开即已读） */
let activePeer = null;
/* 消息中心页是否挂载中（挂载时实时帧由该页处理并渲染） */
let pageActive = false;

function setActivePeer(id) { activePeer = id || null; }
function setPageActive(on) { pageActive = !!on; }

/* 与 ChatView 的 fmtSize 同一口径，保证会话预览文案一致 */
function fmtSize(bytes) {
  if (!bytes && bytes !== 0) return '';
  if (bytes < 1024) return bytes + 'B';
  if (bytes < 1024 * 1024) return Math.ceil(bytes / 1024) + 'KB';
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + 'MB';
  return (bytes / 1024 / 1024 / 1024).toFixed(1) + 'GB';
}

/**
 * 拉取服务端会话列表并写入本地 store（含未读数）。
 * 会话列表以服务端为准：先清掉服务端没有的会话入口与本地记录（如仅点了「联系卖家」
 * 从未发消息、或旧版本残留），再写未读数与最后一条消息预览。
 * @returns {Promise<boolean>} 是否同步成功
 */
async function syncConversations() {
  const convs = await QM_API.chat.conversations();
  if (!Array.isArray(convs)) return false;

  const peerIds = new Set(convs.map(c => c && c.peerId).filter(Boolean));
  QM_STORE.state.contacts = QM_STORE.state.contacts.filter(c => c && peerIds.has(c.id));
  Object.keys(QM_STORE.state.chats).forEach(k => { if (!peerIds.has(k)) delete QM_STORE.state.chats[k]; });

  if (!convs.length) {
    QM_STORE.saveNow();
    QM_STORE.emit('chat');
    return true;
  }

  const me = QM_STORE.state.user ? QM_STORE.state.user.userId : 'me';
  convs.forEach(conv => {
    const peerId = conv.peerId;
    if (!peerId) return;
    const contact = QM_STORE.chat.ensureContact(peerId, conv.peerName || peerId);
    /* 服务端未读数 → 本地会话角标（打开会话时 markRead 清零） */
    contact.unread = conv.unreadCount || 0;
    const msgs = QM_STORE.chat.messages(peerId);
    msgs.forEach(m => { m.unread = false; });   // 清掉历史遗留的消息级标记，避免重复计数
    if (msgs.length) return;                    // 本地已有历史，不覆盖
    if (conv.lastContent == null) return;
    const ts = conv.lastTime ? Date.parse(String(conv.lastTime).replace(' ', 'T')) : NaN;
    const preview = {
      from: conv.lastSenderId === me ? 'me' : peerId,
      type: conv.lastRecalled ? 'sys' : 'text',
      content: conv.lastRecalled ? '对方撤回了一条消息' : (conv.lastContent || ''),
      time: Number.isNaN(ts) ? Date.now() : ts
    };
    if (conv.lastMsgId) preview.id = conv.lastMsgId;  // 带 msgId 便于历史合并去重
    QM_STORE.chat.push(peerId, preview);
  });

  QM_STORE.saveNow();
  QM_STORE.emit('chat');   // 刷新顶部「消息中心」/ 首页会员卡的未读角标
  return true;
}

/**
 * 实时帧兜底落库（仅在消息中心页未挂载时生效）。
 * ChatView 挂载时由它处理同一帧并负责重绘，这里直接返回，避免重复计数。
 */
function handleFrame(payload) {
  if (!payload || pageActive) return;
  const type = payload.type;
  if (type === 'PONG') return;

  const msg = payload.message || {};
  if (type === 'PRESENCE') {
    /* 服务端推送全量状态快照（在线 / 离开 / 离线）：覆盖式更新联系人状态。
       applyPresence 内部会 emit('chat')，各页角标随之刷新 */
    QM_STORE.chat.applyPresence(msg);
    return;
  }
  if (!['COMM_MES', 'TO_ALL', 'FILE_MES'].includes(type)) return;

  const myId = QM_STORE.state.user ? QM_STORE.state.user.userId : '';
  const peerId = msg.senderId === myId ? msg.receiverId : msg.senderId;
  if (!peerId) return;

  QM_STORE.chat.ensureContact(peerId, msg.senderNickname || peerId, { online: true });
  const contact = QM_STORE.chat.contacts().find(c => c.id === peerId);
  /* 对方发来、且不是当前正在看的会话 → 未读 +1（角标随之刷新） */
  if (msg.senderId !== myId && activePeer !== peerId && contact) {
    contact.unread = (contact.unread || 0) + 1;
  }

  const ts = msg.sendTime ? new Date(String(msg.sendTime).replace(' ', 'T')).getTime() : Date.now();
  const name = msg.fileName || msg.content || '文件';
  const live = {
    from: msg.senderId === myId ? 'me' : peerId,
    type: msg.fileUrl ? 'file' : 'text',
    content: msg.fileUrl ? `[文件] ${name}（${fmtSize(msg.fileSize) || '0KB'}）` : (msg.content || msg.fileName || ''),
    time: ts
  };
  if (msg.fileUrl) { live.name = name; live.size = msg.fileSize; live.url = msg.fileUrl; }
  if (msg.msgId) live.id = msg.msgId;
  QM_STORE.chat.push(peerId, live);   // push 内部 emit('chat') → 各页角标刷新
}

export default {
  syncConversations,
  handleFrame,
  setActivePeer,
  setPageActive,
  isPageActive: () => pageActive
};
