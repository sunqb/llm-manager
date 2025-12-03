package com.llmmanager.agent.storage.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.llmmanager.agent.storage.core.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 会话 Mapper
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 根据会话标识查询
     */
    @Select("SELECT * FROM a_conversations WHERE conversation_code = #{conversationCode} AND is_delete = 0")
    Conversation selectByConversationCode(@Param("conversationCode") String conversationCode);

    /**
     * 检查会话是否存在
     */
    @Select("SELECT COUNT(1) FROM a_conversations WHERE conversation_code = #{conversationCode} AND is_delete = 0")
    int existsByConversationCode(@Param("conversationCode") String conversationCode);

    /**
     * 根据Agent标识查询会话列表
     */
    @Select("SELECT * FROM a_conversations WHERE agent_slug = #{agentSlug} AND is_delete = 0 " +
            "ORDER BY is_pinned DESC, last_message_time DESC")
    List<Conversation> selectByAgentSlug(@Param("agentSlug") String agentSlug);

    /**
     * 查询所有会话标识
     */
    @Select("SELECT conversation_code FROM a_conversations WHERE is_delete = 0")
    List<String> selectAllConversationCodes();

    /**
     * 更新消息数量
     */
    @Update("UPDATE a_conversations SET message_count = message_count + #{count}, " +
            "last_message_time = NOW(), update_time = NOW() " +
            "WHERE conversation_code = #{conversationCode} AND is_delete = 0")
    int incrementMessageCount(@Param("conversationCode") String conversationCode, @Param("count") int count);

    /**
     * 更新tokens消耗
     */
    @Update("UPDATE a_conversations SET total_tokens = total_tokens + #{tokens}, " +
            "update_time = NOW() " +
            "WHERE conversation_code = #{conversationCode} AND is_delete = 0")
    int addTokens(@Param("conversationCode") String conversationCode, @Param("tokens") int tokens);

    /**
     * 软删除会话
     */
    @Update("UPDATE a_conversations SET is_delete = 1, update_time = NOW() " +
            "WHERE conversation_code = #{conversationCode} AND is_delete = 0")
    int softDeleteByConversationCode(@Param("conversationCode") String conversationCode);

    /**
     * 归档会话
     */
    @Update("UPDATE a_conversations SET is_archived = 1, update_time = NOW() " +
            "WHERE conversation_code = #{conversationCode} AND is_delete = 0")
    int archiveByConversationCode(@Param("conversationCode") String conversationCode);

    /**
     * 置顶/取消置顶会话
     */
    @Update("UPDATE a_conversations SET is_pinned = #{isPinned}, update_time = NOW() " +
            "WHERE conversation_code = #{conversationCode} AND is_delete = 0")
    int updatePinned(@Param("conversationCode") String conversationCode, @Param("isPinned") int isPinned);

    /**
     * 更新会话标题
     */
    @Update("UPDATE a_conversations SET title = #{title}, update_time = NOW() " +
            "WHERE conversation_code = #{conversationCode} AND is_delete = 0")
    int updateTitle(@Param("conversationCode") String conversationCode, @Param("title") String title);
}
