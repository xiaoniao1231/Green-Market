package org.web03.controller;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.web03.pojo.CartItem.CartAddRequest;
import org.web03.pojo.CartItem.CartDeleteRequest;
import org.web03.pojo.CartItem.CartSkuUpdateRequest;
import org.web03.pojo.CartItem.CartUpdateRequest;
import org.web03.pojo.Result;
import org.web03.service.CartService;

@Slf4j
@RestController
@RequestMapping("/cart")
public class CartController {
    @Autowired
    private CartService cartService;

    // 获取当前用户购物车列表
    @GetMapping
    public Result list() {
        return Result.success(cartService.list());
    }

    // 加入购物车
    @PostMapping
    public Result add(@RequestBody CartAddRequest cartAddRequest) {
        cartService.add(cartAddRequest);
        return Result.success();
    }

    // 修改数量
    @PutMapping("/items")
    public Result updateQty(@RequestBody CartUpdateRequest cartUpdateRequest) {
        cartService.updateQty(cartUpdateRequest);
        return Result.success();
    }

    // 批量删除
    @DeleteMapping("/items")
    public Result delete(@RequestBody CartDeleteRequest cartDeleteRequest) {
        cartService.delete(cartDeleteRequest);
        return Result.success();
    }

    // 修改款式
    @PutMapping("/items/sku")
    public Result updateSku(@RequestBody CartSkuUpdateRequest cartSkuUpdateRequest) {
        cartService.updateSku(cartSkuUpdateRequest);
        return Result.success();
    }

    // 清空购物车
    @DeleteMapping
    public Result clear() {
        cartService.clear();
        return Result.success();
    }
}
