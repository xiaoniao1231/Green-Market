package org.web03.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.web03.pojo.Order.OrderItem;
import org.web03.pojo.Review.PendingReviewItem;
import org.web03.pojo.Review.PendingReviewOrder;
import org.web03.pojo.Review.ProductReview;
import org.web03.pojo.Review.ReviewCheck;
import org.web03.pojo.Review.ReviewSummaryVO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface ProductReviewMapper {
    // 已完成订单总数
    @Select("select COUNT(*) from orders  where user_id = #{userId} and status = 'done'")
    long countPendingOrders(String userId);

    // 待处理的评价订单列表（不包括订单条目）
    @Select("select id, order_no, finish_time from orders " +
            "where user_id = #{userId} and status = 'done' " +
            "order by finish_time desc, id desc limit #{offset}, #{size}")
    List<PendingReviewOrder> listPendingOrders(@Param("userId") String userId,
                                               @Param("offset") int offset,
                                               @Param("size") int size);

    // 订单中的商品评价列表（条目 + 是否已评价）
    List<PendingReviewItem> listItemsByOrders(List<Integer> orderIds);

    //订单信息校验：条目存在，属于当前账号，所属订单已完成
    @Select("select i.* from order_items i join orders o on o.id = i.order_id where i.id = #{orderItemId} " +
            "and o.user_id = #{userId} and o.status = 'done' limit 1")
    OrderItem findOrderItemById(@Param("orderItemId") Integer orderItemId, @Param("userId") String userId);

    //条目所属订单的状态（用于区分,条目不存在、不属于我与订单尚未完成的报错文案）
    @Select("select o.status from order_items i join orders o on o.id = i.order_id " +
            "where i.id = #{orderItemId} and o.user_id = #{userId}")
    String findOrderStatusByItem(@Param("orderItemId") Integer orderItemId, @Param("userId") String userId);

    //评价数量校验：避免重复评价
    @Select("select count(*) from product_reviews where order_item_id = #{orderItemId}")
    int countByOrderItem(Integer orderItemId);

    //商品所属店铺ID
    @Select("select shop_id from products where id = #{productId}")
    String findShopIdByProduct(Integer productId);

    //新增评价
    void insert(ProductReview review);

    //评价详情
    ProductReview findDetailById(Long id);

    //重新计算商品评分
    void recalcProductRating(Integer productId);

    //重新计算店铺评分
    void recalcShopScore(String shopId);

    //我的评价
    List<ProductReview> listMineAll(String userId);

    //按商品统计评价数量
    long countByProduct(ReviewCheck check);

    //按商品查询评价列表
    List<ProductReview> listByProduct(ReviewCheck check);

    //本店收到的评价总数（店家视角，支持 productId / score / replyStatus 筛选）
    long countByShop(ReviewCheck check);

    //本店收到的评价分页列表（店家视角，支持 productId / score / replyStatus 筛选）
    List<ProductReview> listByShop(ReviewCheck check);

    //本店商品评价统计：按商品分组，返回每个商品的 total / replied / unreplied / withAppend
    List<Map<String, Object>> listShopGroupStats(@Param("shopId") String shopId);

    //获取商品评分，评价数，好评率
    ReviewSummaryVO selectSummary(Integer productId);

    //获取商品评价分布
    List<Map<String, Object>> selectDistribution(Integer productId);

    //获取商品款式评价分布
    List<Map<String, Object>> selectSkuDistribution(Integer productId);

    //店铺当前评分
    BigDecimal findShopScore(String shopId);

    //评价详情
    ProductReview findById(Long id);

    //商家回复
    int updateReply(@Param("id") Long id, @Param("shopId") String shopId, @Param("content") String content);

    //追评
    int updateAppend(@Param("id") Long id, @Param("userId") String userId, @Param("content") String content);
}
