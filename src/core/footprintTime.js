/* =========================================================
   玉子市场 · core/footprintTime.js —— 浏览足迹的时间工具（纯函数，无 Vue 依赖）
   ---------------------------------------------------------
   足迹页的两个能力都建立在「时间」上，抽成纯函数便于单测
   （测试脚本：scripts/test-footprints.mjs，node 直接 import 本模块跑断言）：

   ① 按天分组：dayKey / dayLabel / groupByDay
      —— 输出「今天 · 周X / 昨天 · 周X / M月D日 · 周X / yyyy年M月D日 · 周X」；
   ② 本地时间筛选：normalizeRange / rangeSince / filterByRange
      —— 自然日边界，与后端 `GET /footprints?range=` 口径严格一致
         （见 docs/历史足迹接口文档.md 1.2）：

         | range | 时间下界 since            |
         | all   | 无（不过滤）               |
         | today | 今天 00:00:00             |
         | week  | 今天往前 6 天的 00:00:00   |
         | month | 今天往前 29 天的 00:00:00  |

   时区：一律按**运行环境本地时区**算自然日（浏览器里就是用户时区），
   与后端 LocalDate.now().atStartOfDay() 的口径对齐。
   所有涉及"今天"的函数都接受 now 参数（毫秒），便于测试注入固定时间。
   ========================================================= */

export const DAY_MS = 86400e3;
export const WEEK_CN = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

/** 时间筛选枚举：key 与后端 range 一一对应（页面 chips 直接渲染这个数组） */
export const RANGE_OPTIONS = [
  { key: 'all', label: '全部' },
  { key: 'today', label: '今天' },
  { key: 'week', label: '近 7 天' },
  { key: 'month', label: '近 30 天' }
];

const pad = v => String(v).padStart(2, '0');

/** 本地自然日的 00:00:00（毫秒） */
export function startOfDay(now = Date.now()) {
  const d = new Date(now);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

/** 分组键：yyyy-MM-dd（本地时区）；时间缺失 / 非法一律归入 'unknown' */
export function dayKey(ts) {
  if (!ts) return 'unknown';
  const d = new Date(ts);
  if (isNaN(d.getTime())) return 'unknown';
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
}

/**
 * 分组标题：今天 · 周X / 昨天 · 周X / M月D日 · 周X / yyyy年M月D日 · 周X / 时间未知。
 * 用「两个自然日 00:00 的差值」判断今天 / 昨天：跨夏令时那天是 23 或 25 小时，
 * 直接除以 86400e3 会得到 0.96 / 1.04，故用 Math.round 兜住。
 * 未来时间（客户端时钟偏差）diffDays < 0，落到「M月D日」分支，不会崩。
 */
export function dayLabel(key, now = Date.now()) {
  if (key === 'unknown') return '时间未知';
  const parts = String(key).split('-').map(Number);
  const y = parts[0], m = parts[1], d = parts[2];
  if (!y || !m || !d) return '时间未知';
  const todayStart = startOfDay(now);
  const thatStart = new Date(y, m - 1, d).getTime();
  if (isNaN(thatStart)) return '时间未知';
  const diffDays = Math.round((todayStart - thatStart) / DAY_MS);
  const w = WEEK_CN[new Date(thatStart).getDay()];
  if (diffDays === 0) return '今天 · ' + w;
  if (diffDays === 1) return '昨天 · ' + w;
  if (y === new Date(todayStart).getFullYear()) return m + '月' + d + '日 · ' + w;
  return y + '年' + m + '月' + d + '日 · ' + w;
}

/** range 归一化：非四个枚举（含 null / 空串 / 乱填）一律按 all —— 与后端静默回落同口径 */
export function normalizeRange(range) {
  return RANGE_OPTIONS.some(r => r.key === range) ? range : 'all';
}

/** range → 时间下界（毫秒）；all 返回 0 表示「不过滤」 */
export function rangeSince(range, now = Date.now()) {
  switch (normalizeRange(range)) {
    case 'today': return startOfDay(now);
    case 'week': return startOfDay(now) - 6 * DAY_MS;
    case 'month': return startOfDay(now) - 29 * DAY_MS;
    default: return 0;
  }
}

/**
 * 本地时间筛选：保留 browseTime >= since 的记录。
 * 时间未知（browseTime 为 0 / undefined / NaN）只会在「全部」下出现，筛选后被过滤掉。
 */
export function filterByRange(list, range, now = Date.now()) {
  const since = rangeSince(range, now);
  if (!since) return list || [];
  return (list || []).filter(p => Number(p && p.browseTime) >= since);
}

/**
 * 按天分组：Map 保持插入顺序 → 组间「最近的日期在前」，组内顺序也不变
 * （接口已按最近浏览倒序返回，这里**只切段不重排**）。
 */
export function groupByDay(list, now = Date.now()) {
  const map = new Map();
  (list || []).forEach(p => {
    const k = dayKey(p && p.browseTime);
    if (!map.has(k)) map.set(k, []);
    map.get(k).push(p);
  });
  return Array.from(map, ([key, items]) => ({ key, label: dayLabel(key, now), items }));
}
