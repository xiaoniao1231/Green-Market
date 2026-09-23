package org.web03.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.web03.pojo.Result;
import org.web03.pojo.Shop.ShopRequest;
import org.web03.service.ShopService;

@Slf4j
@RestController
@RequestMapping("/shops")
public class ShopController {

    @Autowired
    private ShopService shopService;

    // 创建店铺
    @PostMapping
    public Result create(@RequestBody ShopRequest shopRequest){
        return Result.success(shopService.createShop(shopRequest));
    }

    // 上传店铺头像
    @PostMapping("/avatar")
    public Result uploadAvatar(@RequestParam("file") MultipartFile file){
        return Result.success(shopService.uploadAvatar(file));
    }

    // 获取店铺资料
    @GetMapping("/profile")
    public Result myProfile(){
        return Result.success(shopService.getMyShop());
    }

    // 更新店铺资料
    @PutMapping("/profile")
    public Result updateProfile(@RequestBody ShopRequest shopRequest){
        return Result.success(shopService.updateShop(shopRequest));
    }

    // 获取店铺资料
    @GetMapping("/{shopId}")
    public Result publicShop(@PathVariable String shopId) {
        return Result.success(shopService.getPublicShop(shopId));
    }

    // 关注店铺
    @PostMapping("/{shopId}/follow")
    public Result follow(@PathVariable String shopId) {
        return Result.success(shopService.follow(shopId));
    }

    // 取消关注店铺
    @DeleteMapping("/{shopId}/follow")
    public Result unfollow(@PathVariable String shopId) {
        return Result.success(shopService.unfollow(shopId));
    }

}
