package org.web03.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 历史消息分页结果
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistoryResult {
    private Long total;           // 总条数
    private Integer page;         // 当前页码（从 1 开始）
    private Integer size;         // 每页条数
    private List<Messages> list;  // 当前页消息，按时间倒序（前端会 reverse 后渲染）

}
