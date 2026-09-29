package org.web03.pojo.Footprint;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 足迹列表元素
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FootprintProductVO {
    private Integer id;                       // 商品ID
    private String title;                     // 标题
    private BigDecimal price;                 // 售价
    private BigDecimal original;              // 原价/划线价
    private Map<String, Object> art;          // 展示图：商品第一个图片
    private Integer sales;                    // 销量
    private Integer stock;                    // 库存
    private String tag;                       // 标签
    private Map<String, Object> shop;         // {id, name, score}
    private Integer onSale;                   // 是否在售：1 在售 / 0 已下架
    private Integer deleted;                  // 软删除：恒为 0（列表已过滤）
    private String browseTime;                // 最近浏览时间（yyyy-MM-dd HH:mm:ss）
    private Integer browseCount;              // 累计浏览次数
}
