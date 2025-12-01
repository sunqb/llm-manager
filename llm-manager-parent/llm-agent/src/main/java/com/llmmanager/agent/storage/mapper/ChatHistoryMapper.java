package com.llmmanager.agent.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.llmmanager.agent.storage.entity.ChatHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天历史 Mapper
 * 注意：MyBatis-Plus 会自动处理逻辑删除（is_delete字段）
 */
@Mapper
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {

    /**
     * 根据会话ID和限制条数查询最近的消息
     * 注意：MyBatis-Plus 会自动添加 is_delete = 0 条件
     */
    @Select("SELECT * FROM a_chat_history WHERE conversation_id = #{conversationId} " +
            "ORDER BY create_time DESC LIMIT #{limit}")
    List<ChatHistory> selectRecentMessages(@Param("conversationId") String conversationId,
                                           @Param("limit") int limit);

    /**
     * 软删除过期的消息
     */
    @Update("UPDATE a_chat_history SET is_delete = 1, update_time = NOW() " +
            "WHERE create_time < #{expireTime} AND is_delete = 0")
    int deleteExpiredMessages(@Param("expireTime") LocalDateTime expireTime);
}
