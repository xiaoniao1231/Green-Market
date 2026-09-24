package org.web03.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.web03.pojo.Product.ProductsCheck;
import org.web03.pojo.Product.Product;

import java.util.List;

/**
 * 商品数据访问
 */
@Mapper
public interface ProductMapper {

    // 卖家商品列表
    List<Product> sellerList(ProductsCheck productsCheck);

    // 卖家商品总数（与 sellerList 同一套 shopId/status/keyword 条件，不含「仅在售」限制）
    long sellerCount(ProductsCheck productsCheck);

    // 公开商品列表（买家端：仅在售、未删除，无需登录/开店）
    List<Product> publicList(ProductsCheck productsCheck);

    // 公开商品总数
    long publicCount(ProductsCheck productsCheck);

    // 插入商品
    int insert(Product p);

    // 根据id获取商品
    Product getById(Integer id);

    //买家端按 id 获取商品
    Product getPublicById(@Param("id") Integer id);

    //相关推荐：同分类的在售商品优先，不足时用其他在售商品补足（排除自身）。
    List<Product> related(@Param("id") Integer id, @Param("category") String category, @Param("limit") int limit);

    // 删除商品
    int softDelete(@Param("id") Integer id, @Param("shopId") String shopId);

    // 根据id和shopId获取商品
    Product getByIdAndShop(@Param("id") Integer id, @Param("shopId") String shopId);

    // 更新商品
    int update(Product product);

    // 更新商品上下架状态
    int updateStatus(@Param("id") Integer id, @Param("shopId") String shopId, @Param("onSale") int onSale);

    //限时秒杀：在售按销量倒序取前 N 条
    List<Product> flashList(int i);

    //猜你喜欢：在售按创建时间倒序分页
    List<Product> recommendList(ProductsCheck productsCheck);

    //猜你喜欢：在售按创建时间倒序总数
    long recommendCount();

    // 标题模糊搜索（在售）
    List<Product> search(ProductsCheck productsCheck);

    // 标题模糊搜索（在售）总数
    long searchCount(@Param("q") String q);

    // 扣减库存
    @Update("update products set stock = stock - #{qty} where id = #{id} and stock >= #{qty}")
    int deductStock(@Param("id") Integer id, @Param("qty") int qty);

    //库存回补（取消订单时归还）
    @Update("update products set stock = stock + #{qty} where id = #{id}")
    void restoreStock(@Param("id") Integer id, @Param("qty") Integer qty);
}
