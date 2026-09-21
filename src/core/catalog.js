/* =========================================================
   青集市 · core/catalog.js —— 商品分类字典（业务配置，不是演示数据）
   ---------------------------------------------------------
   后端商品表（products）用字符串字段 category / sub 存分类，没有独立的分类接口，
   因此这份分类树由前端维护，供以下场景共用：
     · 首页分类浮层（App.vue）与分类页导航（CategoryView）
     · 商品管理页的分类 / 子类下拉（SellerProductsView）
     · 分类页按一级分类筛选商品
   可用的分类取值必须与后端写入 products.category / products.sub 的值保持一致。
   ========================================================= */

/** 一级分类 + 其下的子类目 */
export const CATEGORIES = [
  { id: '数码科技', icon: '◈', subs: ['手机', '笔记本', '耳机音响', '智能穿戴', '摄影摄像'] },
  { id: '家居生活', icon: '⌂', subs: ['收纳整理', '家纺布艺', '厨房用品', '灯具', '香薰摆件'] },
  { id: '服饰鞋包', icon: '✦', subs: ['女装', '男装', '鞋靴', '箱包', '配饰'] },
  { id: '美妆个护', icon: '◌', subs: ['护肤', '彩妆', '洗护', '香水', '美妆工具'] },
  { id: '美食饮品', icon: '♨', subs: ['休闲零食', '咖啡茶饮', '冲调谷物', '生鲜果蔬'] },
  { id: '运动户外', icon: '◒', subs: ['健身器材', '跑步装备', '露营装备', '骑行运动', '球类'] },
  { id: '图书文具', icon: '▤', subs: ['文具', '手账本册', '小说文学', '学习教辅'] },
  { id: '宠物花植', icon: '❋', subs: ['猫狗主粮', '宠物玩具', '绿植盆栽', '水族用品'] }
];

/** 取某个一级分类的子类目列表（未知分类返回空数组） */
export function bySub(catId) {
  const c = CATEGORIES.find(x => x.id === catId);
  return c ? c.subs : [];
}

/** 取某个一级分类的完整定义（未知分类返回 null） */
export function catOf(catId) {
  return CATEGORIES.find(x => x.id === catId) || null;
}

export default { CATEGORIES, bySub, catOf };
