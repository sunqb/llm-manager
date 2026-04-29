package com.llmmanager.agent.storage.core.service;

import com.llmmanager.agent.storage.core.entity.PendingReview;

import java.util.List;
import java.util.Optional;

/**
 * 人工审核记录 Service 接口
 *
 * 职责：封装 PendingReview 的 CRUD 操作和业务逻辑
 *
 * 命名规范：
 * - reviewCode：审核业务唯一标识（32位UUID，无连字符）
 *
 * @author LLM Manager
 */
public interface PendingReviewService {

    /**
     * 创建新审核记录
     *
     * @param review 审核记录实体
     * @return 创建后的审核记录
     */
    PendingReview create(PendingReview review);

    /**
     * 根据审核标识查询
     *
     * @param reviewCode 审核标识
     * @return 审核记录（可能为空）
     */
    Optional<PendingReview> findByReviewCode(String reviewCode);

    /**
     * 根据审核标识获取（必须存在）
     *
     * @param reviewCode 审核标识
     * @return 审核记录
     * @throws IllegalArgumentException 如果审核记录不存在
     */
    PendingReview getByReviewCode(String reviewCode);

    /**
     * 检查审核是否存在
     *
     * @param reviewCode 审核标识
     * @return 是否存在
     */
    boolean exists(String reviewCode);

    /**
     * 查询待审核列表
     *
     * @param limit 查询数量限制（0 表示全部）
     * @return 待审核记录列表
     */
    List<PendingReview> findPendingReviews(int limit);

    /**
     * 查询所有待审核列表
     *
     * @return 待审核记录列表
     */
    List<PendingReview> findAllPendingReviews();

    /**
     * 根据会话标识查询审核记录
     *
     * @param conversationCode 会话标识
     * @return 审核记录列表
     */
    List<PendingReview> findByConversationCode(String conversationCode);

    /**
     * 根据 Graph 任务 ID 查询审核记录
     *
     * @param graphTaskId Graph 任务 ID
     * @return 审核记录列表
     */
    List<PendingReview> findByGraphTaskId(Long graphTaskId);

    /**
     * 根据审核类型查询审核记录
     *
     * @param reviewType 审核类型
     * @param limit 查询数量限制
     * @return 审核记录列表
     */
    List<PendingReview> findByReviewType(PendingReview.ReviewType reviewType, int limit);

    /**
     * 根据审核人 ID 查询审核记录
     *
     * @param reviewerId 审核人 ID
     * @param limit 查询数量限制
     * @return 审核记录列表
     */
    List<PendingReview> findByReviewerId(Long reviewerId, int limit);

    /**
     * 查询指定时间范围内的审核记录
     *
     * @param startTime 开始时间（格式：yyyy-MM-dd HH:mm:ss）
     * @param endTime 结束时间（格式：yyyy-MM-dd HH:mm:ss）
     * @return 审核记录列表
     */
    List<PendingReview> findByTimeRange(String startTime, String endTime);

    /**
     * 批准审核
     *
     * @param reviewCode 审核标识
     * @param reviewerId 审核人 ID
     * @param reviewComment 审核意见
     */
    void approve(String reviewCode, Long reviewerId, String reviewComment);

    /**
     * 拒绝审核
     *
     * @param reviewCode 审核标识
     * @param reviewerId 审核人 ID
     * @param reviewComment 拒绝原因
     */
    void reject(String reviewCode, Long reviewerId, String reviewComment);

    /**
     * 更新审核记录
     *
     * @param review 审核记录
     */
    void update(PendingReview review);

    /**
     * 根据 ID 更新审核记录
     *
     * @param id 审核记录 ID
     * @param review 审核记录
     */
    void updateById(Long id, PendingReview review);

    /**
     * 增加重试次数
     *
     * @param reviewCode 审核标识
     */
    void incrementRetryCount(String reviewCode);

    /**
     * 软删除审核记录
     *
     * @param reviewCode 审核标识
     */
    void delete(String reviewCode);

    /**
     * 根据会话标识软删除所有审核记录
     *
     * @param conversationCode 会话标识
     */
    void deleteByConversationCode(String conversationCode);

    /**
     * 根据 Graph 任务 ID 软删除所有审核记录
     *
     * @param graphTaskId Graph 任务 ID
     */
    void deleteByGraphTaskId(Long graphTaskId);

    /**
     * 统计待审核数量
     *
     * @return 待审核数量
     */
    int countPending();

    /**
     * 统计指定类型的待审核数量
     *
     * @param reviewType 审核类型
     * @return 待审核数量
     */
    int countPendingByType(PendingReview.ReviewType reviewType);
}
