/* =========================================================
   青集市 · core/oss.js —— 阿里云 OSS 文件直传模块
   · 流程：向后端申请 STS 临时凭证（GET /oss/sts，需登录态）
           → 在浏览器端用临时凭证把文件直传到 OSS
           → 返回文件在 OSS 的完整访问地址（由上层提交给后端落库）
   · 安全：AccessKey 只存在于后端，前端拿到的是 STS 临时凭证（短期有效、
           可限定最小权限），不会泄露主账号密钥。
   ========================================================= */
import OSS from 'ali-oss';
import QM_CFG from './config.js';
import QM_STORE from './store.js';

/** 客户端文件大小上限（字节）：与 config.js 的 UPLOAD_MAX_SIZE、后端 multipart 限制保持一致，超限提前拦截 */
export const OSS_MAX_SIZE = QM_CFG.UPLOAD_MAX_SIZE || 1024 * 1024 * 1024;

const tokenOf = () => (QM_STORE.state.user && QM_STORE.state.user.token) || '';

/**
 * 向后端申请 OSS 上传凭证。
 * 后端返回 Result 结构：{ code:1, data:{ accessKeyId, accessKeySecret, securityToken,
 *   expiration, region, bucket, endpoint? } }（实现见 docs/阿里云OSS文件直传-实现教程.md）
 */
async function requestSts() {
  const base = String(QM_CFG.API_BASE).replace(/\/+$/, '');
  const token = tokenOf();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), QM_CFG.TIMEOUT);
  let res;
  try {
    res = await fetch(base + '/oss/sts', {
      method: 'GET',
      headers: token ? { Authorization: 'Bearer ' + token } : {},
      signal: controller.signal
    });
  } catch (e) {
    throw new Error('无法连接后端服务，无法获取 OSS 上传凭证');
  } finally {
    clearTimeout(timer);
  }
  let json = null;
  try { json = await res.json(); } catch (e) { /* 非 JSON 响应 */ }

  /* 后端尚未实现 /oss/sts：给出明确指引而不是让人误以为是网络问题 */
  if (res.status === 404 || res.status === 501 || (res.ok && json === null)) {
    throw new Error('后端尚未实现 /oss/sts 接口，请先按教程完成 OSS 直传凭证接口');
  }
  if (!res.ok) {
    throw new Error((json && json.msg) || ('获取 OSS 上传凭证失败（HTTP ' + res.status + '）'));
  }
  if (json && json.code === 0) {
    throw new Error(json.msg || '获取 OSS 上传凭证失败');
  }
  const data = (json && json.data) || {};
  if (!data.accessKeyId || !data.accessKeySecret || !data.securityToken || !data.bucket || !data.region) {
    throw new Error('后端 /oss/sts 返回数据不完整（缺 accessKeyId / securityToken / bucket / region），请检查接口实现');
  }
  return data;
}

/** 生成 OSS 对象名：chat/yyyy/MM/随机名.ext（目录风格与后端 AliyunOSSOperator 一致，便于统一管理/清理） */
export function buildObjectName(file) {
  const d = new Date();
  const pad = n => String(n).padStart(2, '0');
  const dir = d.getFullYear() + '/' + pad(d.getMonth() + 1);
  const extMatch = /(\.[^.\\/]+)$/.exec(file.name || '');
  const ext = extMatch ? extMatch[1] : '';
  const rand = Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10);
  return 'chat/' + dir + '/' + rand + ext;
}

/**
 * 把文件直传到 OSS。
 * @returns {Promise<{url: string, objectName: string}>}
 *   url —— 文件在 OSS 的完整访问地址（Bucket 公共读时可直接在浏览器打开/下载），交给后端存入数据库。
 */
export async function uploadToOss(file) {
  if (!file) throw new Error('文件不能为空');
  if (file.size > OSS_MAX_SIZE) {
    const fmt = (b) => b >= 1024*1024*1024 ? (b/1024/1024/1024).toFixed(1)+'GB' : (b/1024/1024).toFixed(1)+'MB';
    throw new Error('文件不能超过 ' + fmt(OSS_MAX_SIZE));
  }
  const sts = await requestSts();
  const objectName = buildObjectName(file);

  let client;
  try {
    client = new OSS({
      region: sts.region,
      accessKeyId: sts.accessKeyId,
      accessKeySecret: sts.accessKeySecret,
      stsToken: sts.securityToken,
      bucket: sts.bucket,
      secure: true,
      /* 有自定义 endpoint（如自定义域名）时优先使用；缺省时 SDK 按 region 自动拼 */
      endpoint: sts.endpoint ? sts.endpoint.replace(/\/+$/, '') : undefined
    });
  } catch (e) {
    throw new Error('OSS 客户端初始化失败：' + ((e && e.message) || e));
  }

  let result;
  try {
    result = await client.put(objectName, file);
  } catch (e) {
    const code = e && e.code;
    if (code === 'AccessDenied' || /AccessDenied/.test((e && e.message) || '')) {
      throw new Error('OSS 上传被拒绝：请检查 STS 权限策略是否包含 oss:PutObject，以及 Bucket 跨域（CORS）设置');
    }
    if (code === 'SecurityTokenExpired') throw new Error('OSS 临时凭证已过期，请重试');
    throw new Error('OSS 上传失败：' + ((e && e.message) || e));
  }

  const url = (result && result.url)
    || (sts.endpoint ? sts.endpoint.replace(/\/+$/, '') + '/' + objectName : '')
    || (sts.bucket && sts.region ? 'https://' + sts.bucket + '.oss-' + sts.region + '.aliyuncs.com/' + objectName : '');
  if (!url) throw new Error('OSS 上传成功但未能获取文件地址');
  return { url, objectName };
}

export default { uploadToOss, buildObjectName, OSS_MAX_SIZE };
