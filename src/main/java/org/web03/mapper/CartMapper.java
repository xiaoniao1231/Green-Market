package org.web03.mapper;

import org.apache.ibatis.annotations.*;
import org.web03.pojo.CartItem.CartItem;

import java.util.List;

@Mapper
public interface CartMapper {

    //根据用户ID查询购物车列表
    List<CartItem> listByUserId(String userId);

    //添加购物车
    void upsert(CartItem cartItem);

    //修改购物商品数量
    @Update("update cart_items set quantity = #{quantity}, updated_at = now() " +
            "where user_id = #{userId} and product_id = #{productId} and sku = #{sku}")
    int updateQty(@Param("userId") String userId, @Param("productId") int productId,
                  @Param("sku") String sku, @Param("quantity") int quantity);

    //根据用户ID、商品ID和SKU查询购物车项
    @Select("select * from cart_items where user_id = #{userId} and product_id = #{productId} and sku = #{sku}")
    CartItem findByKey(@Param("userId") String userId, @Param("productId") int productId, @Param("sku") String sku);

    //删除购物车项
    @Delete("delete from cart_items where user_id = #{userId} and product_id = #{productId} and sku = #{sku}")
    int deleteOne(@Param("userId") String userId, @Param("productId") int productId, @Param("sku") String sku);

    //清空购物车
    @Delete("delete from cart_items where user_id = #{userId}")
    int clear(String userId);
}
