-- ==============================================================
--  建库脚本：网页通信
--  源库环境：MySQL 8.0.45 / utf8mb4 / utf8mb4_0900_ai_ci
--  生成时间：2026-09-30 22:59:58
--  脚本内容：21 张业务表的表结构（建库 + 建表 + 索引 + 注释），不含数据
--  说明：源库无视图、存储过程、触发器、事件与外键约束
--  执行方式：mysql -uroot -p --default-character-set=utf8mb4 < 本文件
-- ==============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `网页通信`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `网页通信`;

-- --------------------------------------------------------------
--  account_logs  账户敏感操作日志（绑定手机号 / 换绑 / 改密 / 改资料留痕，append-only）
-- --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `account_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` varchar(15) NOT NULL COMMENT '操作账号',
  `action` varchar(32) NOT NULL COMMENT '动作：bind_phone 绑定手机号 / change_phone 换绑手机号 / change_password 修改密码 / update_profile 更新资料',
  `detail` varchar(200) NOT NULL DEFAULT '' COMMENT '操作摘要',
  `ip` varchar(45) NULL COMMENT '客户端 IP',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  KEY `idx_user_time` (`user_id`, `created_at`),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='账户敏感操作日志（绑定手机号 / 换绑 / 改密 / 改资料留痕，append-only）';

-- --------------------------------------------------------------
--  addresses  收货地址表
-- --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `addresses` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '地址ID',
  `user_id` varchar(15) NOT NULL COMMENT '归属用户账号',
  `name` varchar(20) NOT NULL COMMENT '收货人姓名',
  `phone` varchar(11) NOT NULL COMMENT '收货手机号',
  `region` varchar(100) NOT NULL COMMENT '所在地区（省/市/区）',
  `detail` varchar(120) NOT NULL COMMENT '详细地址',
  `is_default` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否默认地址：1 是 / 0 否（同一 user_id 至多一条 1）',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tag` varchar(6) NOT NULL COMMENT '地址标签',
  KEY `idx_user` (`user_id`, `is_default`),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收货地址表';

-- --------------------------------------------------------------
--  after_sale_items  售后单商品明细
-- --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `after_sale_items` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `after_sale_id` int NOT NULL COMMENT '售后单ID',
  `order_item_id` int NULL COMMENT '订单条目ID',
  `product_id` int NOT NULL COMMENT '商品ID',
  `title` varchar(120) NOT NULL COMMENT '商品标题快照',
  `sku` varchar(500) NOT NULL DEFAULT '默认' COMMENT '款式文本快照',
  `art_img` varchar(500) NULL COMMENT '款式图 OSS 地址快照',
  `qty` int NOT NULL DEFAULT 1 COMMENT '本件售后数量',
  `price` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '下单单价快照',
  `refund_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '本件退款金额 = price × qty',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY `idx_after` (`after_sale_id`),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='售后单商品明细';

-- --------------------------------------------------------------
--  after_sale_logs  售后处理记录
-- --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `after_sale_logs` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `after_sale_id` int NOT NULL COMMENT '售后单ID',
  `role` varchar(10) NOT NULL COMMENT '操作方：buyer 买家 / seller 卖家 / system 系统',
  `action` varchar(20) NOT NULL COMMENT '动作：apply 申请 / approve 同意 / refuse 拒绝 / ship 买家寄回 / receive 商家确认收货 / cancel 买家撤销',
  `actor_id` varchar(32) NOT NULL DEFAULT '' COMMENT '操作账号',
  `content` varchar(2000) NOT NULL DEFAULT '',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  KEY `idx_after` (`after_sale_id`, `created_at`),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='售后处理记录';

-- --------------------------------------------------------------
--  after_sales  售后单表
-- --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `after_sales` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '售后单ID',
  `after_no` varchar(32) NOT NULL COMMENT '售后单号',
  `order_id` int NOT NULL COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单号快照',
  `order_item_id` int NOT NULL COMMENT '订单条目ID',
  `user_id` varchar(15) NOT NULL COMMENT '买家账号',
  `shop_id` varchar(32) NOT NULL COMMENT '商品所属店铺快照',
  `product_id` int NOT NULL COMMENT '商品ID',
  `title` varchar(120) NOT NULL COMMENT '商品标题快照',
  `sku` varchar(500) NOT NULL DEFAULT '默认',
  `art_img` varchar(500) NULL COMMENT '款式图 OSS 地址快照',
  `qty` int NOT NULL DEFAULT 1 COMMENT '售后件数',
  `price` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '该款式下单时单价快照',
  `refund_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '退款金额 = price × qty',
  `type` varchar(12) NOT NULL COMMENT '售后类型：refund 仅退款 / return 退货退款 / exchange 换货',
  `reason` varchar(100) NOT NULL,
  `description` varchar(2000) NOT NULL DEFAULT '',
  `images` text NULL COMMENT '凭证图 JSON 数组：["https://oss.../1.jpg","..."]',
  `status` varchar(12) NOT NULL DEFAULT 'pending' COMMENT '售后状态：pending 待商家处理 / agreed 待买家寄回 / returned 待商家收货 / refunded 已退款 / exchanged 换货完成 / refused 商家已拒绝 / canceled 买家已撤销',
  `return_address` varchar(200) NULL COMMENT '商家同意退货时给出的寄回地址',
  `refuse_reason` varchar(2000) NULL,
  `seller_remark` varchar(1000) NULL,
  `buyer_company` varchar(50) NULL COMMENT '买家寄回承运商',
  `buyer_tracking_no` varchar(50) NULL COMMENT '买家寄回运单号',
  `buyer_ship_time` datetime NULL COMMENT '买家寄回时间',
  `reship_company` varchar(50) NULL COMMENT '换货重发承运商',
  `reship_no` varchar(50) NULL COMMENT '换货重发运单号',
  `reship_time` datetime NULL COMMENT '换货重发时间',
  `finish_time` datetime NULL COMMENT '售后结束时间',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY `idx_order` (`order_id`),
  KEY `idx_order_item` (`order_item_id`),
  KEY `idx_shop_status` (`shop_id`, `status`, `created_at`),
  KEY `idx_user_status` (`user_id`, `status`, `created_at`),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_after_no` (`after_no`),
  UNIQUE KEY `uk_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='售后单表';

-- --------------------------------------------------------------
--  cart_items  购物车表
-- --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `cart_items` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '购物车条目ID',
  `user_id` varchar(15) NOT NULL COMMENT '归属用户账号',
  `product_id` int NOT NULL COMMENT '商品ID',
  `sku` varchar(100) NOT NULL DEFAULT '默认' COMMENT '规格文本',
  `quantity` int NOT NULL DEFAULT 1 COMMENT '数量',
  `price` decimal(10,2) NULL COMMENT '加购时选中款式的成交价快照',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY `idx_user` (`user_id`),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product_sku` (`user_id`, `product_id`, `sku`)
) ENGINE=InnoDB AUTO_INCREMENT=167 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车表';

-- --------------------------------------------------------------
--  coupons  优惠券模板
-- --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `coupons` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '券模板ID',
  `title` varchar(50) NOT NULL COMMENT '券名称',
  `threshold` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '使用门槛：整单商品金额达到该值才可用',
  `amount` decimal(10,2) NOT NULL COMMENT '抵扣金额',
  `expire` varchar(20) NULL COMMENT '有效期',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否可发放：1 启用 / 0 停用',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券模板';

-- --------------------------------------------------------------
--  favorites  收藏夹表
-- --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `favorites` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '收藏记录ID',
  `user_id` varchar(15) NOT NULL COMMENT '归属用户账号',
  `product_id` int NOT NULL COMMENT '商品ID',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  KEY `idx_user` (`user_id`),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`)
) ENGINE=InnoDB AUTO_INCREMENT=45 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏夹表';

-- --------------------------------------------------------------
--  flash_usage
-- --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `flash_usage` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` varchar(15) NOT NULL,
  `product_id` int NOT NULL,
  `flash_day` date NOT NULL,
  `order_id` int NULL,
  `status` varchar(12) NOT NULL DEFAULT 'used',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_flash_order` (`order_id`),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flash_user_product_day` (`user_id`, `product_id`, `flash_day`)
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- --------------------------------------------------------------
--  footprints  浏览足迹表
-- --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `footprints` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '足迹ID',
  `user_id` varchar(15) NOT NULL COMMENT '归属账号',
  `product_id` int NOT NULL COMMENT '商品ID',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次浏览时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近浏览时间',
  KEY `idx_user_time` (`user_id`, `updated_at`),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`)
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览足迹表';

-- --------------------------------------------------------------
--  messages  消息表
-- --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `messages` (
  `id` int NOT NULL AUTO_INCREMENT,
  `msg_id` varchar(100) NOT NULL COMMENT '消息唯一ID',
  `sender_id` varchar(15) NOT NULL COMMENT '发送者账号（users.user_id）',
  `receiver_id` varchar(15) NULL COMMENT '接收者账号（users.user_id；群发为 ALL）',
  `msg_type` varchar(10) NOT NULL COMMENT '消息类型：COMM_MES 私聊 / TO_ALL 群发 / FILE_MES 文件',
  `content` text NULL COMMENT '文本内容或文件名',
  `file_url` varchar(500) NULL COMMENT '文件在阿里云 OSS 的访问地址（FILE_MES 消息）',
  `file_size` bigint NULL COMMENT '文件大小（字节）',
  `send_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  `is_recalled` tinyint NULL DEFAULT 0 COMMENT '是否已撤回，0-未撤回，1-已撤回',
  `biz_json` mediumtext NULL COMMENT '业务消息快照（商品/订单/优惠券卡片）',
  KEY `idx_receiver_sender` (`receiver_id`, `sender_id`),
  KEY `idx_sender_receiver` (`sender_id`, `receiver_id`),
  UNIQUE KEY `msg_id` (`msg_id`),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=308 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息表';

-- --------------------------------------------------------------
--  order_items  订单条目表
-- --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `order_items` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '条目ID',
  `order_id` int NOT NULL COMMENT '订单ID',
  `shop_id` varchar(32) NOT NULL COMMENT '商品所属店铺快照',
  `product_id` int NOT NULL COMMENT '商品ID',
  `title` varchar(120) NOT NULL COMMENT '商品标题快照',
  `sku` varchar(100) NOT NULL DEFAULT '默认' COMMENT '规格文本',
  `qty` int NOT NULL DEFAULT 1 COMMENT '数量',
  `price` decimal(10,2) NOT NULL COMMENT '下单时售价快照',
  `art_img` varchar(500) NULL COMMENT '展示图 OSS 地址快照（下单时定格商品第一个图片；无图时前端回退渐变占位）',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY `idx_order` (`order_id`),
  KEY `idx_shop_order` (`shop_id`, `order_id`),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=140 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单条目表';

-- --------------------------------------------------------------
--  orders  订单主表
-- --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `orders` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单号',
  `pay_no` varchar(32) NULL COMMENT '支付单号：同一次下单拆出的子订单共享，用于一次付清',
  `user_id` varchar(15) NOT NULL COMMENT '买家账号',
  `status` varchar(12) NOT NULL DEFAULT 'pending' COMMENT 'pending 待付款 / paid 待发货 / shipped 待收货 / done 已完成 / canceled 已取消',
  `goods_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '商品金额',
  `discount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '优惠券抵扣金额',
  `freight` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '运费',
  `total` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '应付总额 = 商品金额 - 优惠 + 运费',
  `pay_method` varchar(20) NOT NULL DEFAULT '支付宝' COMMENT '支付方式',
  `remark` varchar(100) NULL DEFAULT '' COMMENT '订单备注',
  `address_json` text NULL COMMENT '收货地址快照',
  `coupon_json` text NULL COMMENT '优惠券快照',
  `logistics_json` text NULL COMMENT '物流轨迹',
  `pay_time` datetime NULL COMMENT '支付时间',
  `ship_time` datetime NULL COMMENT '发货时间',
  `finish_time` datetime NULL COMMENT '完成时间（确认收货）',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY `idx_pay_no` (`pay_no`),
  KEY `idx_status_time` (`status`, `created_at`),
  KEY `idx_user_status` (`user_id`, `status`),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`)
) ENGINE=InnoDB AUTO_INCREMENT=122 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单主表';

-- --------------------------------------------------------------
--  product_reviews  商品评价表
-- --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `product_reviews` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  `order_id` int NOT NULL COMMENT '订单ID',
  `order_item_id` int NOT NULL COMMENT '订单条目ID',
  `product_id` int NOT NULL COMMENT '商品ID',
  `shop_id` varchar(32) NOT NULL COMMENT '商品所属店铺快照',
  `user_id` varchar(15) NOT NULL COMMENT '评价人账号',
  `score` tinyint NOT NULL COMMENT '评分：1-5 星',
  `content` varchar(500) NOT NULL DEFAULT '' COMMENT '评价内容',
  `images` text NULL COMMENT '晒单图',
  `anonymous` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否匿名：1 匿名',
  `sku` varchar(100) NOT NULL DEFAULT '默认' COMMENT '款式文本快照',
  `append_content` varchar(500) NULL COMMENT '追评内容',
  `append_time` datetime NULL COMMENT '追评时间',
  `reply_content` varchar(500) NULL COMMENT '商家回复内容',
  `reply_time` datetime NULL COMMENT '商家回复时间',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY `idx_order` (`order_id`),
  KEY `idx_product_sku` (`product_id`, `sku`),
  KEY `idx_shop` (`shop_id`),
  KEY `idx_user` (`user_id`, `created_at`),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_item_user` (`order_item_id`, `user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品评价表';

-- --------------------------------------------------------------
--  products  商品表
-- --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `products` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `shop_id` varchar(32) NOT NULL COMMENT '所属店铺标识',
  `title` varchar(120) NOT NULL COMMENT '商品标题',
  `price` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '售价',
  `original_price` decimal(10,2) NULL COMMENT '原价/划线价（可为空）',
  `sales` int NOT NULL DEFAULT 0 COMMENT '销量',
  `stock` int NOT NULL DEFAULT 0 COMMENT '库存',
  `category` varchar(32) NULL COMMENT '一级分类',
  `sub` varchar(32) NULL COMMENT '二级分类',
  `tag` varchar(32) NULL COMMENT '标签',
  `rating` decimal(3,2) NULL COMMENT '商品评分：全部评价的平均分',
  `review_count` int NOT NULL DEFAULT 0 COMMENT '评价数',
  `skus` text NULL COMMENT '规格款式 JSON：[{"name":"颜色","values":[{"v":"曜石黑","img":"url"}|"曜石黑"]}',
  `params` text NULL COMMENT '参数 JSON：[["品牌","青禾"],["续航","60小时"]]',
  `detail` text NULL COMMENT '图文详情',
  `description` text NULL COMMENT '商品简介',
  `on_sale` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否在售：1 是 / 0 否',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '软删除：1 已删 / 0 正常',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY `idx_category` (`category`, `on_sale`, `deleted`),
  KEY `idx_sales` (`sales`),
  KEY `idx_shop` (`shop_id`, `on_sale`, `deleted`),
  KEY `idx_shop_rating` (`shop_id`, `rating`),
  KEY `idx_time` (`created_at`),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=53 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

-- --------------------------------------------------------------
--  read_state
-- --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `read_state` (
  `user_id` varchar(64) NOT NULL COMMENT '当前用户账号',
  `peer_id` varchar(64) NOT NULL COMMENT '对端账号',
  `last_read_time` datetime NOT NULL COMMENT '最近打开会话的时间',
  PRIMARY KEY (`user_id`, `peer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- --------------------------------------------------------------
--  seller_reminders  买家催发货记录（一单一店一行）
-- --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `seller_reminders` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `order_id` int NOT NULL COMMENT '订单ID（orders.id）',
  `shop_id` varchar(32) NOT NULL COMMENT '被催的店铺（shops.shop_id）',
  `buyer_id` varchar(32) NOT NULL COMMENT '提醒人（买家账号 users.user_id）',
  `owner_user_id` varchar(32) NOT NULL COMMENT '店主账号（推送目标，shops.owner_user_id 快照）',
  `remind_count` int NOT NULL DEFAULT 0 COMMENT '累计提醒次数',
  `last_remind_time` datetime NOT NULL COMMENT '最近一次提醒时间（冷却期与列表排序依据）',
  `handled` tinyint NOT NULL DEFAULT 0 COMMENT '店家是否已处理：1 已发货 / 0 未处理',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY `idx_shop_pending` (`shop_id`, `handled`, `last_remind_time`),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='买家催发货记录（一单一店一行）';

-- --------------------------------------------------------------
--  shop_follows  店铺关注表
-- --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `shop_follows` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '关注记录ID',
  `user_id` varchar(15) NOT NULL COMMENT '关注者账号',
  `shop_id` varchar(32) NOT NULL COMMENT '被关注店铺',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  KEY `idx_shop` (`shop_id`),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_shop` (`user_id`, `shop_id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺关注表';

-- --------------------------------------------------------------
--  shops  店铺表
-- --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `shops` (
  `shop_id` varchar(32) NOT NULL COMMENT '店铺标识',
  `name` varchar(60) NOT NULL COMMENT '店铺名',
  `owner_user_id` varchar(32) NOT NULL COMMENT '店主账号',
  `score` decimal(3,2) NOT NULL DEFAULT 4.80 COMMENT '店铺评分',
  `avatar` varchar(255) NULL DEFAULT '' COMMENT '店铺头像',
  `fans` int NOT NULL DEFAULT 0 COMMENT '粉丝数',
  `intro` varchar(255) NULL DEFAULT '' COMMENT '店铺简介',
  `founded` date NULL COMMENT '开店时间',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺表';

-- --------------------------------------------------------------
--  user_coupons  用户领取的优惠券（每张券每人限领一张）
-- --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_coupons` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '持有记录ID',
  `user_id` varchar(15) NOT NULL COMMENT '持有人',
  `coupon_id` int NOT NULL COMMENT '券模板ID',
  `status` varchar(12) NOT NULL DEFAULT 'unused' COMMENT 'unused 未使用 / used 已使用',
  `used_order_id` int NULL COMMENT '使用该券的订单ID',
  `used_at` datetime NULL COMMENT '使用时间',
  `received_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收到时间',
  KEY `idx_user_status` (`user_id`, `status`),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg` (`id`),
  UNIQUE KEY `uk_user_coupon` (`user_id`, `coupon_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户领取的优惠券（每张券每人限领一张）';

-- --------------------------------------------------------------
--  users  用户表
-- --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `users` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` varchar(15) NOT NULL COMMENT '用户账号（手机号注册时即手机号）',
  `password` varchar(100) NOT NULL COMMENT '密码哈希（BCrypt，60 字符）；历史明文数据在登录成功后自动升级',
  `nickname` varchar(30) NOT NULL COMMENT '昵称',
  `gender` varchar(10) NOT NULL DEFAULT 'secret' COMMENT '性别：male 男 / female 女 / secret 保密',
  `avatar` varchar(500) NOT NULL DEFAULT '' COMMENT '头像：阿里云 OSS 访问地址（旧数据为 emoji 字符，前端兼容显示）',
  `signature` varchar(40) NOT NULL DEFAULT '' COMMENT '个性签名（最长 40 字）',
  `shop_id` varchar(32) NULL COMMENT '所属店铺标识（未开店为 NULL；开店账号由服务端返回）',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `phone_number` varchar(11) NULL COMMENT '手机号',
  `last_pwd_change_at` datetime NULL COMMENT '密码最近修改时间',
  `phone_bound_at` datetime NULL COMMENT '手机号绑定/换绑时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '资料更新时间',
  `pwd_version` int NOT NULL DEFAULT 0 COMMENT '密码版本号：每次改密/重置 +1，令此前签发的 JWT 立即失效',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_phone_number` (`phone_number`),
  UNIQUE KEY `user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=70 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

SET FOREIGN_KEY_CHECKS = 1;

-- 脚本结束：共 21 张表
