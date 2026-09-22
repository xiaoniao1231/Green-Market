package org.web03.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.web03.pojo.Product.ProductsCheck;
import org.web03.pojo.Product.ProductRequest;
import org.web03.pojo.Result;
import org.web03.service.ProductService;

import java.util.Map;

/**
 * 店家中心
 */
@Slf4j
@RestController
@RequestMapping("/seller/products")
public class SellerProductController {

    @Autowired
    private ProductService productService;

    // 获取商品列表
    @GetMapping
    public Result list(ProductsCheck productsCheck) {
        return Result.success(productService.sellerList(productsCheck));
    }


    // 创建商品
    @PostMapping
    public Result create(@RequestBody ProductRequest productRequest){
        return Result.success(productService.create(productRequest));
    }

    //删除
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id){
        return Result.success(productService.delete(id));
    }

    //更新
    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @RequestBody ProductRequest request) {
        return Result.success(productService.update(id, request));
    }

    // 上下架
    @PutMapping("/{id}/status")
    public Result setStatus(@PathVariable Integer id, @RequestBody Map<String, Boolean> body) {
        return Result.success(productService.setStatus(id, body.get("onSale")));
    }

    // 获取商品详情
    @GetMapping("/{id}")
    public Result detail(@PathVariable Integer id) {
        return Result.success(productService.getForEdit(id));
    }

}
