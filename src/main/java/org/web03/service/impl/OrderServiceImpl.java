package org.web03.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.web03.exception.BusinessException;
import org.web03.mapper.*;
import org.web03.pojo.Coupon.UserCoupon;
import org.web03.pojo.Messages.WsMessage;
import org.web03.pojo.Order.*;
import org.web03.pojo.Product.Product;
import org.web03.pojo.Shop.Shop;
import org.web03.service.FlashSaleService;
import org.web03.service.FlashUsageService;
import org.web03.service.OrderService;
import org.web03.utils.CouponUtils;
import org.web03.utils.CurrentHolder;
import org.web03.utils.JsonUtils;
import org.web03.websocket.ChatWebSocketHandler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单服务实现类
 */


@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private ShopMapper shopMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private CartMapper cartMapper;
    @Autowired
    private SellerReminderMapper sellerReminderMapper;
    @Autowired
    private EmpMapper empMapper;
    @Autowired
    private ChatWebSocketHandler chatWebSocketHandler;
    @Autowired
    private CouponMapper couponMapper;
    @Autowired
    private FlashSaleService flashSaleService;
    @Autowired
    private FlashUsageService flashUsageService;

    //最大数量限制999
    private static final int MAX_QTY = 999;
    //包邮门槛
    private static final BigDecimal FREE_FREIGHT_THRESHOLD = new BigDecimal("50");
    //不满包邮门槛的运费
    private static final BigDecimal FREIGHT_FEE = new BigDecimal("5");
    //催发货冷却期(24小时)：原值 24*60*60 被 plusHours 当小时用，实际冷却 10 年
    private static final int REMIND_COOLDOWN_HOURS = 24;
    //催发货次数上限：达到后当天不再受理，提示买家耐心等待
    private static final int MAX_REMIND_TIMES = 5;
    //时间格式化器
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 状态常量 */
    private static final String S_PENDING = "pending";// 待处理
    private static final String S_PAID = "paid";// 已支付
    private static final String S_SHIPPED = "shipped";// 已发货
    private static final String S_DONE = "done";// 完成
    private static final String S_CANCELED = "canceled";// 已取消

    //创建订单
    @Override
    @Transactional
    public OrderCreateResult create(OrderCreateRequest request) {
        String userId = currentUser();

        if (request == null || request.getItems() == null || request.getItems().isEmpty())
            throw new BusinessException("订单项不能为空");
        Map<String, Object> address = request.getAddress();
        if(address == null ) throw new BusinessException("收货地址不能为空");
        if(!StringUtils.hasLength(request.getPayMethod())) throw new BusinessException("支付方式不能为空");

        //当日秒杀资格：同一商品当天只能用一次 5 折，先占资格再算价
        Map<Integer, BigDecimal> flashDeal = new HashMap<>();
        for (OrderCreateItem it : request.getItems()) {
            if (it == null || it.getProductId() == null) continue;
            Integer pid = it.getProductId();
            if (flashDeal.containsKey(pid)) continue;
            Product p = productMapper.getPublicById(pid);
            if (p == null) continue;
            BigDecimal flash = flashSaleService.flashPrice(pid, p.getPrice());
            if (flash != null && flashUsageService.claim(userId, pid)) flashDeal.put(pid, flash);
        }

        //逐条校验商品、扣库存并定格下单快照
        List<OrderItem> items = new ArrayList<>();
        Set<Integer> flashClaimed = new HashSet<>();// 本单真正用掉秒杀资格的商品
        BigDecimal goodsAmount = BigDecimal.ZERO;// 整单商品总金额
        for (OrderCreateItem it : request.getItems()){
            if(it == null || it.getProductId() == null) throw new BusinessException("订单项不能为空");
            int qty = requireQuantity(it.getQty());
            Product p = productMapper.getPublicById(it.getProductId());
            if(p == null)throw new BusinessException("商品不存在或已下架");
            if(productMapper.deductStock(p.getId(), qty) == 0)throw new BusinessException("库存不足");

            String sku = StringUtils.hasLength(it.getSku()) ? it.getSku().trim() : "默认";
            // 秒杀只限 1 件：本单首次遇到该商品且抢到资格时，第 1 件按秒杀价，其余按到手价
            BigDecimal flash = flashDeal.get(p.getId());
            if (flash != null && flashClaimed.add(p.getId())) {
                items.add(orderItemOf(p, sku, 1, flash));
                goodsAmount = goodsAmount.add(flash);
                if (qty > 1) {
                    BigDecimal rest = p.getPrice().multiply(BigDecimal.valueOf(qty - 1));
                    items.add(orderItemOf(p, sku, qty - 1, p.getPrice()));
                    goodsAmount = goodsAmount.add(rest);
                }
            } else {
                items.add(orderItemOf(p, sku, qty, p.getPrice()));
                goodsAmount = goodsAmount.add(p.getPrice().multiply(BigDecimal.valueOf(qty)));
            }
        }

        //优惠券：整单统一一张；前端只回传 user_coupons.id，券的归属 / 状态 / 门槛一律以库为准
        BigDecimal discount = BigDecimal.ZERO;
        Map<String, Object> coupon = request.getCoupon();
        Integer userCouponId = null;
        if (coupon != null && !coupon.isEmpty()) {
            userCouponId = parseCouponId(coupon.get("id"));
            /* 传了券却给不出持有记录 id（旧格式只带 amount / threshold，或 id 是脏值）直接拒绝：
               否则会落成「订单快照写着用了券、抵扣却是 0」，用户以为自己已经享受优惠。 */
            if (userCouponId == null) throw new BusinessException("优惠券参数无效");
            UserCoupon uc = couponMapper.findUserCoupon(userCouponId, userId);
            if (uc == null || !"unused".equals(uc.getStatus())) throw new BusinessException("优惠券不可用");
            if (CouponUtils.isExpired(uc.getExpire())) throw new BusinessException("优惠券已过期");
            if (uc.getThreshold() != null && goodsAmount.compareTo(uc.getThreshold()) < 0)
                throw new BusinessException("优惠券不满足使用条件");
            discount = uc.getAmount().min(goodsAmount);
            coupon = JsonUtils.parseMap(JsonUtils.toJson(uc));   // 快照统一以库里的券为准
        }

        //按店铺分组
        Map<String, List<OrderItem>> byShop = new LinkedHashMap<>();
        // 遍历订单项，并按店铺分组加入集合中
        for (OrderItem oi : items) {
            byShop.computeIfAbsent(oi.getShopId(), k -> new ArrayList<>()).add(oi);
        }
        //  获取byShop中所有的店铺ID
        List<String> shopIds = new ArrayList<>(byShop.keySet());

        //逐店生成子订单,运费按店独立计算,优惠券按商品金额占比分摊，最后一单吸收四舍五入差额，保证「各子单抵扣之和」恰好等于整单优惠
        String payNo = genPayNo();// 整单统一支付单号
        List<OrderVO> orders = new ArrayList<>();// 存储子订单信息
        BigDecimal sumDiscount = BigDecimal.ZERO;// 优惠券优惠金额
        BigDecimal sumFreight = BigDecimal.ZERO;// 运费
        BigDecimal sumTotal = BigDecimal.ZERO;// 订单总金额

        for (int i = 0; i < shopIds.size(); i++) {
            String shopId = shopIds.get(i);
            List<OrderItem> groupItems = byShop.get(shopId);

            BigDecimal groupGoods = BigDecimal.ZERO;
            for (OrderItem oi : groupItems) {
                groupGoods = groupGoods.add(oi.getPrice().multiply(BigDecimal.valueOf(oi.getQty())));
            }
            BigDecimal groupFreight = groupGoods.compareTo(FREE_FREIGHT_THRESHOLD) >= 0
                    ? BigDecimal.ZERO : FREIGHT_FEE;

            BigDecimal groupDiscount;
            if (i == shopIds.size() - 1) {
                groupDiscount = discount.subtract(sumDiscount);// 最后一单兜住分摊差额
            } else {
                groupDiscount = discount.multiply(groupGoods).divide(goodsAmount, 2, RoundingMode.DOWN);
            }
            if (groupDiscount.compareTo(groupGoods) > 0) groupDiscount = groupGoods;// 防御：单店抵扣不超过本店商品金额

            BigDecimal groupTotal = groupGoods.subtract(groupDiscount).add(groupFreight);

            Order order = new Order();
            order.setOrderNo(genOrderNo());
            order.setPayNo(payNo);
            order.setUserId(userId);
            order.setStatus(S_PENDING);
            order.setGoodsAmount(groupGoods);
            order.setDiscount(groupDiscount);
            order.setFreight(groupFreight);
            order.setTotal(groupTotal);
            order.setPayMethod(request.getPayMethod());
            order.setRemark(request.getRemark() == null ? "" : request.getRemark().trim());
            order.setAddressJson(JsonUtils.toJson(address));
            order.setCouponJson(coupon == null || coupon.isEmpty() ? null : JsonUtils.toJson(coupon));
            // 回填下单时间
            order.setCreatedAt(LocalDateTime.now());
            if(orderMapper.insertOrder(order) == 0) throw new BusinessException("创建订单失败");

            for (OrderItem oi : groupItems) {
                oi.setOrderId(order.getId());
                orderMapper.insertItem(oi);
                // 秒杀资格挂到订单上，取消订单时可归还
                if (flashClaimed.contains(oi.getProductId())) {
                    flashUsageService.bindOrder(userId, oi.getProductId(), order.getId());
                }
            }

            orders.add(toVO(order, groupItems));
            sumDiscount = sumDiscount.add(groupDiscount);
            sumFreight = sumFreight.add(groupFreight);
            sumTotal = sumTotal.add(groupTotal);
        }

        //核销优惠券
        if (userCouponId != null && !orders.isEmpty()) {
            if (couponMapper.markUsed(userCouponId, userId, orders.get(0).getId()) == 0) {
                throw new BusinessException("优惠券不可用");
            }
        }

        for (OrderItem oi : items) {
            cartMapper.deleteOne(userId, oi.getProductId(), oi.getSku());
        }

        OrderCreateResult result = new OrderCreateResult();
        result.setPayNo(payNo);
        result.setOrders(orders);
        result.setOrderCount(orders.size());
        result.setGoodsAmount(goodsAmount);
        result.setDiscount(sumDiscount);
        result.setFreight(sumFreight);
        result.setTotalAmount(sumTotal);
        log.info("创建订单(按店铺拆单): {} 支付单号={} 子订单数={} 应付={}", userId, payNo, orders.size(), sumTotal);
        return result;
    }

    //下单快照行（秒杀价与原价会拆成两行）
    private OrderItem orderItemOf(Product p, String sku, int qty, BigDecimal price) {
        OrderItem oi = new OrderItem();
        oi.setShopId(p.getShopId());
        oi.setProductId(p.getId());
        oi.setTitle(p.getTitle());
        oi.setSku(sku);
        oi.setQty(qty);
        oi.setPrice(price);
        oi.setArtImg(JsonUtils.skuImg(p.getSkus(), sku));
        return oi;
    }

    //批量支付：把同一次下单拆出的所有子订单一次付清
    @Override
    @Transactional
    public int payBatch(String payNo) {
        String userId = currentUser();
        if (!StringUtils.hasLength(payNo)) throw new BusinessException("支付单号不能为空");
        List<Order> orders = orderMapper.listByPayNo(userId, payNo.trim());
        if (orders.isEmpty()) throw new BusinessException("支付单不存在或不属于当前账号");

        int paid = 0;
        for (Order o : orders) {
            if (S_PAID.equals(o.getStatus())) continue;// 幂等：已支付的子订单跳过
            if (!S_PENDING.equals(o.getStatus()))
                throw new BusinessException("订单 " + o.getOrderNo() + " 状态不允许支付");
            if (orderMapper.changeStatus(o.getId(), userId, S_PENDING, S_PAID) == 0)
                throw new BusinessException("订单 " + o.getOrderNo() + " 状态不允许支付");
            orderMapper.markPaidTime(o.getId());
            paid++;
        }
        log.info("批量支付: {} 支付单号={} 本次支付 {} 笔（共 {} 笔）", userId, payNo, paid, orders.size());
        return paid;
    }

    //支付订单
    @Override
    @Transactional
    public void pay(Integer id) {
        Order order = requireOwnOrder(id);
        if (S_PAID.equals(order.getStatus())) return; // 幂等：已支付直接成功
        if (!S_PENDING.equals(order.getStatus())) throw new BusinessException("订单状态不允许支付");
        if (orderMapper.changeStatus(id, currentUser(), S_PENDING, S_PAID) == 0) {
            throw new BusinessException("订单状态不允许支付");
        }
        orderMapper.markPaidTime(id);
        log.info("支付订单: {} 订单号={}", currentUser(), order.getOrderNo());
    }

    //取消订单
    @Override
    @Transactional
    public void cancel(Integer id) {
        Order order = requireOwnOrder(id);
        if (S_CANCELED.equals(order.getStatus())) return; // 幂等：已取消直接成功
        if (!S_PENDING.equals(order.getStatus())) throw new BusinessException("订单状态不允许取消");
        if (orderMapper.changeStatus(id, currentUser(), S_PENDING, S_CANCELED) == 0) {
            throw new BusinessException("订单状态不允许取消");
        }
        for (OrderItem it : orderMapper.listItems(id)) {
            productMapper.restoreStock(it.getProductId(), it.getQty());
        }
        flashUsageService.releaseByOrder(id);   // 归还本单占用的秒杀资格
        restoreCoupon(order);
        log.info("取消订单: {} 订单号={}（已回补库存）", currentUser(), order.getOrderNo());
    }

    // 取消订单退券：同一次下单拆出的子订单全部取消后，把券退回未使用
    private void restoreCoupon(Order order) {
        Map<String, Object> c = JsonUtils.parseMap(order.getCouponJson());
        if (c == null || c.get("id") == null) return;
        if (StringUtils.hasLength(order.getPayNo())) {
            for (Order o : orderMapper.listByPayNo(order.getUserId(), order.getPayNo())) {
                if (!S_CANCELED.equals(o.getStatus())) return;
            }
        }
        if (couponMapper.restoreCoupon(Integer.valueOf(String.valueOf(c.get("id"))), order.getUserId()) > 0) {
            log.info("取消订单退回优惠券: {} 持有记录ID={}", order.getUserId(), c.get("id"));
        }
    }

    //查询订单列表
    @Override
    public Map<String, Object> list(String status, Integer page, Integer size) {
        String userId = currentUser();
        String st = StringUtils.hasLength(status) ? status.trim() : null;
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null ? 20 : Math.min(Math.max(size, 1), 100);
        List<OrderVO> list = orderMapper.listByUser(userId, st, (p - 1) * s, s)
                .stream().map(this::toVO).collect(Collectors.toList());
        long total = orderMapper.countByUser(userId, st);
        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("page", p);
        data.put("size", s);
        data.put("list", list);
        return data;
    }

    //查询各订单状态数量
    @Override
    public Map<String, Object> counts() {
        String userId = currentUser();
        Map<String, Object> data = new HashMap<>();
        data.put("all", orderMapper.countByUser(userId, null));
        for (String st : Arrays.asList(S_PENDING, S_PAID, S_SHIPPED, S_DONE, S_CANCELED)) {
            data.put(st, orderMapper.countByUser(userId, st));
        }
        return data;
    }

    //查询订单详情
    @Override
    public OrderVO get(Integer id) {
        Order order = requireOwnOrder(id);
        OrderVO vo = toVO(order);
        SellerReminder reminder = sellerReminderMapper.findByOrder(id);
        if (reminder != null) {
            vo.setRemindCount(reminder.getRemindCount());
            vo.setLastRemindTime(formatTime(reminder.getLastRemindTime()));
        }
        return vo;
    }

    //确认收货
    @Override
    @Transactional
    public void confirm(Integer id) {
        Order order = requireOwnOrder(id);
        if (S_DONE.equals(order.getStatus())) return; // 幂等：已完成直接成功
        if (!S_SHIPPED.equals(order.getStatus())) throw new BusinessException("订单状态不允许确认收货");
        if (orderMapper.changeStatus(id, currentUser(), S_SHIPPED, S_DONE) == 0) {
            throw new BusinessException("订单状态不允许确认收货");
        }
        if (orderMapper.markFinishTime(id) == 0) throw new BusinessException("确认收货失败");
        for (OrderItem it : orderMapper.listItems(id)) {
            productMapper.increaseSales(it.getProductId(), it.getQty());
        }
        //物流轨迹最前追加签收记录
        List<Map<String, Object>> logistics = JsonUtils.parseList(order.getLogisticsJson());
        Map<String, Object> signed = new HashMap<>();
        signed.put("text", "包裹已签收，感谢您使用玉子市场");
        signed.put("time", LocalDateTime.now().format(TIME_FORMATTER));
        logistics.add(0, signed);
        orderMapper.updateLogistics(id, JsonUtils.toJson(logistics));
        log.info("确认收货: {} 订单号={}", currentUser(), order.getOrderNo());
    }

    //提醒发货
    @Override
    @Transactional
    public Map<String, Object> remind(Integer id) {
        Order order = requireOwnOrder(id);
        if (!S_PAID.equals(order.getStatus())) throw new BusinessException("订单状态不允许提醒发货");

        String buyerId = currentUser();
        //订单涉及的店铺：拆单后恒为一个；保留集合语义以兼容拆单上线前产生的历史跨店订单
        List<String> shopIds = orderMapper.listItems(id).stream()
                .map(OrderItem::getShopId)
                .filter(StringUtils::hasLength)
                .distinct()
                .toList();
        if (shopIds.isEmpty()) throw new BusinessException("订单商品异常，无法提醒发货");

        for (String shopId : shopIds) {
            SellerReminder exist = sellerReminderMapper.findByOrder(id);
            //防骚扰
            if (exist != null && exist.getRemindCount() != null && exist.getRemindCount() >= MAX_REMIND_TIMES) {
                throw new BusinessException("已提醒过 " + MAX_REMIND_TIMES + " 次，请耐心等待店家发货");
            }
            if (exist != null && exist.getLastRemindTime() != null) {
                LocalDateTime next = exist.getLastRemindTime().plusHours(REMIND_COOLDOWN_HOURS);
                if (LocalDateTime.now().isBefore(next)) {
                    throw new BusinessException("已提醒过，请于 " + TIME_FORMATTER.format(next) + " 后再试");
                }
            }

            Shop shop = shopMapper.findById(shopId);
            if (shop == null || !StringUtils.hasLength(shop.getOwnerUserId())) {
                //店铺被删或未绑定店主账号
                log.warn("提醒发货: 店铺 {} 不存在或未绑定店主，跳过推送（订单号={}）", shopId, order.getOrderNo());
                continue;
            }

            SellerReminder reminder = new SellerReminder();
            reminder.setOrderId(id);
            reminder.setShopId(shopId);
            reminder.setBuyerId(buyerId);
            reminder.setOwnerUserId(shop.getOwnerUserId());
            sellerReminderMapper.upsert(reminder);// 首次插入 count=1，之后累加

            // 实时通道
            chatWebSocketHandler.pushTo(shop.getOwnerUserId(), "SELLER_REMIND",
                    new WsMessage("remind-" + id + "-" + System.currentTimeMillis(),
                            buyerId, empMapper.findNicknameByUserId(buyerId),
                            shop.getOwnerUserId(),
                            "买家提醒你尽快为订单 " + order.getOrderNo() + " 发货",
                            LocalDateTime.now().format(TIME_FORMATTER)));
        }

        SellerReminder latest = sellerReminderMapper.findByOrder(id);
        log.info("提醒发货: 买家 {} 催订单 {}（累计 {} 次）", buyerId, order.getOrderNo(),
                latest == null ? 1 : latest.getRemindCount());

        Map<String, Object> data = new HashMap<>();
        data.put("reminded", true);
        data.put("orderNo", order.getOrderNo());
        if (latest != null) {
            data.put("remindCount", latest.getRemindCount());
            data.put("lastRemindTime", formatTime(latest.getLastRemindTime()));
            data.put("nextRemindTime", formatTime(latest.getLastRemindTime().plusHours(REMIND_COOLDOWN_HOURS)));
        }
        return data;
    }

    //查询物流信息
    @Override
    public Map<String, Object> logistics(Integer id) {
        Order order = requireOwnOrder(id);
        return Collections.singletonMap("list", JsonUtils.parseList (order.getLogisticsJson()));
    }

    /* ------------------------------------- 店家 ----------------------------------- */

    //查询店铺订单列表
    @Override
    public Map<String, Object> sellerList(String status, Integer page, Integer size, String orderNo) {
        String shopId = requireShopId();
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null ? 20 : Math.min(Math.max(size, 1), 100);
        String st = StringUtils.hasLength(status) ? status.trim() : null;
        // 订单号模糊查询（店家端搜索框）：空串按「不筛选」处理，避免 like '%%' 白跑一趟
        String no = StringUtils.hasLength(orderNo) ? orderNo.trim() : null;
        // 店家端条目只取本店商品
        List<OrderVO> list = orderMapper.sellerList(shopId, st, (p - 1) * s, s, no)
                .stream().map(o -> toVO(o, orderMapper.listItemsByShop(o.getId(), shopId)))
                .collect(Collectors.toList());
        long total = orderMapper.sellerCount(shopId, st, no);
        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("page", p);
        data.put("size", s);
        data.put("list", list);
        return data;
    }

    //发货
    @Override
    @Transactional
    public void ship(Integer orderId) {
        String shopId = requireShopId();
        Order order = orderMapper.findById(orderId);
        if (order == null || orderMapper.countItemsByShop(orderId, shopId) == 0) {
            throw new BusinessException("订单不存在或不属于本店铺");
        }
        /* 生成两条演示物流轨迹（最新在前；时间格式 yyyy-MM-dd HH:mm:ss） */
        List<Map<String, Object>> logistics = new ArrayList<>();
        Map<String, Object> l1 = new HashMap<>();
        l1.put("text", "包裹已到达【杭州转运中心】");
        l1.put("time", LocalDateTime.now().minusHours(2).format(TIME_FORMATTER));
        logistics.add(l1);
        Map<String, Object> l2 = new HashMap<>();
        l2.put("text", "卖家已发货，等待揽收");
        l2.put("time", LocalDateTime.now().minusHours(20).format(TIME_FORMATTER));
        logistics.add(l2);
        /* paid→shipped（幂等：已 shipped 直接成功；ship_time 保留首次发货时间） */
        if (orderMapper.sellerShip(orderId, JsonUtils.toJson(logistics)) == 0) {
            throw new BusinessException("当前状态不可发货");
        }
        /* 发货即视为处理掉这笔订单的催发货：店家端「被催」角标随之消失 */
        sellerReminderMapper.markHandled(orderId);
        log.info("店家发货: 店铺 {} 订单号={}", shopId, order.getOrderNo());
    }

    //获取当前用户
    private String currentUser() {
        String userId = CurrentHolder.getCurrentUserId();
        if (!StringUtils.hasLength(userId)) throw new BusinessException("未登录或登录已失效");
        return userId;
    }

    //获取当前用户店铺id
    private String requireShopId() {
        String shopId = shopMapper.findShopIdByOwner(currentUser());
        if (!StringUtils.hasLength(shopId)) throw new BusinessException("当前账号未开店");
        return shopId;
    }

    //订单必须存在且属于当前用户
    private Order requireOwnOrder(Integer id) {
        if (id == null) throw new BusinessException("订单不存在");
        Order order = orderMapper.findByIdAndUser(id, currentUser());
        if (order == null) throw new BusinessException("订单不存在");
        return order;
    }

    //验证数量：1-999
    private int requireQuantity(Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > MAX_QTY) {
            throw new BusinessException("数量必须在1-" + MAX_QTY + "之间");
        }
        return quantity;
    }

    //请求体里的优惠券持有记录 id：缺失 / 空白 / 非数字一律返回 null（由调用方给出业务提示）
    private Integer parseCouponId(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        if (!StringUtils.hasLength(s)) return null;
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    //数据库行 → 接口对象
    private OrderVO toVO(Order o) {
        return toVO(o, orderMapper.listItems(o.getId()));
    }

    //数据库行 + 指定条目 → 接口对象
    private OrderVO toVO(Order o, List<OrderItem> items) {
        OrderVO vo = new OrderVO();
        vo.setId(o.getId());
        vo.setOrderNo(o.getOrderNo());
        vo.setPayNo(o.getPayNo());
        vo.setStatus(o.getStatus());
        vo.setCreateTime(formatTime(o.getCreatedAt()));
        vo.setPayTime(formatTime(o.getPayTime()));
        vo.setShipTime(formatTime(o.getShipTime()));
        vo.setFinishTime(formatTime(o.getFinishTime()));
        vo.setItems(items.stream().map(this::toItemVO).collect(Collectors.toList()));
        vo.setAddress(JsonUtils.parseMap(o.getAddressJson()));
        vo.setCoupon(JsonUtils.parseMap(o.getCouponJson()));
        vo.setPayMethod(o.getPayMethod());
        vo.setRemark(o.getRemark());
        vo.setGoodsAmount(o.getGoodsAmount());
        vo.setDiscount(o.getDiscount());
        vo.setFreight(o.getFreight());
        vo.setTotal(o.getTotal());
        vo.setLogistics(JsonUtils.parseList(o.getLogisticsJson()));
        /* 催发货状态：列表查询由 LEFT JOIN 填充，普通单条查询为 null（前端按 null 视为未提醒） */
        vo.setRemindCount(o.getRemindCount());
        vo.setLastRemindTime(formatTime(o.getLastRemindTime()));
        return vo;
    }

    //条目 → 条目 VO
    private OrderItemVO toItemVO(OrderItem it) {
        OrderItemVO vo = new OrderItemVO();
        vo.setProductId(it.getProductId());
        vo.setTitle(it.getTitle());
        vo.setSku(it.getSku());
        vo.setQty(it.getQty());
        vo.setPrice(it.getPrice());
        /* art_img 列存的就是一张图片地址，直接构建展示图；不要再当 skus JSON 解析 */
        vo.setArt(JsonUtils.buildArtFromImg(it.getArtImg()));
        return vo;
    }

    //格式化时间
    private String formatTime(LocalDateTime t) {
        return t == null ? null : TIME_FORMATTER.format(t);
    }

    //订单号：QM + yyyyMMdd + 6 位随机大写字母数字
    public static String genOrderNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String rand = new Random().ints(0, 36).limit(6)
                .mapToObj(i -> Character.toString("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt(i)))
                .collect(Collectors.joining());
        return "QM" + date + rand;
    }

    //支付单号：PM + yyyyMMdd + 6 位随机大写字母数字（与订单号同构，便于人工识别与对账）
    public static String genPayNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String rand = new Random().ints(0, 36).limit(6)
                .mapToObj(i -> Character.toString("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt(i)))
                .collect(Collectors.joining());
        return "PM" + date + rand;
    }
}
