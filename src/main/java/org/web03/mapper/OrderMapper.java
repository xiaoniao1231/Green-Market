package org.web03.mapper;


import org.apache.ibatis.annotations.*;
import org.web03.pojo.Order.Order;
import org.web03.pojo.Order.OrderItem;

import java.util.List;


@Mapper
public interface OrderMapper {

    // 插入订单
    int insertOrder(Order order);

    // 插入订单条目
    int insertItem(OrderItem oi);

    // 列出订单条目
    @Select("select * from order_items where order_id = #{orderId} order by id")
    List<OrderItem> listItems(Integer id);

    // 修改订单状态
    @Update("update orders set status = #{to}, updated_at = now() " +
            "where id = #{id} and user_id = #{userId} and status = #{from}")
    int changeStatus(@Param("id") Integer id, @Param("userId") String userId,
                     @Param("from") String from, @Param("to") String to);

    // 标记支付时间
    @Update("update orders set pay_time = now(), updated_at = now() where id = #{id}")
    void markPaidTime(Integer id);

    // 买家---根据id和用户查找订单
    @Select("select * from orders where id = #{id} and user_id = #{userId}")
    Order findByIdAndUser(@Param("id") Integer id, @Param("userId") String userId);

    // 买家---根据用户和状态查找订单
    List<Order> listByUser(@Param("userId") String userId, @Param("status") String status,
                                  @Param("offset") int offset, @Param("size") int size);

    // 买家---统计订单数量
    long countByUser(@Param("userId") String userId, @Param("status") String status);

    // 买家---按支付单号取同批拆出的所有子订单（批量支付一次付清，见 OrderMapper.xml）
    List<Order> listByPayNo(@Param("userId") String userId, @Param("payNo") String payNo);

    // 写完成时间
    @Update("update orders set finish_time = now(), updated_at = now() where id = #{id}")
    int markFinishTime(Integer id);

    // 覆盖物流轨迹
    @Update("update orders set logistics_json = #{json}, updated_at = now() where id = #{id}")
    void updateLogistics(@Param("id") Integer id, @Param("json") String json);

    /* ------------------------------------- 店家 ----------------------------------- */

    // 卖家---根据店铺和状态查找订单
    List<Order> sellerList(@Param("shopId") String shopId, @Param("status") String status,
                           @Param("offset") int offset, @Param("size") int size);

    // 卖家---统计订单数量
    long sellerCount(@Param("shopId") String shopId, @Param("status") String status);

    // 卖家---根据订单和店铺查找订单条目
    @Select("select * from order_items where order_id = #{orderId} and shop_id = #{shopId} order by id")
    List<OrderItem> listItemsByShop(@Param("orderId") Integer orderId, @Param("shopId") String shopId);

    // 卖家---判断订单是否含本店商品
    @Select("select count(*) from order_items where order_id = #{orderId} and shop_id = #{shopId}")
    int countItemsByShop(@Param("orderId") Integer orderId, @Param("shopId") String shopId);

    // 卖家---根据id查找订单
    @Select("select * from orders where id = #{id}")
    Order findById(Integer orderId);

    // 卖家发货：paid→shipped（幂等：已 shipped 直接成功，ship_time 用 COALESCE 保留首次发货时间）
    @Update("update orders set status = 'shipped', ship_time = coalesce(ship_time, now()), " +
            "logistics_json = #{json}, updated_at = now() " +
            "where id = #{id} and (status = 'paid' or status = 'shipped')")
    int sellerShip(@Param("id") Integer id, @Param("json") String json);
}
