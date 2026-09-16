# 青集市前端（mall-web-vue · Vue3 + Vite + Element Plus）

与课程原版（纯 HTML/CSS/JS 静态版）**页面外观与功能一致**的 Vue3 重实现，配套 **前后端分离部署**：

- 前端不再打在后端 jar 里，而是由 **独立 nginx**（`../nginx-1.22.0`，端口 90）托管构建产物；
- 所有后端请求走 **同源相对路径 `/api`**，由 nginx 反代到 Spring Boot（`web03`，端口 8080）；
- 聊天 WebSocket 走 **`/ws`**，由 nginx 升级协议头后反代到 8080；
- 架构与参考站（`D:\java\javaWeb01\nginx-1.22.0-web`，Vue 构建产物 + nginx `/api` 反代）同构。

## 一、目录结构

```text
mall-web-vue/
├── index.html            # Vite 入口（同原版页面标题/描述）
├── package.json          # vue3 / vue-router4 / element-plus / vite5
├── vite.config.js        # dev 代理：/api、/ws → http://localhost:8080
├── docs/页面移植规范.md    # 页面移植约定
└── src/
    ├── main.js           # 应用入口（挂载 Element Plus + 全部页面样式）
    ├── App.vue           # 页面骨架（顶栏/头部/导航/页脚/弹窗挂载点）+ 启动逻辑
    ├── router/index.js   # Hash 路由（与原版 #/home #/detail/p01 … 一致）
    ├── composables/useRouteCompat.js  # 原版 route {name,params,query} 兼容对象
    ├── core/             # 原 js/ 目录的 ES 模块版（逻辑与原版一致）
    │   ├── config.js     # API_BASE 默认 '/api'（可调，存 localStorage）
    │   ├── jwt.js  mock.js  api.js  store.js  ui.js  viewRefresh.js
    ├── assets/css/       # 原版六份 CSS 原样引入（视觉不变）
    └── views/            # 10 个页面组件（与原 js/pages 一一对应）
```

## 二、本地开发（前后端分离模式）

```bat
cd /d D:\java\KnockoffQQ03\mall-web-vue
npm install
npm run dev          # http://localhost:5173
```

开发服务器已内置代理：`/api`、`/ws` 自动转发到 `http://localhost:8080`，
因此前端代码无需写死后端地址（相对路径 `/api`，无跨域问题）。

## 三、生产部署（nginx 前后端分离）

```bat
:: 方式一：一键构建 + 部署
cd /d D:\java\KnockoffQQ03
build-and-deploy.bat

:: 方式二：手动
cd mall-web-vue
npm run build                    # 产物 dist/
```

然后：

1. 后端：IDEA 运行 `web03`（Spring Boot，8080，需本地 MySQL：库 `网页通信`，root/123456）。
2. 前端：双击 `D:\java\KnockoffQQ03\nginx-1.22.0\start-nginx.bat`，访问 **http://localhost:90**。
3. 停止/重载：`stop-nginx.bat` / `reload-nginx.bat`。

> 端口冲突：参考站 nginx（javaWeb01）与本项目 nginx 同为 90 端口，只能同时运行一个。
> 运行本项目前请先停止参考站：在 `D:\java\javaWeb01\nginx-1.22.0-web\nginx-1.22.0` 执行 `nginx.exe -s stop`。

## 四、与后端的接口约定

与原版完全一致，见 `../docs/前端接口文档.md`。要点：

- 登录：`POST /login`（web03 已实现，返回 JWT + 用户信息）；
- 聊天/好友/消息/文件：契约已定义（`strict` 接口，后端落地前会提示后端错误）；
- 商城接口：预留，前端自动回退本地演示数据；
- 响应约定：`{ code: 1 成功 / 0 失败, msg, data }`，鉴权头 `Authorization: Bearer <token>`。
