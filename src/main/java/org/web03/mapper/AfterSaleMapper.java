package org.web03.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.web03.pojo.AfterSale.AfterSale;
import org.web03.pojo.AfterSale.AfterSaleItem;
import org.web03.pojo.AfterSale.AfterSaleLog;

import java.util.Collection;
import java.util.List;

@Mapper
public interface AfterSaleMapper {

    // 根据id和用户id查询
    @Select("select * from after_sales where id = #{id} and user_id = #{userId}")
    AfterSale findByIdAndUser(@Param("id") Integer id, @Param("userId") String userId);

    // 根据id和店铺id查询
    @Select("select * from after_sales where id = #{id} and shop_id = #{shopId}")
    AfterSale findByIdAndShop(@Param("id") Integer id, @Param("shopId") String shopId);

    // 某售后单的全部记录
    @Select("select * from after_sale_logs where after_sale_id = #{afterSaleId} order by id")
    List<AfterSaleLog> listLogs(Integer afterSaleId);

    // 根据用户id查询所有售后单
    @Select("select * from after_sales where user_id = #{userId} order by id desc")
    List<AfterSale> listAllByUser(String userId);

    // 插入售后记录
    @Insert("insert into after_sale_logs (after_sale_id, role, action, actor_id, content, created_at) " +
            "values (#{afterSaleId}, #{role}, #{action}, #{actorId}, #{content}, now())")
    int insertLog(AfterSaleLog log);

    // 查重（订单级）：一个订单只能有一条售后单
    @Select("select * from after_sales where order_id = #{orderId}")
    AfterSale findByOrderId(Integer orderId);

    // 某售后单的商品明细（一条售后单可含订单里的多件商品）
    @Select("select * from after_sale_items where after_sale_id = #{afterSaleId} order by id")
    List<AfterSaleItem> listItems(Integer afterSaleId);

    // 批量写入商品明细
    int insertItems(List<AfterSaleItem> items);

    // 清空某售后单的商品明细（重新申请时先删后插）
    @Delete("delete from after_sale_items where after_sale_id = #{afterSaleId}")
    int deleteItems(Integer afterSaleId);

    // 买家寄回物流（仅「待买家寄回」→「待商家收货」时写入）
    @Update("update after_sales set buyer_company = #{company}, buyer_tracking_no = #{trackingNo}, " +
            "buyer_ship_time = now(), updated_at = now() where id = #{id}")
    int updateShipInfo(@Param("id") Integer id, @Param("company") String company, @Param("trackingNo") String trackingNo);

    // 商家同意：写入寄回地址与备注
    int updateApproveInfo(@Param("id") Integer id, @Param("address") String address, @Param("remark") String remark);

    // 商家拒绝：写入拒绝原因（after_sales.refuse_reason，前端买卖双方页面都会展示）
    @Update("update after_sales set refuse_reason = #{reason}, updated_at = now() where id = #{id}")
    int updateRefuseReason(@Param("id") Integer id, @Param("reason") String reason);

    // 商家确认收货：写入换货重发物流与备注
    int updateReceiveInfo(@Param("id") Integer id, @Param("exchange") boolean exchange,
                          @Param("company") String company, @Param("no") String no, @Param("remark") String remark);

    //撤销 / 被拒绝后重新申请
    int resetForReapply(AfterSale afterSale);

    // 按 ID 查询
    @Select("select * from after_sales where id = #{id}")
    AfterSale findById(Integer id);

    // 插入售后单
    int insert(AfterSale afterSale);

    //修改售后单状态
    int changeStatus(@Param("id") Integer id,
                     @Param("userId") String userId,
                     @Param("from") String from,
                     @Param("to") String to,
                     @Param("finish") boolean finish);

    // 根据店铺ID和状态查询售后单
    List<AfterSale> listByShop(@Param("shopId") String shopId, @Param("status") String status,
                               @Param("afterNo") String afterNo,
                               @Param("offset") int offset, @Param("size") int size);

    // 根据店铺ID和状态查询售后单数量
    long countByShop(@Param("shopId") String shopId, @Param("status") String status,
                     @Param("afterNo") String afterNo);
}
