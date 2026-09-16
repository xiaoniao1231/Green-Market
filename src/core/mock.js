/* =========================================================
   青集市 · mock.js —— 本地演示数据（后端未启动时使用）
   商品 / 分类 / 秒杀 / 聊天联系人 / 自动回复 / 辅助函数
   ========================================================= */

/* ---------- 商品分类 ---------- */
  const categories = [
    { id: '数码科技', icon: '◈', subs: ['手机', '笔记本', '耳机音响', '智能穿戴', '摄影摄像'] },
    { id: '家居生活', icon: '⌂', subs: ['收纳整理', '家纺布艺', '厨房用品', '灯具', '香薰摆件'] },
    { id: '服饰鞋包', icon: '✦', subs: ['女装', '男装', '鞋靴', '箱包', '配饰'] },
    { id: '美妆个护', icon: '◌', subs: ['护肤', '彩妆', '洗护', '香水', '美妆工具'] },
    { id: '美食饮品', icon: '♨', subs: ['休闲零食', '咖啡茶饮', '冲调谷物', '生鲜果蔬'] },
    { id: '运动户外', icon: '◒', subs: ['健身器材', '跑步装备', '露营装备', '骑行运动', '球类'] },
    { id: '图书文具', icon: '▤', subs: ['文具', '手账本册', '小说文学', '学习教辅'] },
    { id: '宠物花植', icon: '❋', subs: ['猫狗主粮', '宠物玩具', '绿植盆栽', '水族用品'] }
  ];

  /* ---------- 商品库（art.e 表情图标 + art.g 渐变背景） ---------- */
  const products = [
    { id: 'p01', title: '青禾无线降噪耳机 头戴式蓝牙5.3 超长续航 重低音游戏音乐耳机', price: 299, original: 459, sales: 12600, stock: 320, category: '数码科技', sub: '耳机音响', tag: '次日达', art: { e: '🎧', g: ['#7f7fd5', '#86a8e7'] }, shop: { name: '青禾数码旗舰店', score: 4.8 }, skus: [{ name: '颜色', values: ['曜石黑', '月光白', '雾霾蓝'] }, { name: '版本', values: ['标准版', 'Pro 降噪版'] }], desc: '40mm 大动圈单元，主动降噪深度 -38dB，蓝牙 5.3 稳定连接，60 小时超长续航，Type-C 快充 10 分钟畅听 5 小时。', params: [['品牌', '青禾'], ['连接方式', '蓝牙 5.3 / 有线'], ['续航', '60 小时'], ['降噪', '主动降噪 -38dB'], ['重量', '248g']], comments: [{ user: '小**鱼', rate: 5, text: '降噪效果比想象中好，通勤戴着很舒服，续航一周不用充电。', time: '2026-08-12' }, { user: '风**子', rate: 4, text: '音质不错，戴久了稍微有点夹头，整体满意。', time: '2026-08-05' }] },
    { id: 'p02', title: '智能运动手表 血氧心率监测 100+运动模式 5ATM防水 两周续航', price: 399, original: 599, sales: 8300, stock: 150, category: '数码科技', sub: '智能穿戴', tag: '包邮', art: { e: '⌚', g: ['#ff9966', '#ff5e62'] }, shop: { name: '青禾数码旗舰店', score: 4.8 }, skus: [{ name: '颜色', values: ['曜石黑', '星光银', '珊瑚粉'] }], desc: '1.43 英寸 AMOLED 高清屏，24 小时心率/血氧监测，100+ 运动模式，5ATM 防水，典型使用续航 14 天。', params: [['品牌', '青禾'], ['屏幕', '1.43 英寸 AMOLED'], ['防水', '5ATM'], ['续航', '14 天']], comments: [{ user: '跑**者', rate: 5, text: '跑步数据很准，GPS 定位快，睡眠监测也很详细。', time: '2026-08-10' }] },
    { id: 'p03', title: '青禾轻薄本 14英寸 2.8K高刷屏 16G+512G 全金属机身 办公学习笔记本电脑', price: 4599, original: 5299, sales: 2100, stock: 60, category: '数码科技', sub: '笔记本', tag: '次日达', art: { e: '💻', g: ['#5b86e5', '#36d1dc'] }, shop: { name: '青禾数码旗舰店', score: 4.8 }, skus: [{ name: '配置', values: ['16G+512G', '16G+1TB', '32G+1TB'] }], desc: '14 英寸 2.8K 120Hz 高分屏，锐龙 R7 处理器，全金属机身仅 1.29kg，65W 快充，学生办公两相宜。', params: [['屏幕', '14" 2.8K 120Hz'], ['处理器', 'R7-7840H'], ['内存', '16G/32G'], ['重量', '1.29kg']], comments: [{ user: '课**表', rate: 5, text: '屏幕素质非常好，写代码看文档都很舒服，风扇声音小。', time: '2026-08-08' }] },
    { id: 'p04', title: '旗舰影像手机 12GB+256GB 1英寸大底 潜望长焦 120W快充 5G全网通', price: 3699, original: 4299, sales: 15600, stock: 90, category: '数码科技', sub: '手机', tag: '次日达', art: { e: '📱', g: ['#11998e', '#38ef7d'] }, shop: { name: '青禾数码旗舰店', score: 4.8 }, skus: [{ name: '颜色', values: ['远峰蓝', '松柏绿', '星光白'] }, { name: '存储', values: ['12G+256G', '16G+512G'] }], desc: '1 英寸大底主摄，3.2 倍潜望长焦，120W 有线快充 18 分钟满电，旗舰芯片性能拉满。', params: [['主摄', '1 英寸大底'], ['长焦', '潜望 3.2x'], ['快充', '120W'], ['系统', '自研 OS 5']], comments: [{ user: '拍**党', rate: 5, text: '夜景和长焦都很能打，快充是真的快，性价比高。', time: '2026-08-15' }] },
    { id: 'p05', title: '北欧风琴叶榕绿植盆栽 室内客厅大型植物 净化空气 带盆发货', price: 89, original: 129, sales: 5200, stock: 400, category: '宠物花植', sub: '绿植盆栽', tag: '包邮', art: { e: '🪴', g: ['#a8e063', '#56ab2f'] }, shop: { name: '森语园艺', score: 4.9 }, skus: [{ name: '规格', values: ['小号 60cm', '中号 90cm', '大号 120cm'] }], desc: '网红琴叶榕，四季常绿，耐阴好养，吸附甲醛净化空气，客厅卧室皆宜。', params: [['高度', '60-120cm'], ['光照', '散射光'], ['浇水', '见干见湿']], comments: [{ user: '绿**指', rate: 5, text: '包装很仔细，叶子一片没掉，放在客厅很有氛围感。', time: '2026-08-06' }] },
    { id: 'p06', title: '懒人豆袋沙发 单人榻榻米 可拆洗 阳台卧室躺椅 加厚填充', price: 199, original: 329, sales: 7400, stock: 210, category: '家居生活', sub: '家纺布艺', tag: '包邮', art: { e: '🛋️', g: ['#ee9ca7', '#ffdde1'] }, shop: { name: '暖居生活馆', score: 4.7 }, skus: [{ name: '颜色', values: ['奶油白', '雾灰', '焦糖棕'] }], desc: '高回弹粒子填充，环保棉麻外套可拆洗，慵懒午后窝进豆袋的幸福感。', params: [['面料', '棉麻'], ['填充', 'EPP 高回弹粒子'], ['承重', '150kg']], comments: [{ user: '宅**喵', rate: 5, text: '超舒服！周末窝在里面看书刷剧，幸福感爆棚。', time: '2026-08-11' }] },
    { id: 'p07', title: '麦饭石不粘锅炒锅 家用平底煎锅 少油烟 电磁炉燃气通用', price: 129, original: 199, sales: 9800, stock: 500, category: '家居生活', sub: '厨房用品', tag: '次日达', art: { e: '🍳', g: ['#ffb75e', '#ed8f03'] }, shop: { name: '暖居生活馆', score: 4.7 }, skus: [{ name: '尺寸', values: ['26cm', '28cm', '30cm'] }], desc: '麦饭石不粘涂层，少油少烟，一擦即净，燃气灶电磁炉通用，附赠硅胶铲。', params: [['口径', '26-30cm'], ['涂层', '麦饭石不粘'], ['适用', '明火/电磁炉']], comments: [{ user: '厨**记', rate: 5, text: '不粘效果很好，煎蛋不放油也不粘，清洗方便。', time: '2026-08-03' }] },
    { id: 'p08', title: '手冲咖啡壶套装 细口壶+滤杯+分享壶 家用咖啡器具礼盒', price: 158, original: 239, sales: 3600, stock: 180, category: '美食饮品', sub: '咖啡茶饮', tag: '包邮', art: { e: '☕', g: ['#6b4f3f', '#b98d6f'] }, shop: { name: '拾光咖啡', score: 4.9 }, skus: [{ name: '套装', values: ['基础三件套', '礼盒六件套'] }], desc: '专业细口壶 8mm 出水口，控流精准，新手也能冲出好风味，自用送礼皆宜。', params: [['材质', '304 不锈钢'], ['容量', '600ml'], ['套装', '三/六件']], comments: [{ user: '手**师', rate: 5, text: '水流控制很顺，颜值也高，周末在家手冲仪式感满满。', time: '2026-08-14' }] },
    { id: 'p09', title: '办公室休闲零食大礼包 坚果果干混合装 网红解馋小吃 30袋', price: 59, original: 99, sales: 23000, stock: 800, category: '美食饮品', sub: '休闲零食', tag: '包邮', art: { e: '🍪', g: ['#f7971e', '#ffd200'] }, shop: { name: '拾光咖啡', score: 4.8 }, skus: [{ name: '口味', values: ['坚果混合', '果干混合', '混搭装'] }], desc: '30 袋独立小包装，坚果果干科学配比，办公室下午茶解馋必备。', params: [['净含量', '750g'], ['包装', '30 小袋独立装'], ['保质期', '180 天']], comments: [{ user: '摸**员', rate: 5, text: '同事们都很喜欢，独立包装干净卫生，回购第三次了。', time: '2026-08-13' }] },
    { id: 'p10', title: '轻量缓震跑步鞋 男女同款 透气网面 专业马拉松训练鞋', price: 269, original: 399, sales: 11000, stock: 260, category: '运动户外', sub: '跑步装备', tag: '次日达', art: { e: '👟', g: ['#fc4a1a', '#f7b733'] }, shop: { name: '跃动体育', score: 4.8 }, skus: [{ name: '颜色', values: ['荧光橙', '午夜黑', '湖水蓝'] }, { name: '尺码', values: ['39', '40', '41', '42', '43'] }], desc: '中底超临界发泡材料，回弹率 75%，透气飞织鞋面，单只仅 215g，日常慢跑到马拉松都能驾驭。', params: [['重量', '215g/只'], ['回弹', '75%'], ['鞋面', '飞织透气']], comments: [{ user: '跑**者', rate: 5, text: '缓震很舒服，10 公里下来膝盖没有不适感，配色也好看。', time: '2026-08-09' }] },
    { id: 'p11', title: '大容量旅行双肩包 防泼水电脑包 15.6英寸 学生商务通勤背包', price: 139, original: 219, sales: 6600, stock: 340, category: '服饰鞋包', sub: '箱包', tag: '包邮', art: { e: '🎒', g: ['#4b6cb7', '#182848'] }, shop: { name: '行囊箱包', score: 4.7 }, skus: [{ name: '颜色', values: ['深空灰', '藏青', '卡其'] }], desc: '28L 大容量，独立电脑仓可放 15.6 英寸笔记本，防泼水面料，背部透气减压设计。', params: [['容量', '28L'], ['电脑仓', '15.6 英寸'], ['面料', '防泼水尼龙']], comments: [{ user: '通**族', rate: 4, text: '收纳分区合理，肩带宽背起来不累，防泼水很实用。', time: '2026-08-02' }] },
    { id: 'p12', title: '法式复古碎花连衣裙 夏季收腰显瘦 温柔风长裙 气质女神范', price: 159, original: 259, sales: 8900, stock: 220, category: '服饰鞋包', sub: '女装', tag: '包邮', art: { e: '👗', g: ['#eea2ad', '#f9d423'] }, shop: { name: '南巷衣橱', score: 4.6 }, skus: [{ name: '颜色', values: ['雾粉碎花', '杏色碎花', '浅蓝碎花'] }, { name: '尺码', values: ['S', 'M', 'L', 'XL'] }], desc: '法式方领收腰设计，垂感雪纺面料，遮肉显瘦，约会出游都出片。', params: [['面料', '雪纺'], ['版型', '收腰 A 字'], ['季节', '夏季']], comments: [{ user: '裙**生', rate: 5, text: '上身效果超出预期，显瘦又温柔，朋友都问链接。', time: '2026-08-16' }] },
    { id: 'p13', title: '丝绒哑光口红礼盒 3支装 显白不拔干 情人节生日礼物', price: 129, original: 199, sales: 12000, stock: 300, category: '美妆个护', sub: '彩妆', tag: '次日达', art: { e: '💄', g: ['#ff416c', '#ff4b2b'] }, shop: { name: '花颜美妆', score: 4.8 }, skus: [{ name: '色号', values: ['正红棕', '豆沙粉', '烂番茄'] }], desc: '丝绒哑光质地，一抹显色，添加角鲨烷滋润不拔干，礼盒包装送礼有面子。', params: [['质地', '丝绒哑光'], ['净含量', '3.5g×3'], ['保质期', '3 年']], comments: [{ user: '口**控', rate: 5, text: '显白绝了！黄皮也能驾驭，礼盒包装很精致。', time: '2026-08-07' }] },
    { id: 'p14', title: '氨基酸温和洁面乳 敏感肌可用 深层清洁不紧绷 150g', price: 59, original: 89, sales: 18000, stock: 600, category: '美妆个护', sub: '洗护', tag: '包邮', art: { e: '🧴', g: ['#a1c4fd', '#c2e9fb'] }, shop: { name: '花颜美妆', score: 4.8 }, skus: [{ name: '规格', values: ['150g 单支', '150g×2 组合'] }], desc: '氨基酸表活温和清洁，绵密泡沫洗后不紧绷，敏感肌安心之选。', params: [['表活', '氨基酸'], ['净含量', '150g'], ['适用', '所有肤质']], comments: [{ user: '敏**肌', rate: 5, text: '温和不刺激，洗完脸软软的，已经空管三支。', time: '2026-08-01' }] },
    { id: 'p15', title: '治愈系小说 我在风里等你 青春文学畅销书 正版包邮', price: 32, original: 45, sales: 4300, stock: 900, category: '图书文具', sub: '小说文学', tag: '包邮', art: { e: '📚', g: ['#5f2c82', '#49a09d'] }, shop: { name: '墨香书社', score: 4.9 }, skus: [{ name: '版本', values: ['平装', '精装签名版'] }], desc: '豆瓣高分治愈系青春文学，关于相遇与告别的温柔故事，附赠主题明信片。', params: [['作者', '苏晚'], ['页数', '312 页'], ['装帧', '平装/精装']], comments: [{ user: '书**虫', rate: 5, text: '文笔细腻，读到最后眼眶湿润，值得二刷。', time: '2026-08-04' }] },
    { id: 'p16', title: '钢笔礼盒套装 商务办公签字笔 顺滑练字钢笔 刻字定制', price: 99, original: 159, sales: 2900, stock: 150, category: '图书文具', sub: '文具', tag: '包邮', art: { e: '✒️', g: ['#3a1c71', '#d76d77'] }, shop: { name: '墨香书社', score: 4.9 }, skus: [{ name: '颜色', values: ['经典黑金', '玫瑰金', '星空蓝'] }], desc: '德国笔尖书写顺滑，礼盒含墨水+笔套，支持免费刻字，送礼自用两相宜。', params: [['笔尖', 'EF 0.38mm'], ['材质', '黄铜烤漆'], ['刻字', '免费']], comments: [{ user: '练**生', rate: 5, text: '书写顺滑不断墨，刻字很精致，送朋友的生日礼物。', time: '2026-08-12' }] },
    { id: 'p17', title: '无谷冻干猫粮 全价全期 鸡肉配方 美毛护肠 2kg 装', price: 149, original: 209, sales: 7600, stock: 280, category: '宠物花植', sub: '猫狗主粮', tag: '次日达', art: { e: '🐱', g: ['#ffaf7b', '#d76d77'] }, shop: { name: '毛球星球', score: 4.9 }, skus: [{ name: '规格', values: ['1kg 尝鲜装', '2kg 装', '5kg 囤货装'] }], desc: '82% 动物性原料，无谷低敏配方，添加冻干鸡肉粒，挑嘴猫也爱吃。', params: [['肉含量', '82%'], ['配方', '无谷低敏'], ['净含量', '2kg']], comments: [{ user: '铲**官', rate: 5, text: '主子超爱吃冻干，毛发也变亮了，会一直回购。', time: '2026-08-10' }] },
    { id: 'p18', title: '宠物逗猫棒套装 耐咬羽毛铃铛 猫咪玩具 解闷神器 5件套', price: 19.9, original: 39.9, sales: 15000, stock: 1000, category: '宠物花植', sub: '宠物玩具', tag: '包邮', art: { e: '🐶', g: ['#fbd786', '#f7797d'] }, shop: { name: '毛球星球', score: 4.9 }, skus: [{ name: '套装', values: ['5 件套', '10 件套豪华装'] }], desc: '羽毛+铃铛+激光笔组合，激发猫咪捕猎天性，每天 15 分钟互动增进感情。', params: [['数量', '5 件'], ['材质', '环保羽毛'], ['适用', '猫咪']], comments: [{ user: '猫**娘', rate: 5, text: '主子玩疯了，每天到点就叼着逗猫棒来找我。', time: '2026-08-14' }] },
    { id: 'p19', title: '香薰蜡烛礼盒 助眠安神 大豆蜡 卧室浪漫氛围 无烟 3 罐装', price: 69, original: 119, sales: 5400, stock: 260, category: '家居生活', sub: '香薰摆件', tag: '包邮', art: { e: '🕯️', g: ['#c9a7eb', '#ffd1ff'] }, shop: { name: '暖居生活馆', score: 4.7 }, skus: [{ name: '香型', values: ['白茶', '海盐鼠尾草', '雪松'] }], desc: '天然大豆蜡，无烟无黑渣，燃烧约 30 小时，睡前点燃放松助眠。', params: [['蜡基', '天然大豆蜡'], ['燃烧', '约 30h/罐'], ['数量', '3 罐']], comments: [{ user: '香**家', rate: 5, text: '白茶味很高级，睡前点一会儿特别放松。', time: '2026-08-08' }] },
    { id: 'p20', title: '护眼台灯 学习办公 国AA级照度 无频闪 触控调光 学生宿舍', price: 189, original: 269, sales: 4700, stock: 190, category: '家居生活', sub: '灯具', tag: '次日达', art: { e: '💡', g: ['#f6d365', '#fda085'] }, shop: { name: '暖居生活馆', score: 4.8 }, skus: [{ name: '颜色', values: ['雅白', '墨黑'] }], desc: '国 AA 级照度，Ra98 高显色，无频闪无蓝光危害，触控无极调光，护眼学习好搭档。', params: [['照度', '国 AA 级'], ['显色', 'Ra98'], ['调光', '触控无极']], comments: [{ user: '考**生', rate: 5, text: '灯光很柔和，写作业眼睛不累了，孩子很喜欢。', time: '2026-08-06' }] },
    { id: 'p21', title: '机械键盘 87键 热插拔轴座 RGB背光 电竞游戏办公 客制化', price: 259, original: 359, sales: 6800, stock: 170, category: '数码科技', sub: '电脑外设', tag: '次日达', art: { e: '⌨️', g: ['#232526', '#414345'] }, shop: { name: '青禾数码旗舰店', score: 4.8 }, skus: [{ name: '轴体', values: ['红轴', '茶轴', '青轴'] }, { name: '配色', values: ['黑灰', '白粉'] }], desc: 'Gasket 结构手感软弹，全键热插拔随心换轴，RGB 灯效 18 种，游戏办公全都要。', params: [['配列', '87 键'], ['轴体', '热插拔'], ['灯效', 'RGB 18 种']], comments: [{ user: '键**党', rate: 5, text: '敲击手感很棒，声音好听，热插拔可玩性高。', time: '2026-08-15' }] },
    { id: 'p22', title: '无线游戏手柄 双模连接 霍尔摇杆 体感震动 适配PC/Switch', price: 199, original: 279, sales: 5100, stock: 230, category: '数码科技', sub: '电脑外设', tag: '包邮', art: { e: '🎮', g: ['#00c6ff', '#0072ff'] }, shop: { name: '青禾数码旗舰店', score: 4.7 }, skus: [{ name: '颜色', values: ['极光蓝', '暗夜黑', '樱花粉'] }], desc: '霍尔摇杆不漂移，双模连接 PC/Switch 即插即用，六轴体感+四马达震动，沉浸式游戏体验。', params: [['连接', '蓝牙/2.4G'], ['摇杆', '霍尔'], ['续航', '20 小时']], comments: [{ user: '游**家', rate: 5, text: '摇杆精准无漂移，连 Switch 和电脑都很稳。', time: '2026-08-11' }] },
    { id: 'p23', title: '中筒运动袜 5双装 透气吸汗防臭 跑步篮球羽毛球袜', price: 39.9, original: 69, sales: 21000, stock: 1200, category: '运动户外', sub: '运动配件', tag: '包邮', art: { e: '🧦', g: ['#00b09b', '#96c93d'] }, shop: { name: '跃动体育', score: 4.6 }, skus: [{ name: '颜色', values: ['黑白灰混装', '彩色混装'] }], desc: '精梳棉混纺透气排汗，袜口不勒腿，加厚毛巾底缓震耐磨，运动休闲两穿。', params: [['数量', '5 双'], ['材质', '精梳棉 78%'], ['适用', '运动/日常']], comments: [{ user: '球**友', rate: 4, text: '透气不臭脚，打球穿很舒服，价格实惠。', time: '2026-08-02' }] },
    { id: 'p24', title: '大容量随行杯 316不锈钢 保温保冷 便携水杯 车载办公 750ml', price: 79, original: 119, sales: 9200, stock: 450, category: '家居生活', sub: '厨房用品', tag: '包邮', art: { e: '🥤', g: ['#f857a6', '#ff5858'] }, shop: { name: '暖居生活馆', score: 4.8 }, skus: [{ name: '颜色', values: ['樱花粉', '薄荷绿', '曜石黑'] }], desc: '316 不锈钢内胆，12 小时保温 24 小时保冷，一键弹盖防漏，办公健身都合适。', params: [['内胆', '316 不锈钢'], ['容量', '750ml'], ['保温', '12 小时']], comments: [{ user: '养**人', rate: 5, text: '保温效果很好，早上装的热水到下午还是烫的。', time: '2026-08-09' }] },
    { id: 'p25', title: '进口黑巧克力礼盒 88%可可 低糖苦醇 下午茶零食 24 片装', price: 49, original: 89, sales: 6100, stock: 380, category: '美食饮品', sub: '休闲零食', tag: '包邮', art: { e: '🍫', g: ['#5c4033', '#8b5a2b'] }, shop: { name: '拾光咖啡', score: 4.9 }, skus: [{ name: '浓度', values: ['72% 可可', '88% 可可', '99% 可可'] }], desc: '精选厄瓜多尔可可豆，88% 可可含量，微苦回甘不甜腻，配咖啡绝佳。', params: [['可可含量', '88%'], ['数量', '24 片'], ['保质期', '18 个月']], comments: [{ user: '黑**控', rate: 5, text: '苦得刚刚好，回甘很香，控糖期也能吃。', time: '2026-08-13' }] },
    { id: 'p26', title: '全自动晴雨伞 黑胶防晒 10骨加固 晴雨两用 便携折叠伞', price: 59, original: 99, sales: 13000, stock: 700, category: '服饰鞋包', sub: '配饰', tag: '次日达', art: { e: '🌂', g: ['#8360c3', '#2ebf91'] }, shop: { name: '行囊箱包', score: 4.7 }, skus: [{ name: '颜色', values: ['藏青', '雾蓝', '墨绿'] }], desc: '黑胶涂层 UPF50+ 阻隔 99% 紫外线，10 骨防风加固，一键开合，晴雨两用。', params: [['防晒', 'UPF50+'], ['伞骨', '10 骨'], ['重量', '320g']], comments: [{ user: '打**人', rate: 5, text: '伞面大，大风天也稳，黑胶防晒很安心。', time: '2026-08-05' }] },
    { id: 'p27', title: '治愈系毛绒玩偶 生日礼物 抱枕公仔 睡觉陪伴 软萌大号', price: 79, original: 139, sales: 9900, stock: 320, category: '宠物花植', sub: '宠物玩具', tag: '包邮', art: { e: '🧸', g: ['#eacda3', '#d6ae7b'] }, shop: { name: '毛球星球', score: 4.8 }, skus: [{ name: '尺寸', values: ['25cm', '45cm', '60cm 大号'] }], desc: '优质短毛绒填充饱满，手感软糯不掉毛，抱着睡超有安全感，送礼首选。', params: [['面料', '短毛绒'], ['填充', 'PP 棉'], ['清洗', '可机洗']], comments: [{ user: '软**控', rate: 5, text: '太软了！抱着睡觉超舒服，没有异味。', time: '2026-08-14' }] },
    { id: 'p28', title: '拍立得相机 即拍即得 复古胶片感 学生礼物 含相纸', price: 459, original: 599, sales: 3300, stock: 110, category: '数码科技', sub: '摄影摄像', tag: '次日达', art: { e: '📷', g: ['#ee9ca7', '#c3cfe2'] }, shop: { name: '青禾数码旗舰店', score: 4.8 }, skus: [{ name: '颜色', values: ['奶油白', '薄荷绿', '樱花粉'] }], desc: '复古胶片质感，自动测光对焦，内置自拍镜，附赠 10 张相纸，记录生活仪式感。', params: [['画幅', '86×54mm'], ['对焦', '自动'], ['赠品', '相纸 10 张']], comments: [{ user: '记**控', rate: 5, text: '出片很有感觉，聚会拍照氛围神器，朋友都抢着拍。', time: '2026-08-16' }] },
    { id: 'p29', title: '露营氛围灯 户外帐篷灯 充电LED 三色调光 防水便携 野营装备', price: 89, original: 139, sales: 4800, stock: 200, category: '运动户外', sub: '露营装备', tag: '包邮', art: { e: '🏕️', g: ['#f2c94c', '#f2994a'] }, shop: { name: '跃动体育', score: 4.7 }, skus: [{ name: '颜色', values: ['琥珀黄', '森林绿'] }], desc: '三色温无极调光，IPX4 防水，12000mAh 可当充电宝，续航 40 小时，露营氛围感拉满。', params: [['电池', '12000mAh'], ['防水', 'IPX4'], ['续航', '40 小时']], comments: [{ user: '露**家', rate: 5, text: '灯光很暖很有氛围，还能给手机应急充电，实用。', time: '2026-08-07' }] },
    { id: 'p30', title: '清爽防晒霜 SPF50+ 军训户外 防水防汗 不油腻 学生党必备 50ml', price: 69, original: 109, sales: 17000, stock: 850, category: '美妆个护', sub: '护肤', tag: '包邮', art: { e: '🧴', g: ['#56ccf2', '#2f80ed'] }, shop: { name: '花颜美妆', score: 4.8 }, skus: [{ name: '规格', values: ['50ml', '50ml×2'] }], desc: 'SPF50+ PA++++ 高倍防晒，水感质地不油腻不搓泥，防水防汗，军训通勤都安心。', params: [['防晒指数', 'SPF50+ PA++++'], ['质地', '水感清爽'], ['净含量', '50ml']], comments: [{ user: '军**生', rate: 5, text: '军训半个月没晒黑！清爽不闷痘，会回购。', time: '2026-08-12' }] }
  ];

  /* ---------- 秒杀商品 ---------- */
  const flashIds = ['p01', 'p09', 'p13', 'p17', 'p24', 'p30'];

  /* ---------- 店铺与开店用户（演示） ----------
     全站只有一个用户账号体系，不存在独立的「店家账号」：
     · 店铺归属于某个用户账号（userId），卖家与买家是同一类用户；
     · 聊天就是用户与用户之间的私聊——联系一家店铺，就是和开这家店的用户聊天；
     · 每个店铺的 userId 即该用户的演示账号（owner001~owner010，见 shopOwners），
       userId 兼作聊天 peer id；
     · 不预置联系人与聊天记录，用户在商品详情页点「联系卖家」后按店铺创建会话。 */
  const contacts = [];

  const chats = {};

  /* 按店铺名索引的店铺信息（商品详情「联系卖家」、店铺页、我的店铺共用）：
     id=店铺标识  userId=开店用户账号（兼作聊天身份）  name=该用户昵称（对端显示名）
     owner=卖家名称  avatar=店铺头像  fans=粉丝数  founded=开店时间  shopIntro=店铺简介 */
  const shopServices = {
    '青禾数码旗舰店': { id: 'shop-qinghe', userId: 'owner001', name: '青禾数码', color: '#ff6a2b', intro: '青禾数码旗舰店 · 商品咨询 / 物流 / 售后', owner: '青禾数码', avatar: '📱', fans: 12800, founded: '2022-03-18', shopIntro: '专注数码好物，正品保障、极速发货、7 天无忧退货，旗舰影像 / 音频 / 电脑一站式购齐。' },
    '森语园艺': { id: 'shop-senlin', userId: 'owner002', name: '森语园艺', color: '#38ad90', intro: '森语园艺 · 商品咨询 / 养护 / 售后', owner: '森语园艺', avatar: '🪴', fans: 8600, founded: '2023-05-02', shopIntro: '把森林搬进你的家，绿植盆栽与园艺资材，带盆发货、包活到家。' },
    '暖居生活馆': { id: 'shop-nuanju', userId: 'owner003', name: '暖居生活', color: '#e08b5e', intro: '暖居生活馆 · 商品咨询 / 物流 / 售后', owner: '暖居生活', avatar: '🛋️', fans: 15200, founded: '2021-11-11', shopIntro: '温暖治愈的家居好物，沙发、灯具、香薰，让每一平米都有幸福感。' },
    '拾光咖啡': { id: 'shop-kafei', userId: 'owner004', name: '拾光咖啡', color: '#8b5e3c', intro: '拾光咖啡 · 商品咨询 / 物流 / 售后', owner: '拾光咖啡', avatar: '☕', fans: 9300, founded: '2022-08-08', shopIntro: '咖啡器具与精品零食，认真对待每一杯手冲，也认真对待每次解馋。' },
    '跃动体育': { id: 'shop-yuedong', userId: 'owner005', name: '跃动体育', color: '#f36f21', intro: '跃动体育 · 商品咨询 / 尺码 / 售后', owner: '跃动体育', avatar: '👟', fans: 11000, founded: '2022-01-20', shopIntro: '专业运动装备，跑步、健身、露营一站式购齐，陪你跑得更远。' },
    '行囊箱包': { id: 'shop-xingnang', userId: 'owner006', name: '行囊箱包', color: '#4b6cb7', intro: '行囊箱包 · 商品咨询 / 物流 / 售后', owner: '行囊箱包', avatar: '🎒', fans: 7200, founded: '2023-02-14', shopIntro: '陪你出发的每一件行囊，通勤、旅行、商务皆宜，装得下日常与远方。' },
    '南巷衣橱': { id: 'shop-nanxiang', userId: 'owner007', name: '南巷衣橱', color: '#d971a4', intro: '南巷衣橱 · 商品咨询 / 尺码 / 售后', owner: '南巷衣橱', avatar: '👗', fans: 13400, founded: '2021-06-18', shopIntro: '法式复古女装，温柔显瘦，为每个日常穿搭加分。' },
    '花颜美妆': { id: 'shop-huayan', userId: 'owner008', name: '花颜美妆', color: '#ff416c', intro: '花颜美妆 · 商品咨询 / 肤质 / 售后', owner: '花颜美妆', avatar: '💄', fans: 18600, founded: '2020-09-09', shopIntro: '美妆个护甄选，成分党严选，敏感肌也安心，颜值与安全都要。' },
    '墨香书社': { id: 'shop-moxiang', userId: 'owner009', name: '墨香书社', color: '#5f2c82', intro: '墨香书社 · 商品咨询 / 物流 / 售后', owner: '墨香书社', avatar: '📚', fans: 6800, founded: '2023-04-23', shopIntro: '正版图书文具，阅读让生活更有温度，选书有眼光、发货有速度。' },
    '毛球星球': { id: 'shop-maoqiu', userId: 'owner010', name: '毛球星球', color: '#d76d77', intro: '毛球星球 · 商品咨询 / 喂养 / 售后', owner: '毛球星球', avatar: '🐱', fans: 15900, founded: '2021-12-12', shopIntro: '萌宠主粮玩具，主子开心、铲屎官省心，毛孩子的快乐星球。' }
  };

  /* 按店铺名取对应店铺信息（未知店铺兜底为平台） */
  function serviceOf(shopName) {
    return shopServices[shopName] || { id: 'shop-service', userId: 'platform', name: '青集市平台', color: '#ff6a2b', intro: '青集市平台 · 商品咨询 / 物流 / 售后', owner: '青集市平台', avatar: '青', fans: 99999, founded: '2020-01-01', shopIntro: '青集市平台，为你解决购物中的任何问题。' };
  }

  /* 按店铺标识 id 或开店用户 id 取店铺信息（消息中心从商品详情进入时使用，找不到返回 null） */
  function serviceById(id) {
    const hit = Object.values(shopServices).find(s => s.id === id || s.userId === id);
    return hit || (id === 'shop-service' || id === 'platform' ? serviceOf('') : null);
  }

  /* 按店铺标识 id 或开店用户 id 反查店铺名（我的店铺 / 聊天迁移使用，找不到返回 null） */
  function shopNameById(id) {
    const hit = Object.entries(shopServices).find(([, s]) => s.id === id || s.userId === id);
    return hit ? hit[0] : null;
  }

  /* 旧版「客服用户 id」（kf-xxx）→ 新版开店用户 id（owner0xx）迁移映射：老聊天记录不丢失 */
  const kfLegacyToOwner = {
    'kf-qinghe': 'owner001', 'kf-senlin': 'owner002', 'kf-nuanju': 'owner003', 'kf-kafei': 'owner004',
    'kf-yuedong': 'owner005', 'kf-xingnang': 'owner006', 'kf-nanxiang': 'owner007', 'kf-huayan': 'owner008',
    'kf-moxiang': 'owner009', 'kf-maoqiu': 'owner010', 'kf-service': 'platform'
  };

  /* ---------- 开店演示账号（账号 + 店铺绑定） ----------
     全站共用同一套用户账号：登录这些账号只会多出一个店铺绑定，账号本身仍是普通用户；
     user.shopId 指向其店铺（后端 + 数据库接入后，由服务端账号表返回该绑定） */
  const shopOwners = {
    'owner001': { password: '123456', nickname: '青禾数码', shopId: 'shop-qinghe' },
    'owner002': { password: '123456', nickname: '森语园艺', shopId: 'shop-senlin' },
    'owner003': { password: '123456', nickname: '暖居生活', shopId: 'shop-nuanju' },
    'owner004': { password: '123456', nickname: '拾光咖啡', shopId: 'shop-kafei' },
    'owner005': { password: '123456', nickname: '跃动体育', shopId: 'shop-yuedong' },
    'owner006': { password: '123456', nickname: '行囊箱包', shopId: 'shop-xingnang' },
    'owner007': { password: '123456', nickname: '南巷衣橱', shopId: 'shop-nanxiang' },
    'owner008': { password: '123456', nickname: '花颜美妆', shopId: 'shop-huayan' },
    'owner009': { password: '123456', nickname: '墨香书社', shopId: 'shop-moxiang' },
    'owner010': { password: '123456', nickname: '毛球星球', shopId: 'shop-maoqiu' }
  };

  /* 用户账号 → 开店演示账号（登录后附带店铺绑定 shopId；未开店账号返回 null） */
  function shopOf(userId) {
    return shopOwners[String(userId || '').trim()] || null;
  }

  /* ---------- 辅助函数 ---------- */
  function byId(id) { return products.find(p => p.id === id) || null; }
  function byCategory(catId, sub) { return products.filter(p => p.category === catId && (!sub || p.sub === sub)); }
  function search(keyword) {
    if (!keyword) return products.slice();
    const kw = String(keyword).toLowerCase();
    return products.filter(p =>
      p.title.toLowerCase().includes(kw) || p.category.includes(keyword) ||
      p.sub.includes(keyword) || p.shop.name.includes(keyword));
  }
  function sortProducts(list, sort) {
    const copy = list.slice();
    if (sort === 'sales') copy.sort((a, b) => b.sales - a.sales);
    else if (sort === 'priceAsc') copy.sort((a, b) => a.price - b.price);
    else if (sort === 'priceDesc') copy.sort((a, b) => b.price - a.price);
    return copy;
  }
  function paginate(list, page, size) {
    page = Math.max(1, page | 0); size = Math.max(1, size | 0);
    return { total: list.length, page, size, list: list.slice((page - 1) * size, page * size) };
  }
  function delay(ms) { return new Promise(r => setTimeout(r, ms || 180 + Math.random() * 220)); }

const QM_MOCK = {
  categories, products, flashIds, contacts, chats, shopServices, serviceOf, serviceById, shopNameById, shopOwners, shopOf, kfLegacyToOwner,
  byId, byCategory, search, sortProducts, paginate, delay,
  bySub(catId) { const c = categories.find(x => x.id === catId); return c ? c.subs : []; },
  /* 秒杀结束时间：存 sessionStorage（与 store.js 的后端一致），
     读写都必须 try/catch —— 浏览器禁用存储时 localStorage 会直接抛异常，
     而调用方（HomeView 的 loadFlash）没有 try，会导致首页秒杀与猜你喜欢整体不渲染 */
  getFlashEnd() {
    const KEY = 'qm_v2_flash_end';
    let end = 0;
    try { end = Number(sessionStorage.getItem(KEY) || 0); } catch (e) { end = 0; }
    if (!end || end < Date.now()) end = Date.now() + 2 * 3600e3;
    QM_MOCK.setFlashEnd(end);
    return end;
  },
  setFlashEnd(end) {
    try { sessionStorage.setItem('qm_v2_flash_end', String(end)); } catch (e) { /* 存储不可用时忽略 */ }
  }
};

export default QM_MOCK;
