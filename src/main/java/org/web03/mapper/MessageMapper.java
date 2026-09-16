package org.web03.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.web03.pojo.Messages;

import java.util.List;
import java.util.Map;

/**
 * 消息表 Mapper
 */
@Mapper
public interface MessageMapper {

    /** 消息入库（msg_id 唯一，重复提交由上层幂等处理） */
    void insert(Messages message);

    /** 按消息 ID 查询（幂等判断用） */
    Messages selectByMsgId(@Param("msgId") String msgId);

    /** 与某账号的双向私聊总条数 */
    long countByPeer(@Param("myId") String myId, @Param("peerId") String peerId);

    /** 分页查询与某账号的双向私聊，时间倒序 */
    List<Messages> selectPageByPeer(@Param("myId") String myId, @Param("peerId") String peerId,
                                    @Param("offset") int offset, @Param("size") int size);

    /** 查询当前用户参与的全部会话：按对端分组，每组取最新一条消息（会话列表 / 最后消息预览用） */
    List<Messages> selectConversations(@Param("myId") String myId);

    /** 各会话未读数：对方发给我、且发送时间晚于该会话最近已读时间的消息数（key = 对端账号） */
    List<Map<String, Object>> countUnreadByPeer(@Param("myId") String myId);

    /** 记录会话已读时间（幂等 upsert） */
    void upsertReadState(@Param("myId") String myId, @Param("peerId") String peerId);

}
