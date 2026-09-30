package org.web03.service;

import org.springframework.web.multipart.MultipartFile;
import org.web03.pojo.Review.*;

import java.util.List;
import java.util.Map;
public interface ReviewService {
    ReviewPageVO pending(ReviewCheck check);

    List<ReviewVO> mine();

    ReviewVO create(ReviewCreateRequest request);

    Map<String, Object> uploadImage(MultipartFile file);

    ReviewVO append(Long reviewId, ProductReview request);

    ReviewVO reply(Long reviewId, ProductReview request);

    ReviewPageVO productReviews(ReviewCheck check);

    ReviewPageVO shopReviews(ReviewCheck check);

    List<Map<String, Object>> shopGroups();

    ReviewSummaryVO productRating(Integer productId);
}
