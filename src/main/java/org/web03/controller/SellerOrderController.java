package org.web03.controller;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.web03.pojo.Result;
import org.web03.service.OrderService;

/**
 *店家-----订单模块的控制器类
 */

@Slf4j
@RestController
@RequestMapping("/seller/orders")
public class SellerOrderController {
    @Autowired
    private OrderService orderService;


    //订单列表
    @GetMapping
    public Result list(@RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(orderService.sellerList(status, page, size));
    }


    //订单发货
    @PutMapping("/{orderId}/ship")
    public Result ship(@PathVariable Integer orderId) {
        orderService.ship(orderId);
        return Result.success();
    }
}
