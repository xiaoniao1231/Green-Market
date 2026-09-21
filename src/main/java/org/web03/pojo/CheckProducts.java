package org.web03.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 商品数据访问
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CheckProducts {
    private String shopId;       // 店铺ID
    private Integer page = 1;   // 当前页码
    private Integer size = 100; // 每页大小
    private String status;      // 状态
    private String keyword;     // 关键字
    private List list; // 商品列表
    private Long total; // 总数
    private Integer offset;// 偏移量
    private String category; // 类别
    private String sub; // 子类
    private String q;
    /** 排序：default/综合（最新上架）、sales（销量）、priceAsc（价格升）、priceDesc（价格降） */
    private String sort;

    public CheckProducts(long total, Integer page, Integer size, List<ProductVO> list) {
        this.total = total;
        this.page = page;
        this.size = size;
        this.list = list;
    }

}
