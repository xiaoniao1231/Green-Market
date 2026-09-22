package org.web03.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.web03.pojo.Favorite.FavoriteAddRequest;
import org.web03.pojo.Favorite.FavoriteFanHui;
import org.web03.pojo.Result;
import org.web03.service.FavoriteService;

/**
 * 收藏夹控制器
 */

@Slf4j
@RestController
@RequestMapping("/favorites")
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    //获取当前用户收藏夹列表
    @GetMapping
    public Result list(FavoriteFanHui favoriteFanHui){
        return Result.success(favoriteService.list(favoriteFanHui));
    }


    //添加收藏
    @PostMapping
    public Result add(@RequestBody FavoriteAddRequest favoriteAddRequest){
        favoriteService.add(favoriteAddRequest);
        return Result.success();
    }

    //取消收藏
    @DeleteMapping("/{productId}")
    public Result delete(@PathVariable Integer productId){
        favoriteService.remove(productId);
        return Result.success();
    }

    //清空收藏
    @DeleteMapping
    public Result deleteAll(){
        favoriteService.clear();
        return Result.success();
    }



}
