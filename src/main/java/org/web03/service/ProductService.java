package org.web03.service;

import org.springframework.web.multipart.MultipartFile;
import org.web03.pojo.CheckProducts;
import org.web03.pojo.ProductRequest;
import org.web03.pojo.ProductVO;

import java.util.List;
import java.util.Map;

/**
 * 商品服务
 */
public interface ProductService {

    CheckProducts sellerList(CheckProducts checkProducts);

    /** 公开商品列表（买家端：仅在售、未删除，无需登录/开店） */
    CheckProducts publicList(CheckProducts checkProducts);

    ProductVO create(ProductRequest productRequest);

    String uploadImage(MultipartFile file);

    Map<String, Object> delete(Integer id);

    ProductVO  update(Integer id, ProductRequest request);

    Map<String, Object> setStatus(Integer id, Boolean onSale);

    ProductVO getForEdit(Integer id);

    ProductVO getDetail(Integer id);

    /** 相关推荐：同分类在售商品优先，不足时由其他在售商品补足（详情页「相关推荐」用） */
    List<ProductVO> related(Integer id, Integer size);

    Map<String, Object> flash();

    CheckProducts recommend(CheckProducts checkProducts);

    CheckProducts search(CheckProducts checkProducts);
}
