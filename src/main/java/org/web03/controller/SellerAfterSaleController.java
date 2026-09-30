package org.web03.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.web03.pojo.AfterSale.AfterSale;
import org.web03.pojo.Result;
import org.web03.service.AfterSaleService;

/**
 * 店家-----售后服务控制器
 */
@Slf4j
@RestController
@RequestMapping("/seller/after-sales")
public class SellerAfterSaleController {

    @Autowired
    private AfterSaleService afterSaleService;

    //本店售后列表
    @GetMapping
    public Result list(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String afterNo,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(afterSaleService.sellerList(status, afterNo, page, size));
    }

    //同意售后
    @PutMapping("/{afterSaleId}/approve")
    public Result approve(@PathVariable Integer afterSaleId,
                          @RequestBody(required = false) AfterSale request) {
        afterSaleService.approve(afterSaleId, request);
        return Result.success();
    }

    //拒绝售后
    @PutMapping("/{afterSaleId}/refuse")
    public Result refuse(@PathVariable Integer afterSaleId,
                         @RequestBody AfterSale request) {
        afterSaleService.refuse(afterSaleId, request);
        return Result.success();
    }

    //确认收货
    @PutMapping("/{afterSaleId}/receive")
    public Result receive(@PathVariable Integer afterSaleId,
                          @RequestBody(required = false) AfterSale request) {
        afterSaleService.receive(afterSaleId, request);
        return Result.success();
    }
}
