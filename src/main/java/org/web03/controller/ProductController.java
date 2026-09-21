package org.web03.controller;

import lombok.experimental.PackagePrivate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.web03.pojo.CheckProducts;
import org.web03.pojo.Result;
import org.web03.service.ProductService;

import java.util.Map;

/**
 * 买家端商品浏览
 */
@Slf4j
@RestController
public class ProductController {

    @Autowired
    private ProductService productService;

    // 获取商品列表
    @GetMapping("/products")
    public Result getProducts(CheckProducts checkProducts){
        return Result.success(productService.publicList(checkProducts));
    }


    //商品图片上传
    @PostMapping("/products/image")
    public Result uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.success(Map.of("url", productService.uploadImage(file)));
    }

    //商品详细
    @GetMapping("/products/{id}")
    public Result detaill(@PathVariable Integer id){
        return Result.success(productService.getDetail(id));
    }

    //相关推荐（详情页「相关推荐」栏；同分类优先，不足用其他在售商品补足）
    @GetMapping("/products/{id}/related")
    public Result related(@PathVariable Integer id,
                          @RequestParam(value = "size", required = false, defaultValue = "5") Integer size){
        return Result.success(productService.related(id, size));
    }

    //限时秒杀
    @GetMapping("/home/flash")
    public Result flash(){
        return Result.success(productService.flash());
    }

    //猜你喜欢
    @GetMapping("/home/recommend")
    public Result recommend(CheckProducts checkProducts){
        return Result.success(productService.recommend(checkProducts));
    }

    //商品模糊查询
    @GetMapping("/products/search")
    public Result search(CheckProducts checkProducts) {
        return Result.success(productService.search(checkProducts));
    }
}
