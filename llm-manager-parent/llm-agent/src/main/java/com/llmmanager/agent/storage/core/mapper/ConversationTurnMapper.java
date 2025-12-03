package com.llmmanager.agent.storage.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.llmmanager.agent.storage.core.entity.ConversationTurn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 对话轮次 Mapper
 */
@Mapper
public interface ConversationTurnMapper extends BaseMapper<ConversationTurn> {

    /**
     * 根据 turnCode 查询
     */
    @Select("SELECT * FROM a_conversation_turns WHERE turn_code = #{turnCode} AND is_delete = 0")
    ConversationTurn selectByTurnCode(@Param("turnCode") String turnCode);

    /**
     * 根据会话标识查询所有轮次（按轮次序号升序）
     */
    @Select("SELECT * FROM a_conversation_turns WHERE conversation_code = #{conversationCode} AND is_delete = 0 ORDER BY turn_index ASC")
    List<ConversationTurn> selectByConversationCode(@Param("conversationCode") String conversationCode);

    /**
     * 查询最近 N 个轮次（按轮次序号降序，返回后需反转）
     */
    @Select("SELECT * FROM a_conversation_turns WHERE conversation_code = #{conversationCode} AND is_delete = 0 ORDER BY turn_index DESC LIMIT #{limit}")
    List<ConversationTurn> selectRecentTurns(@Param("conversationCode") String conversationCode, @Param("limit") int limit);

    /**
     * 获取指定会话的最大轮次序号
     */
    @Select("SELECT MAX(turn_index) FROM a_conversation_turns WHERE conversation_code = #{conversationCode} AND is_delete = 0")
    Integer getMaxTurnIndex(@Param("conversationCode") String conversationCode);

    /**
     * 根据会话标识统计轮次数量
     */
    @Select("SELECT COUNT(*) FROM a_conversation_turns WHERE conversation_code = #{conversationCode} AND is_delete = 0")
    int countByConversationCode(@Param("conversationCode") String conversationCode);

    /**
     * 统计成功的轮次数量
     */
    @Select("SELECT COUNT(*) FROM a_conversation_turns WHERE conversation_code = #{conversationCode} AND status = 'SUCCESS' AND is_delete = 0")
    int countSuccessTurns(@Param("conversationCode") String conversationCode);

    /**
     * 统计会话的总 token 消耗
     */
    @Select("SELECT COALESCE(SUM(total_tokens), 0) FROM a_conversation_turns WHERE conversation_code = #{conversationCode} AND is_delete = 0")
    int sumTotalTokens(@Param("conversationCode") String conversationCode);

    /**
     * 更新 Turn 状态
     */
    @Update("UPDATE a_conversation_turns SET status = #{status}, update_time = NOW() WHERE turn_code = #{turnCode}")
    int updateStatus(@Param("turnCode") String turnCode, @Param("status") String status);

    /**
     * 更新 Turn 的 Token 统计
     */
    @Update("UPDATE a_conversation_turns SET prompt_tokens = #{promptTokens}, completion_tokens = #{completionTokens}, " +
            "total_tokens = #{totalTokens}, update_time = NOW() WHERE turn_code = #{turnCode}")
    int updateTokens(@Param("turnCode") String turnCode,
                     @Param("promptTokens") int promptTokens,
                     @Param("completionTokens") int completionTokens,
                     @Param("totalTokens") int totalTokens);

    /**
     * 更新 Turn 完成信息
     */
    @Update("UPDATE a_conversation_turns SET status = #{status}, prompt_tokens = #{promptTokens}, " +
            "completion_tokens = #{completionTokens}, total_tokens = #{totalTokens}, latency_ms = #{latencyMs}, " +
            "end_time = NOW(), update_time = NOW() WHERE turn_code = #{turnCode}")
    int updateCompletion(@Param("turnCode") String turnCode,
                         @Param("status") String status,
                         @Param("promptTokens") int promptTokens,
                         @Param("completionTokens") int completionTokens,
                         @Param("totalTokens") int totalTokens,
                         @Param("latencyMs") int latencyMs);

    /**
     * 更新用户消息标识
     */
    @Update("UPDATE a_conversation_turns SET user_message_code = #{userMessageCode}, update_time = NOW() WHERE turn_code = #{turnCode}")
    int updateUserMessageCode(@Param("turnCode") String turnCode, @Param("userMessageCode") String userMessageCode);

    /**
     * 更新助手消息标识
     */
    @Update("UPDATE a_conversation_turns SET assistant_message_code = #{assistantMessageCode}, update_time = NOW() WHERE turn_code = #{turnCode}")
    int updateAssistantMessageCode(@Param("turnCode") String turnCode, @Param("assistantMessageCode") String assistantMessageCode);

    /**
     * 软删除指定会话的所有轮次
     */
    @Update("UPDATE a_conversation_turns SET is_delete = 1, update_time = NOW() WHERE conversation_code = #{conversationCode}")
    int softDeleteByConversationCode(@Param("conversationCode") String conversationCode);

    /**
     * 查询会话最近的未完成轮次（PENDING 或 PROCESSING 状态）
     */
    @Select("SELECT * FROM a_conversation_turns WHERE conversation_code = #{conversationCode} " +
            "AND status IN ('PENDING', 'PROCESSING') AND is_delete = 0 ORDER BY turn_index DESC LIMIT 1")
    ConversationTurn selectLatestPendingTurn(@Param("conversationCode") String conversationCode);
}
