/* =========================================================
   青集市 · core/jwt.js —— 前端 JWT 令牌校验（纯本地实现，无第三方依赖）
   · 与后端 JwtUtils 约定一致：HS256 签名、base64 密钥、载荷含 exp（秒）。
   · 提供两级校验：
       check(token)            同步校验：JWT 三段式结构、JSON 可解析、exp 未过期；
       verify(token, secret)   异步校验：在 check 基础上，若配置了 secret 且浏览器
                               支持 Web Crypto（安全上下文），额外做 HS256 签名校验，
                               防止本地令牌被篡改。
   · 安全提示：真正的签名校验应由后端完成，前端密钥仅用于课程演示环境的自校验；
     生产环境请将 QM_CFG.JWT_SECRET 置空（关闭本地签名校验）或改用其他方案。
   ========================================================= */

/* 允许的时钟误差（毫秒）：容忍客户端与服务器时间轻微不一致 */
const CLOCK_SKEW = 60 * 1000;

/* ---------- base64 / base64url 编解码 ---------- */
function bytesToBase64(bytes) {
  let bin = '';
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin);
}

function bytesToBase64Url(bytes) {
  return bytesToBase64(bytes).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function base64UrlToBytes(text) {
  let b64 = String(text).replace(/-/g, '+').replace(/_/g, '/');
  while (b64.length % 4) b64 += '=';
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

function base64ToBytes(text) {
  let b64 = String(text).replace(/\s+/g, '');
  while (b64.length % 4) b64 += '=';
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

function utf8Bytes(text) { return new TextEncoder().encode(text); }

/* 判断字符串是否为 base64：仅含合法字符且补齐后长度可被 4 整除 */
function looksLikeBase64(text) {
  return /^[A-Za-z0-9+/]*={0,2}$/.test(text) && text.length % 4 === 0;
}

/* 常量时间比较，避免简单的时序差异 */
function safeEqual(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

/* ---------- JWT 解析 ---------- */
/** 解析三段式 JWT。失败时抛出带 reason 属性的 Error。 */
function parse(token) {
  if (typeof token !== 'string' || !token.trim()) {
    const e0 = new Error('令牌缺失');
    e0.reason = '令牌缺失';
    throw e0;
  }
  const parts = token.split('.');
  if (parts.length !== 3) {
    const e1 = new Error('令牌格式不正确，应为三段式 JWT（header.payload.signature）');
    e1.reason = e1.message;
    throw e1;
  }
  let header, payload;
  try { header = JSON.parse(new TextDecoder('utf-8').decode(base64UrlToBytes(parts[0]))); }
  catch (err) {
    const e2 = new Error('令牌头部(header)解析失败');
    e2.reason = e2.message;
    throw e2;
  }
  try { payload = JSON.parse(new TextDecoder('utf-8').decode(base64UrlToBytes(parts[1]))); }
  catch (err) {
    const e3 = new Error('令牌载荷(payload)解析失败');
    e3.reason = e3.message;
    throw e3;
  }
  return { header: header, payload: payload, signature: parts[2], signingInput: parts[0] + '.' + parts[1] };
}

/** 仅解码并返回载荷（失败返回 null，不抛异常） */
function payloadOf(token) {
  try { return parse(token).payload; } catch (e) { return null; }
}

/** 返回载荷中的过期时间戳（毫秒），无 exp 返回 null */
function expiryMsOf(token) {
  const p = payloadOf(token);
  if (!p || typeof p.exp !== 'number' || !isFinite(p.exp)) return null;
  return p.exp * 1000;
}

function okResult(parsed) {
  const expMs = (typeof parsed.payload.exp === 'number' && isFinite(parsed.payload.exp)) ? parsed.payload.exp * 1000 : null;
  return {
    ok: true,
    reason: '',
    header: parsed.header,
    payload: parsed.payload,
    signature: parsed.signature,
    signingInput: parsed.signingInput,
    expMs: expMs,
    expired: expMs !== null && expMs + CLOCK_SKEW <= Date.now(),
    expiresInMs: expMs === null ? null : Math.max(0, expMs - Date.now()),
    signatureChecked: false
  };
}

function failResult(reason) {
  return { ok: false, reason: reason, header: null, payload: null, signature: '', signingInput: '', expMs: null, expired: false, expiresInMs: null, signatureChecked: false };
}

/** 同步校验：结构 + 载荷 JSON + exp 有效期。结果形如 { ok, reason, header, payload, ... } */
function check(token) {
  let parsed;
  try { parsed = parse(token); } catch (e) { return failResult(e.reason || '令牌解析失败'); }
  const payload = parsed.payload;
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) return failResult('令牌载荷格式不正确');
  const expMs = (typeof payload.exp === 'number' && isFinite(payload.exp)) ? payload.exp * 1000 : null;
  if (expMs === null) return failResult('令牌载荷缺少有效期(exp)，无法校验令牌是否过期');
  const expired = expMs + CLOCK_SKEW <= Date.now();
  if (expired) return failResult('令牌已过期，请重新登录');
  return okResult(parsed);
}

/**
 * 异步校验：check() + 可选 HS256 签名校验。
 * @param {string} token  待校验的 JWT 字符串
 * @param {string} [secret] base64 密钥字符串（与后端 JwtUtils.SECRET_KEY 一致）；
 *                          空字符串 / 未传 或 非安全上下文（crypto.subtle 不可用）时跳过签名校验。
 * @returns {Promise<{ok:boolean, reason:string, ...}>}
 */
async function verify(token, secret) {
  const verdict = check(token);
  if (!verdict.ok) return verdict;

  if (!secret) return Object.assign(verdict, { signatureChecked: false });

  // 非安全上下文（如部分环境下的 file://）无法使用 crypto.subtle → 跳过签名校验
  if (typeof crypto === 'undefined' || !crypto.subtle || !crypto.subtle.importKey) {
    return Object.assign(verdict, { signatureChecked: false });
  }

  const keyBytes = looksLikeBase64(String(secret)) ? base64ToBytes(String(secret)) : utf8Bytes(String(secret));
  try {
    const key = await crypto.subtle.importKey('raw', keyBytes, { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
    const mac = await crypto.subtle.sign('HMAC', key, utf8Bytes(verdict.signingInput));
    const expected = bytesToBase64Url(new Uint8Array(mac));
    const pass = safeEqual(expected, verdict.signature);
    return Object.assign(verdict, {
      signatureChecked: true,
      ok: pass,
      reason: pass ? '' : '令牌签名校验失败（token 可能被篡改，或前端密钥与后端不一致）'
    });
  } catch (e) {
    return Object.assign(verdict, { signatureChecked: true, ok: false, reason: '令牌签名校验异常：' + (e && e.message ? e.message : e) });
  }
}

const QM_JWT = {
  parse: parse,
  check: check,
  verify: verify,
  payloadOf: payloadOf,
  expiryMsOf: expiryMsOf,
  /* 供调试面板 / 演示使用 */
  b64url: { encode: bytesToBase64Url, decode: base64UrlToBytes }
};

export default QM_JWT;
