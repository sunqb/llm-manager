package com.llmmanager.agent.storage.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.llmmanager.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 人工审核记录实体
 *
 * 支持四种审核类型：
 * - GRAPH_NODE：Graph 工作流中的 HUMAN_REVIEW_NODE 节点
 * - REACT_AGENT_TOOL：ReactAgent 调用 HumanReviewTool
 * - REACT_AGENT_SEQUENTIAL：SEQUENTIAL 工作流的 Agent 间审核
 * - REACT_AGENT_SUPERVISOR：SUPERVISOR 团队的审核
 *
 * 命名规范：
 * - id：主键（自增整数）
 * - reviewCode：业务唯一标识（32位UUID，无连字符）
 *
 * @author LLM Manager
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "a_pending_reviews", autoResultMap = true)
public class PendingReview extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 审核唯一标识（32位UUID，无连字符）
     */
    private String reviewCode;

    /**
     * 审核类型
     */
    private String reviewType;

    /**
     * 关联的 Graph 任务 ID（对于 GRAPH_NODE 类型）
     */
    private Long graphTaskId;

    /**
     * 关联的会话标识（对于 ReactAgent 类型）
     */
    private String conversationCode;

    /**
     * 关联的 Agent 配置 Code（对于 ReactAgent 类型）
     */
    private String agentConfigCode;

    /**
     * 当前节点/Agent 名称
     */
    private String currentNode;

    /**
     * 展示给审核人的提示内容
     */
    @TableField(value = "reviewer_prompt")
    private String reviewerPrompt;

    /**
     * 上下文字段列表（如 ["content", "analysis"]）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private String[] contextKeys;

    /**
     * 上下文数据（状态快照，JSON 格式）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> contextData;

    /**
     * 审核状态：PENDING（待审核）/ APPROVED（已批准）/ REJECTED（已拒绝）
     */
    private String status;

    /**
     * 审核结果：1=通过，0=拒绝，NULL=待审核
     */
    private Boolean reviewResult;

    /**
     * 审核意见/拒绝原因
     */
    private String reviewComment;

    /**
     * 审核人 ID（关联 user 表）
     */
    private Long reviewerId;

    /**
     * 审核时间
     */
    private LocalDateTime reviewedAt;

    /**
     * 批准后是否自动恢复执行：1=是，0=否
     */
    private Boolean resumeAfterApproval;

    /**
     * 最大重试次数（恢复执行失败时）
     */
    private Integer maxRetryCount;

    /**
     * 当前重试次数
     */
    private Integer currentRetryCount;

    /**
     * 审核类型枚举
     */
    public enum ReviewType {
        /**
         * Graph 工作流审核节点
         */
        GRAPH_NODE,

        /**
         * ReactAgent 工具调用审核
         */
        REACT_AGENT_TOOL,

        /**
         * SEQUENTIAL 工作流的 Agent 间审核
         */
        REACT_AGENT_SEQUENTIAL,

        /**
         * SUPERVISOR 团队审核
         */
        REACT_AGENT_SUPERVISOR
    }

    /**
     * 审核状态枚举
     */
    public enum ReviewStatus {
        /**
         * 待审核
         */
        PENDING,

        /**
         * 已批准
         */
        APPROVED,

        /**
         * 已拒绝
         */
        REJECTED
    }

    /**
     * 创建新审核记录的工厂方法
     *
     * @param reviewType 审核类型
     * @param reviewerPrompt 审核提示
     * @param contextData 上下文数据
     * @return 审核记录实体
     */
    public static PendingReview create(ReviewType reviewType, String reviewerPrompt, Map<String, Object> contextData) {
        PendingReview review = new PendingReview();
        review.setReviewCode(generateReviewCode());
        review.setReviewType(reviewType.name());
        review.setReviewerPrompt(reviewerPrompt);
        review.setContextData(contextData);
        review.setStatus(ReviewStatus.PENDING.name());
        review.setResumeAfterApproval(true);
        review.setMaxRetryCount(3);
        review.setCurrentRetryCount(0);
        return review;
    }

    /**
     * 创建 Graph 工作流审核记录
     *
     * @param graphTaskId Graph 任务 ID
     * @param currentNode 当前节点名称
     * @param reviewerPrompt 审核提示
     * @param contextData 上下文数据
     * @return 审核记录实体
     */
    public static PendingReview createGraphNodeReview(Long graphTaskId, String currentNode,
                                                       String reviewerPrompt, Map<String, Object> contextData) {
        PendingReview review = create(ReviewType.GRAPH_NODE, reviewerPrompt, contextData);
        review.setGraphTaskId(graphTaskId);
        review.setCurrentNode(currentNode);
        return review;
    }

    /**
     * 创建 ReactAgent 工具审核记录
     *
     * @param conversationCode 会话标识
     * @param agentConfigCode Agent 配置 Code
     * @param reviewerPrompt 审核提示
     * @param contextData 上下文数据
     * @return 审核记录实体
     */
    public static PendingReview createReactAgentToolReview(String conversationCode, String agentConfigCode,
                                                            String reviewerPrompt, Map<String, Object> contextData) {
        PendingReview review = create(ReviewType.REACT_AGENT_TOOL, reviewerPrompt, contextData);
        review.setConversationCode(conversationCode);
        review.setAgentConfigCode(agentConfigCode);
        return review;
    }

    /**
     * 创建 SEQUENTIAL 工作流审核记录
     *
     * @param conversationCode 会话标识
     * @param agentConfigCode Agent 配置 Code
     * @param currentNode 当前 Agent 名称
     * @param reviewerPrompt 审核提示
     * @param contextData 上下文数据
     * @return 审核记录实体
     */
    public static PendingReview createSequentialReview(String conversationCode, String agentConfigCode,
                                                        String currentNode, String reviewerPrompt,
                                                        Map<String, Object> contextData) {
        PendingReview review = create(ReviewType.REACT_AGENT_SEQUENTIAL, reviewerPrompt, contextData);
        review.setConversationCode(conversationCode);
        review.setAgentConfigCode(agentConfigCode);
        review.setCurrentNode(currentNode);
        return review;
    }

    /**
     * 创建 SUPERVISOR 团队审核记录
     *
     * @param conversationCode 会话标识
     * @param agentConfigCode Agent 配置 Code
     * @param currentNode 当前节点名称（可选）
     * @param reviewerPrompt 审核提示
     * @param contextData 上下文数据
     * @return 审核记录实体
     */
    public static PendingReview createSupervisorReview(String conversationCode, String agentConfigCode,
                                                        String currentNode, String reviewerPrompt,
                                                        Map<String, Object> contextData) {
        PendingReview review = create(ReviewType.REACT_AGENT_SUPERVISOR, reviewerPrompt, contextData);
        review.setConversationCode(conversationCode);
        review.setAgentConfigCode(agentConfigCode);
        review.setCurrentNode(currentNode);
        return review;
    }

    /**
     * 生成审核唯一标识（32位UUID，无连字符）
     */
    public static String generateReviewCode() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 标记为已批准
     *
     * @param reviewerId 审核人 ID
     * @param reviewComment 审核意见
     */
    public void markApproved(Long reviewerId, String reviewComment) {
        this.status = ReviewStatus.APPROVED.name();
        this.reviewResult = true;
        this.reviewerId = reviewerId;
        this.reviewComment = reviewComment;
        this.reviewedAt = LocalDateTime.now();
    }

    /**
     * 标记为已拒绝
     *
     * @param reviewerId 审核人 ID
     * @param reviewComment 拒绝原因
     */
    public void markRejected(Long reviewerId, String reviewComment) {
        this.status = ReviewStatus.REJECTED.name();
        this.reviewResult = false;
        this.reviewerId = reviewerId;
        this.reviewComment = reviewComment;
        this.reviewedAt = LocalDateTime.now();
    }

    /**
     * 增加重试次数
     */
    public void incrementRetryCount() {
        this.currentRetryCount = (this.currentRetryCount == null ? 0 : this.currentRetryCount) + 1;
    }

    /**
     * 检查是否可以重试
     *
     * @return 是否可以重试
     */
    public boolean canRetry() {
        return this.currentRetryCount == null || this.currentRetryCount < this.maxRetryCount;
    }

    /**
     * 检查是否待审核
     *
     * @return 是否待审核
     */
    public boolean isPending() {
        return ReviewStatus.PENDING.name().equals(this.status);
    }

    /**
     * 检查是否已批准
     *
     * @return 是否已批准
     */
    public boolean isApproved() {
        return ReviewStatus.APPROVED.name().equals(this.status);
    }

    /**
     * 检查是否已拒绝
     *
     * @return 是否已拒绝
     */
    public boolean isRejected() {
        return ReviewStatus.REJECTED.name().equals(this.status);
    }
}
