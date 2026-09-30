package org.web03.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.web03.pojo.AfterSale.AfterSale;
import org.web03.pojo.AfterSale.AfterSaleVO;
import org.web03.pojo.Result;
import org.web03.service.AfterSaleService;

import java.util.Collections;


/**
 * 买家-----售后服务控制器
 */
@Slf4j
@RestController
@RequestMapping("/after-sales")
public class AfterSaleController {
    @Autowired
    private AfterSaleService afterSaleService;

    //我的全部售后单
    @GetMapping
    public Result list() {
        return Result.success(afterSaleService.listAll());
    }

    //售后详情
    @GetMapping("/{afterSaleId}")
    public Result get(@PathVariable Integer afterSaleId) {
        return Result.success(afterSaleService.get(afterSaleId));
    }

    //申请售后
    @PostMapping
    public Result create(@RequestBody AfterSaleVO request) {
        return Result.success(afterSaleService.create(request));
    }

    //上传凭证图
    @PostMapping("/image")
    public Result uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.success(Collections.singletonMap("url", afterSaleService.uploadImage(file)));
    }

    //撤销申请
    @PostMapping("/{afterSaleId}/cancel")
    public Result cancel(@PathVariable Integer afterSaleId) {
        afterSaleService.cancel(afterSaleId);
        return Result.success();
    }

    //填写寄回物流
    @PostMapping("/{afterSaleId}/ship")
    public Result ship(@PathVariable Integer afterSaleId, @RequestBody AfterSale request) {
        afterSaleService.ship(afterSaleId, request);
        return Result.success();
    }
}
