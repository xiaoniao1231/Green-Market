/* =========================================================
   玉子市场 · core/config.js —— 全局配置
   · 前后端分离：默认 API 基址为相对路径 /api（由 nginx 反代到
     Spring Boot 8080；npm run dev 时由 Vite 代理转发）。
   · 可在右下角「⚙ 接口调试面板」修改并保存其他后端地址（如
     http://localhost:8080，直连后端时使用）。
   接口调试请使用 Apifox：导入项目 apifox/ 目录下的 OpenAPI 文件。
   ========================================================= */

/* 存储后端与 store.js 保持一致：优先 sessionStorage（按标签页隔离，可同时登录两个账号）。
   浏览器禁用存储时（隐私模式等）setItem/getItem 会抛异常，
   这里统一探针 + try/catch 兜底，否则 http() 每次读 API_BASE 都会把全站请求打挂。 */
const storage = (() => {
  try {
    window.sessionStorage.setItem('__qm_cfg_probe__', '1');
    window.sessionStorage.removeItem('__qm_cfg_probe__');
    return window.sessionStorage;
  } catch (e) {
    try {
      window.localStorage.setItem('__qm_cfg_probe__', '1');
      window.localStorage.removeItem('__qm_cfg_probe__');
      return window.localStorage;
    } catch (e2) {
      return null; // 存储完全不可用：读写全部走内存兜底
    }
  }
})();

const memoryStore = new Map();
function readKey(key) {
  if (!storage) return memoryStore.has(key) ? memoryStore.get(key) : null;
  try { return storage.getItem(key); } catch (e) { return null; }
}
function writeKey(key, value) {
  if (!storage) { memoryStore.set(key, value); return; }
  try { storage.setItem(key, value); } catch (e) { memoryStore.set(key, value); }
}

const QM_CFG = {
  /** 后端 API 基础地址：默认 /api（同源，经 nginx/Vite 反代到 8080） */
  get API_BASE() { return readKey('qm_v2_api_base') || '/api'; },
  set API_BASE(url) { writeKey('qm_v2_api_base', url); },
  /** 单次 HTTP 请求超时（毫秒），超时或后端不可用时由页面显示空态 / 错误提示 */
  TIMEOUT: 2500,
  /** 文件上传专用超时（毫秒）：上传体积大、耗时长，不能用 2.5 秒的普通请求超时 */
  UPLOAD_TIMEOUT: 120000,
  /**
   * 文件大小上限（字节）：前端提前拦截，避免白传一趟。
   * 后端 multipart 限制为 1GB（application.yml），经 nginx 访问时还受
   * nginx.conf 的 client_max_body_size 限制，调大文件上限时三处要同步。
   */
  UPLOAD_MAX_SIZE: 1024 * 1024 * 1024,
  /** 后端不可用时是否允许本地离线回退（仅订单的本地存储实现；
      购物车 / 收藏 / 地址等服务端数据一律 strict，接口失败时如实报错，不做本地假成功） */
  FALLBACK_MOCK: true,
  /** 本地存储统一前缀 */
  KEY: 'qm_v2_',
  /** 聊天 WebSocket 路径 */
  WS_PATH: '/ws',
  /**
   * JWT 签名校验密钥（base64 字符串），须与后端 JwtUtils.SECRET_KEY 保持一致。
   * - 配置后：登录 / 启动时会对后端返回的 JWT 做 HS256 签名校验（依赖 Web Crypto）；
   * - 置空 ''：仅做「JWT 结构 + exp 有效期」校验，不做签名校验。
   * 这里默认置空：前端产物是公开的，不应携带签名密钥，令牌签名校验统一由后端完成。
   * 本地开发若确需前端自校验，可临时填入与后端一致的密钥，但不要提交到仓库。
   */
  JWT_SECRET: '',
  /** 登录态守卫检查间隔（毫秒）：本地保存的令牌缺失 / 失效 / 过期时自动登出并提示重新登录 */
  TOKEN_CHECK_INTERVAL: 60 * 1000
};

export default QM_CFG;
