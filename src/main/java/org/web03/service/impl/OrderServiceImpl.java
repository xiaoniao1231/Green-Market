package org.web03.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.web03.exception.BusinessException;
import org.web03.mapper.EmpMapper;
import org.web03.mapper.OrderMapper;
import org.web03.mapper.ProductMapper;
import org.web03.mapper.SellerReminderMapper;
import org.web03.mapper.ShopMapper;
import org.web03.pojo.Messages.WsMessage;
import org.web03.pojo.Order.*;
import org.web03.pojo.Product.Product;
import org.web03.pojo.Shop.Shop;
import org.web03.service.OrderService;
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
    private SellerReminderMapper sellerReminderMapper;
    @Autowired
    private EmpMapper empMapper;
    @Autowired
    private ChatWebSocketHandler chatWebSocketHandler;

    //最大数量限制999
    private static final int MAX_QTY = 999;
    //包邮门槛
    private static final BigDecimal FREE_FREIGHT_THRESHOLD = new BigDecimal("50");
    //不满包邮门槛的运费
    private static final BigDecimal FREIGHT_FEE = new BigDecimal("5");
    //催发货冷却期(24小时)
    private static final int REMIND_COOLDOWN_HOURS = 24 * 60 * 60;
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

        //逐条校验商品、扣库存并定格下单快照
        List<OrderItem> items = new ArrayList<>();
        BigDecimal goodsAmount = BigDecimal.ZERO;// 整单商品总金额
        for (OrderCreateItem it : request.getItems()){
            if(it == null || it.getProductId() == null) throw new BusinessException("订单项不能为空");
            int qty = requireQuantity(it.getQty());
            Product p = productMapper.getPublicById(it.getProductId());
            if(p == null)throw new BusinessException("商品不存在或已下架");
            if(productMapper.deductStock(p.getId(), qty) == 0)throw new BusinessException("库存不足");

            OrderItem oi = new OrderItem();
            oi.setShopId(p.getShopId());
            oi.setProductId(p.getId());
            oi.setTitle(p.getTitle());
            oi.setSku(StringUtils.hasLength(it.getSku()) ? it.getSku().trim() : "默认");
            oi.setQty(qty);
            oi.setPrice(p.getPrice());
            oi.setArtImg(JsonUtils.skuImg(p.getSkus(), oi.getSku()));
            items.add(oi);
            goodsAmount = goodsAmount.add(p.getPrice().multiply(BigDecimal.valueOf(qty)));
        }

        //优惠券
        BigDecimal discount = BigDecimal.ZERO;// 优惠券优惠金额
        Map<String, Object> coupon = request.getCoupon();
        if (coupon != null && !coupon.isEmpty()) {
            Object amountObj = coupon.get("amount");
            if (amountObj == null) throw new BusinessException("优惠券参数无效");
            BigDecimal amount = new BigDecimal(String.valueOf(amountObj));
            Object thresholdObj = coupon.get("threshold");
            //校验使用门槛，抵扣金额不超商品金额（避免应付为负）
            if (thresholdObj != null && goodsAmount.compareTo(new BigDecimal(String.valueOf(thresholdObj))) < 0) {
                throw new BusinessException("优惠券不满足使用条件");
            }
            discount = amount.min(goodsAmount);
        }

        //按店铺分组
        Map<String, List<OrderItem>> byShop = new LinkedHashMap<>();
        for (OrderItem oi : items) {
            byShop.computeIfAbsent(oi.getShopId(), k -> new ArrayList<>()).add(oi);
        }
        List<String> shopIds = new ArrayList<>(byShop.keySet());

        //逐店生成子订单,运费按店独立计算,优惠券按商品金额占比分摊，最后一单吸收四舍五入差额，保证「各子单抵扣之和」恰好等于整单优惠
        String payNo = genPayNo();// 本次下单拆出的所有子订单共享
        List<OrderVO> orders = new ArrayList<>();
        BigDecimal sumDiscount = BigDecimal.ZERO;
        BigDecimal sumFreight = BigDecimal.ZERO;
        BigDecimal sumTotal = BigDecimal.ZERO;

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
            }

            orders.add(toVO(order, groupItems));
            sumDiscount = sumDiscount.add(groupDiscount);
            sumFreight = sumFreight.add(groupFreight);
            sumTotal = sumTotal.add(groupTotal);
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

    //批量支付：把同一次下单拆出的所有子订单一次付清（避免逐单支付留下「付了一半」的状态）
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
        log.info("取消订单: {} 订单号={}（已回补库存）", currentUser(), order.getOrderNo());
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
        /* 物流轨迹最前追加签收记录（最新在前，与订单页物流弹窗展示顺序一致） */
        List<Map<String, Object>> logistics = JsonUtils.parseList(order.getLogisticsJson());
        Map<String, Object> signed = new HashMap<>();
        signed.put("text", "包裹已签收，感谢您使用青集市");
        signed.put("time", LocalDateTime.now().format(TIME_FORMATTER));
        logistics.add(0, signed);
        orderMapper.updateLogistics(id, JsonUtils.toJson(logistics));
        log.info("确认收货: {} 订单号={}", currentUser(), order.getOrderNo());
    }

    //提醒发货：落库（累加次数）+ WebSocket 实时推送给店主，并返回提醒状态供买家端置灰按钮
    @Override
    @Transactional
    public Map<String, Object> remind(Integer id) {
        Order order = requireOwnOrder(id);
        if (!S_PAID.equals(order.getStatus())) throw new BusinessException("订单状态不允许提醒发货");

        String buyerId = currentUser();
        /* 订单涉及的店铺：拆单后恒为一个；保留集合语义以兼容拆单上线前产生的历史跨店订单 */
        List<String> shopIds = orderMapper.listItems(id).stream()
                .map(OrderItem::getShopId)
                .filter(StringUtils::hasLength)
                .distinct()
                .collect(Collectors.toList());
        if (shopIds.isEmpty()) throw new BusinessException("订单商品异常，无法提醒发货");

        for (String shopId : shopIds) {
            SellerReminder exist = sellerReminderMapper.findByOrder(id);
            /* 防骚扰：次数上限 + 冷却期。两项都在写库之前判断，命中即整笔回滚（不会留下半条记录） */
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
                /* 店铺被删或未绑定店主账号：跳过推送，但不让买家的提醒整体失败 */
                log.warn("提醒发货: 店铺 {} 不存在或未绑定店主，跳过推送（订单号={}）", shopId, order.getOrderNo());
                continue;
            }

            SellerReminder reminder = new SellerReminder();
            reminder.setOrderId(id);
            reminder.setShopId(shopId);
            reminder.setBuyerId(buyerId);
            reminder.setOwnerUserId(shop.getOwnerUserId());
            sellerReminderMapper.upsert(reminder);// 首次插入 count=1，之后累加

            /* 实时通道：店主开着页面立刻收到。不在线也无妨 —— 记录已落库，
               店家端下次打开订单列表照样能看到「催发货」角标 */
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
    public Map<String, Object> sellerList(String status, Integer page, Integer size) {
        String shopId = requireShopId();
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null ? 20 : Math.min(Math.max(size, 1), 100);
        String st = StringUtils.hasLength(status) ? status.trim() : null;
        // 店家端条目只取本店商品
        List<OrderVO> list = orderMapper.sellerList(shopId, st, (p - 1) * s, s)
                .stream().map(o -> toVO(o, orderMapper.listItemsByShop(o.getId(), shopId)))
                .collect(Collectors.toList());
        long total = orderMapper.sellerCount(shopId, st);
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
