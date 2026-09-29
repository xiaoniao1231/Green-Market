package org.web03.pojo.Footprint;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 足迹列表响应参数
 */


@Data
@NoArgsConstructor
@AllArgsConstructor
public class FootprintListResult {
    private Integer total;                        // 总条数
    private Integer page = 1;                     // 当前页码
    private Integer size = 100;                   // 每页大小
    private List<FootprintProductVO> list;        // 列表数据
}
