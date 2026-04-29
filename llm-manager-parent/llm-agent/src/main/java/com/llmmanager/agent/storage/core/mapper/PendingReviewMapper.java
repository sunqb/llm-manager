package com.llmmanager.agent.storage.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.llmmanager.agent.storage.core.entity.PendingReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 人工审核记录 Mapper
 *
 * @author LLM Manager
 */
@Mapper
public interface PendingReviewMapper extends BaseMapper<PendingReview> {

    /**
     * 根据审核标识查询
     *
     * @param reviewCode 审核标识
     * @return 审核记录（可能为空）
     */
    @Select("SELECT * FROM a_pending_reviews WHERE review_code = #{reviewCode} AND is_delete = 0")
    PendingReview selectByReviewCode(@Param("reviewCode") String reviewCode);

    /**
     * 检查审核是否存在
     *
     * @param reviewCode 审核标识
     * @return 是否存在
     */
    @Select("SELECT COUNT(1) FROM a_pending_reviews WHERE review_code = #{reviewCode} AND is_delete = 0")
    int existsByReviewCode(@Param("reviewCode") String reviewCode);

    /**
     * 查询待审核列表（按创建时间倒序）
     *
     * @param limit 查询数量限制
     * @return 待审核记录列表
     */
    @Select("SELECT * FROM a_pending_reviews WHERE status = 'PENDING' AND is_delete = 0 " +
            "ORDER BY create_time DESC LIMIT #{limit}")
    List<PendingReview> selectPendingReviews(@Param("limit") int limit);

    /**
     * 查询所有待审核列表
     *
     * @return 待审核记录列表
     */
    @Select("SELECT * FROM a_pending_reviews WHERE status = 'PENDING' AND is_delete = 0 " +
            "ORDER BY create_time DESC")
    List<PendingReview> selectAllPendingReviews();

    /**
     * 根据会话标识查询审核记录
     *
     * @param conversationCode 会话标识
     * @return 审核记录列表
     */
    @Select("SELECT * FROM a_pending_reviews WHERE conversation_code = #{conversationCode} " +
            "AND is_delete = 0 ORDER BY create_time DESC")
    List<PendingReview> selectByConversationCode(@Param("conversationCode") String conversationCode);

    /**
     * 根据 Graph 任务 ID 查询审核记录
     *
     * @param graphTaskId Graph 任务 ID
     * @return 审核记录列表
     */
    @Select("SELECT * FROM a_pending_reviews WHERE graph_task_id = #{graphTaskId} " +
            "AND is_delete = 0 ORDER BY create_time DESC")
    List<PendingReview> selectByGraphTaskId(@Param("graphTaskId") Long graphTaskId);

    /**
     * 根据审核类型查询审核记录
     *
     * @param reviewType 审核类型
     * @param limit 查询数量限制
     * @return 审核记录列表
     */
    @Select("SELECT * FROM a_pending_reviews WHERE review_type = #{reviewType} " +
            "AND status = 'PENDING' AND is_delete = 0 " +
            "ORDER BY create_time DESC LIMIT #{limit}")
    List<PendingReview> selectByReviewType(@Param("reviewType") String reviewType, @Param("limit") int limit);

    /**
     * 根据审核人 ID 查询审核记录
     *
     * @param reviewerId 审核人 ID
     * @param limit 查询数量限制
     * @return 审核记录列表
     */
    @Select("SELECT * FROM a_pending_reviews WHERE reviewer_id = #{reviewerId} " +
            "AND is_delete = 0 ORDER BY reviewed_at DESC LIMIT #{limit}")
    List<PendingReview> selectByReviewerId(@Param("reviewerId") Long reviewerId, @Param("limit") int limit);

    /**
     * 查询指定时间范围内的审核记录
     *
     * @param startTime 开始时间（格式：yyyy-MM-dd HH:mm:ss）
     * @param endTime 结束时间（格式：yyyy-MM-dd HH:mm:ss）
     * @return 审核记录列表
     */
    @Select("SELECT * FROM a_pending_reviews WHERE create_time BETWEEN #{startTime} AND #{endTime} " +
            "AND is_delete = 0 ORDER BY create_time DESC")
    List<PendingReview> selectByTimeRange(@Param("startTime") String startTime, @Param("endTime") String endTime);

    /**
     * 更新审核结果
     *
     * @param reviewCode 审核标识
     * @param status 审核状态
     * @param reviewResult 审核结果
     * @param reviewerId 审核人 ID
     * @param reviewComment 审核意见
     * @return 更新行数
     */
    @Update("UPDATE a_pending_reviews SET " +
            "status = #{status}, " +
            "review_result = #{reviewResult}, " +
            "reviewer_id = #{reviewerId}, " +
            "review_comment = #{reviewComment}, " +
            "reviewed_at = NOW(), " +
            "update_time = NOW() " +
            "WHERE review_code = #{reviewCode} AND is_delete = 0")
    int updateReviewResult(@Param("reviewCode") String reviewCode,
                           @Param("status") String status,
                           @Param("reviewResult") Boolean reviewResult,
                           @Param("reviewerId") Long reviewerId,
                           @Param("reviewComment") String reviewComment);

    /**
     * 增加重试次数
     *
     * @param reviewCode 审核标识
     * @return 更新行数
     */
    @Update("UPDATE a_pending_reviews SET " +
            "current_retry_count = current_retry_count + 1, " +
            "update_time = NOW() " +
            "WHERE review_code = #{reviewCode} AND is_delete = 0")
    int incrementRetryCount(@Param("reviewCode") String reviewCode);

    /**
     * 软删除审核记录
     *
     * @param reviewCode 审核标识
     * @return 更新行数
     */
    @Update("UPDATE a_pending_reviews SET is_delete = 1, update_time = NOW() " +
            "WHERE review_code = #{reviewCode} AND is_delete = 0")
    int softDeleteByReviewCode(@Param("reviewCode") String reviewCode);

    /**
     * 根据会话标识软删除所有审核记录
     *
     * @param conversationCode 会话标识
     * @return 更新行数
     */
    @Update("UPDATE a_pending_reviews SET is_delete = 1, update_time = NOW() " +
            "WHERE conversation_code = #{conversationCode} AND is_delete = 0")
    int softDeleteByConversationCode(@Param("conversationCode") String conversationCode);

    /**
     * 根据 Graph 任务 ID 软删除所有审核记录
     *
     * @param graphTaskId Graph 任务 ID
     * @return 更新行数
     */
    @Update("UPDATE a_pending_reviews SET is_delete = 1, update_time = NOW() " +
            "WHERE graph_task_id = #{graphTaskId} AND is_delete = 0")
    int softDeleteByGraphTaskId(@Param("graphTaskId") Long graphTaskId);

    /**
     * 统计待审核数量
     *
     * @return 待审核数量
     */
    @Select("SELECT COUNT(1) FROM a_pending_reviews WHERE status = 'PENDING' AND is_delete = 0")
    int countPending();

    /**
     * 统计指定类型的待审核数量
     *
     * @param reviewType 审核类型
     * @return 待审核数量
     */
    @Select("SELECT COUNT(1) FROM a_pending_reviews WHERE review_type = #{reviewType} " +
            "AND status = 'PENDING' AND is_delete = 0")
    int countPendingByType(@Param("reviewType") String reviewType);
}
