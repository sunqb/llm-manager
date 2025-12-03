package com.llmmanager.agent.storage.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.llmmanager.agent.storage.core.entity.ChatHistory;
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
     * 根据会话标识和限制条数查询最近的消息
     * 注意：需要手动添加 is_delete = 0 条件
     */
    @Select("SELECT * FROM a_chat_history WHERE conversation_code = #{conversationCode} AND is_delete = 0 " +
            "ORDER BY create_time DESC LIMIT #{limit}")
    List<ChatHistory> selectRecentMessages(@Param("conversationCode") String conversationCode,
                                           @Param("limit") int limit);

    /**
     * 根据消息标识查询
     */
    @Select("SELECT * FROM a_chat_history WHERE message_code = #{messageCode} AND is_delete = 0")
    ChatHistory selectByMessageCode(@Param("messageCode") String messageCode);

    /**
     * 根据轮次标识查询消息列表
     */
    @Select("SELECT * FROM a_chat_history WHERE turn_code = #{turnCode} AND is_delete = 0 ORDER BY message_index ASC")
    List<ChatHistory> selectByTurnCode(@Param("turnCode") String turnCode);

    /**
     * 软删除过期的消息
     */
    @Update("UPDATE a_chat_history SET is_delete = 1, update_time = NOW() " +
            "WHERE create_time < #{expireTime} AND is_delete = 0")
    int deleteExpiredMessages(@Param("expireTime") LocalDateTime expireTime);

    /**
     * 获取指定会话的最大消息序号
     * @param conversationCode 会话标识
     * @return 最大消息序号，如果没有记录则返回null
     */
    @Select("SELECT MAX(message_index) FROM a_chat_history " +
            "WHERE conversation_code = #{conversationCode} AND is_delete = 0")
    Integer getMaxMessageIndex(@Param("conversationCode") String conversationCode);

    /**
     * 软删除指定会话的所有消息
     */
    @Update("UPDATE a_chat_history SET is_delete = 1, update_time = NOW() " +
            "WHERE conversation_code = #{conversationCode} AND is_delete = 0")
    int softDeleteByConversationCode(@Param("conversationCode") String conversationCode);

    /**
     * 查询指定会话的最新用户消息
     */
    @Select("SELECT * FROM a_chat_history " +
            "WHERE conversation_code = #{conversationCode} AND message_type = 'USER' AND is_delete = 0 " +
            "ORDER BY create_time DESC LIMIT 1")
    ChatHistory selectLatestUserMessage(@Param("conversationCode") String conversationCode);
}
