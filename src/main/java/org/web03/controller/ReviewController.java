package org.web03.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.web03.pojo.Result;
import org.web03.pojo.Review.ProductReview;
import org.web03.pojo.Review.ReviewCheck;
import org.web03.pojo.Review.ReviewCreateRequest;
import org.web03.service.ReviewService;


/**
 * 用户评价晒单
 */

@Slf4j
@RestController
@RequestMapping("/reviews")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;


    // 待处理的评价
    @GetMapping("/pending")
    public Result pending(ReviewCheck check) {
        return Result.success(reviewService.pending(check));
    }

    // 我的评价
    @GetMapping("/mine")
    public Result mine() {
        return Result.success(reviewService.mine());
    }

    // 店家收到的评价列表
    @GetMapping("/shop")
    public Result shopReviews(ReviewCheck check) {
        return Result.success(reviewService.shopReviews(check));
    }

    // 店家各商品的评价分组统计
    @GetMapping("/shop/groups")
    public Result shopGroups() {
        return Result.success(reviewService.shopGroups());
    }

    // 发表评价
    @PostMapping
    public Result create(@RequestBody ReviewCreateRequest request) {
        return Result.success(reviewService.create(request));
    }

    // 上传晒单图
    @PostMapping("/image")
    public Result uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.success(reviewService.uploadImage(file));
    }

    // 追评
    @PostMapping("/{reviewId}/append")
    public Result append(@PathVariable Long reviewId, @RequestBody ProductReview request) {
        return Result.success(reviewService.append(reviewId, request));
    }

    // 商家回复
    @PostMapping("/{reviewId}/reply")
    public Result reply(@PathVariable Long reviewId, @RequestBody ProductReview request) {
        return Result.success(reviewService.reply(reviewId, request));
    }


}
