# 玉子市场 · TAMAKO MARKET

一个前后端分离的综合电商平台：**同一个账号既能买东西，也能开店卖东西**，覆盖
「商品浏览 → 下单支付 → 发货收货 → 评价售后」的完整交易链路，并带买卖双方一对一实时聊天。

## 仓库结构

前后端用**同一个远程仓库的两个分支**承载：

| 分支 | 对应工程 | 内容 |
| --- | --- | --- |
| `main` | 前端 | Vue 3 + Vite 单页应用（分支根目录即工程根目录） |
| `backend` | 后端 | Spring Boot + MyBatis 服务（分支根目录即工程根目录） |

> 两个分支各有一份本说明。前端代码在 `main`，后端代码在 `backend`。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 前端 | Vue 3.4 · Vite 5.4 · vue-router 4（Hash 路由）· Element Plus 2.7 · 原生 CSS 设计系统 |
| 后端 | Spring Boot 4.0 · Java 17 · MyBatis 4.0 · MySQL 8.0 · Redis · WebSocket |
| 鉴权 | JWT（HS256）+ 密码版本号校验；密码以 BCrypt 哈希存储 |
| 文件 | 阿里云 OSS（由后端转发上传，前端不持有凭证） |
| 部署 | 前端构建产物交 nginx 托管，`/api`、`/ws` 反代到后端 |

## 功能一览

### 买家端
- **商品**：首页推荐、限时秒杀、分类导航、搜索（综合 / 销量 / 价格排序与筛选）、
  商品详情（多规格款式、款式独立定价与配图、评价按款式筛选）
- **购物车与下单**：按店铺分组、跨店拆单预览、优惠券、收货地址、独立「确认订单」页与「收银台」页
- **订单**：订单列表与详情、支付、取消、物流轨迹、确认收货、提醒发货
- **评价与售后**：按订单条目（款式）评价、晒单图与追评；整单售后申请、凭证上传、撤销、寄回物流
- **其他**：收藏夹、浏览足迹、地址簿、优惠券领取、账户设置（资料 / 手机号绑定与换绑 / 修改密码）

### 卖家端
- **店铺**：开店、店铺资料维护、店铺主页
- **商品管理**：发布 / 编辑 / 删除 / 上下架、多规格与款式价
- **订单管理**：本店订单、发货
- **售后管理**：同意、拒绝（须写原因）、确认收货、回复买家
- **收入**：累计收入、已退款、累计到账，以及退款逐笔对账

### 通用
- **账号**：账号密码登录、手机号验证码登录、注册、忘记密码（短信验证码重置）；
  改密后旧令牌立即失效（密码版本号机制）
- **聊天**：一对一实时消息（文本 / 文件 / 图片 / 视频 / 音频）、商品卡片、
  未读与已读、在线状态三态（在线 / 离开 / 离线）

## 前端目录结构

```text
mall-web-vue/
├── index.html              # Vite 入口
├── vite.config.js          # dev 代理：/api、/ws → http://localhost:8080
├── 前端接口文档.md          # 前后端接口契约（含实现状态）
├── 前端页面说明.md          # 逐页说明：路由 / 视图 / 登录要求 / 数据来源
└── src/
    ├── main.js             # 应用入口
    ├── App.vue             # 页面骨架（顶栏 / 头部 / 导航 / 页脚）+ 启动逻辑
    ├── router/index.js     # Hash 路由
    ├── composables/        # useRouteCompat 等组合式工具
    ├── core/               # api / store / ui / config / jwt / chatSocket 等核心模块
    ├── assets/css/         # 全站样式（设计令牌 + 分页样式）
    └── views/              # 页面组件
```

## 快速开始

### 环境要求

- JDK 17+、Maven 3.9+
- Node.js 18+（推荐 20+）
- MySQL 8.0、Redis

### 1. 初始化数据库

从 `backend` 分支拿到建库脚本后执行（脚本只含表结构，共 21 张表，不含数据）：

```bash
mysql -uroot -p --default-character-set=utf8mb4 < 网页通信_建库脚本.sql
```

### 2. 启动后端

```bash
cd web03
# 仓库中的 application.yml 已把数据库与 OSS 配置全部置空，运行前必须填写：
#   spring.datasource.url / username / password
#   aliyun.oss.endpoint / bucketName / region / accessKeyId / accessKeySecret
mvn spring-boot:run          # 默认监听 8080
```

### 3. 启动前端

```bash
cd mall-web-vue
npm install
npm run dev                  # 默认 5173，/api 与 /ws 由 Vite 代理到 8080
```

浏览器打开 <http://localhost:5173> 即可。

### 生产构建

```bash
npm run build                # 产物在 dist/，交给 nginx 托管
```

## 项目文档

| 文档 | 位置 | 内容 |
| --- | --- | --- |
| 前端接口文档 | `main` 分支根目录 | 前后端 HTTP / WebSocket 接口契约、鉴权与响应码约定、各模块接口明细与实现状态 |
| 前端页面说明 | `main` 分支根目录 | 逐页说明：路由、视图文件、是否需要登录、数据来源与主要区块 |
| 建库脚本 | `backend` 分支根目录 | MySQL 8.0 建库建表脚本（21 张业务表，含索引与字段注释） |

## 部署与安全说明

- **不含任何真实凭据**：`application.yml` 中的数据库连接与阿里云 OSS 字段全部为空，
  前端 `src/core/config.js` 的 `JWT_SECRET` 也已置空，仓库里没有可直接使用的密钥。
- **JWT 密钥**：前端不持有签名密钥 —— 令牌签名校验完全由后端完成，前端只校验令牌结构与有效期。
  后端签名密钥优先读取环境变量 `JWT_SECRET`，未设置时使用内置开发默认值，**部署环境请务必注入自己的密钥**。
- **数据库配置**：除直接写入 `application.yml` 外，也可用 Spring Boot 标准环境变量注入（优先级更高）：
  `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`。
- **OSS 凭证**：需在 `application.yml` 的 `aliyun.oss.accessKeyId` / `accessKeySecret` 中填写；
  缺失时上传接口会返回明确提示，而不是 SDK 的空指针异常。
- **上传限制**：单文件上限 1 GB（`application.yml` 配置），经 nginx 时还需同步调整
  `client_max_body_size`。
