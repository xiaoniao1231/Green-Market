package org.web03.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.web03.pojo.Order.SellerReminder;

/**
 * 买家催发货记录数据访问。
 */
@Mapper
public interface SellerReminderMapper {

    //按订单查催发货记录（未提醒过返回 null）
    @Select("select * from seller_reminders where order_id = #{orderId} limit 1")
    SellerReminder findByOrder(Integer orderId);

    //累加一次催发货：首次提醒插入 count=1；已存在则 count+1 并刷新时间。
    @Insert("insert into seller_reminders (order_id, shop_id, buyer_id, owner_user_id, remind_count, last_remind_time, handled, created_at, updated_at) " +
            "values (#{orderId}, #{shopId}, #{buyerId}, #{ownerUserId}, 1, now(), 0, now(), now()) " +
            "on duplicate key update remind_count = remind_count + 1, last_remind_time = now(), handled = 0, updated_at = now()")
    void upsert(SellerReminder reminder);

    //店家发货后把该订单的催发货标记为已处理（店家端角标随之消失）
    @Update("update seller_reminders set handled = 1, updated_at = now() where order_id = #{orderId}")
    void markHandled(Integer orderId);
}
