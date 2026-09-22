package org.web03.pojo.Favorite;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 获取收藏夹列表响应参数
 */


@Data
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteFanHui {
    private Integer total;               // 总条数
    private Integer page = 1;            // 当前页码
    private Integer size = 100;          // 每页大小
    private List<FavoriteProductVO> list; // 列表数据（前端契约商品对象）
}
