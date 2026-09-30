package org.web03.controller;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.web03.pojo.Result;
import org.web03.pojo.Review.ReviewCheck;
import org.web03.service.ReviewService;

/**
 * 商品评价展示
 */

@Slf4j
@RestController
@RequestMapping("/products/{productId}")
public class ProductReviewsDisplay {

    @Autowired
    private ReviewService reviewService;

    //商品评价列表 + 评分汇总。
    @GetMapping("/reviews")
    public Result productReviews(@PathVariable Integer productId, ReviewCheck check) {
        check.setProductId(productId);
        return Result.success(reviewService.productReviews(check));
    }

    //商品评分汇总
    @GetMapping("/rating")
    public Result productRating(@PathVariable Integer productId) {
        return Result.success(reviewService.productRating(productId));
    }
}
