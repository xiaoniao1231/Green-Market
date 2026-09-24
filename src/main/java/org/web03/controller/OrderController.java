package org.web03.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.web03.pojo.Order.OrderCreateRequest;
import org.web03.pojo.Result;
import org.web03.service.OrderService;

import java.util.Collections;
import java.util.Map;

/**
 *买家-----订单模块的控制器类
 */

@Slf4j
@RestController
@RequestMapping("/orders")
public class OrderController {
    @Autowired
    private OrderService orderService;

    //获取订单列表
    @GetMapping
    public Result list(@RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "20") Integer size){
        return Result.success(orderService.list(status, page, size));
    }

    //获取各状态订单
    @GetMapping("/counts")
    public Result counts(){
        return Result.success(orderService.counts());
    }

    // 订单详情
    @GetMapping("/{orderId}")
    public Result get(@PathVariable Integer orderId) {
        return Result.success(orderService.get(orderId));
    }

    //创建订单
    @PostMapping
    public Result create(@RequestBody OrderCreateRequest request){
        return Result.success(orderService.create(request));
    }

    //批量支付
    @PostMapping("/pay")
    public Result payBatch(@RequestBody Map<String, String> body){
        String payNo = body == null ? null : body.get("payNo");
        return Result.success(Collections.singletonMap("paid", orderService.payBatch(payNo)));
    }

    //支付订单
    @PostMapping("/{orderId}/pay")
    public Result pay(@PathVariable Integer orderId){
        orderService.pay(orderId);
        return Result.success();
    }

    //取消订单
    @PostMapping("/{orderId}/cancel")
    public Result cancel(@PathVariable Integer orderId){
        orderService.cancel(orderId);
        return Result.success();
    }


    // 确认收货
    @PostMapping("/{orderId}/confirm")
    public Result confirm(@PathVariable Integer orderId) {
        orderService.confirm(orderId);
        return Result.success();
    }

    // 提醒发货
    @PostMapping("/{orderId}/remind")
    public Result remind(@PathVariable Integer orderId) {
        return Result.success(orderService.remind(orderId));
    }

    // 物流轨迹
    @GetMapping("/{orderId}/logistics")
    public Result logistics(@PathVariable Integer orderId) {
        return Result.success(orderService.logistics(orderId));
    }


}
