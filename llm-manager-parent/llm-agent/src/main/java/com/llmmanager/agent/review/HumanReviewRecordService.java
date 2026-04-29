package com.llmmanager.agent.review;

import com.llmmanager.agent.review.snapshot.GraphStateSnapshot;
import com.llmmanager.agent.review.snapshot.SequentialStateSnapshot;
import com.llmmanager.agent.review.snapshot.SupervisorStateSnapshot;
import com.llmmanager.agent.storage.core.entity.PendingReview;
import com.llmmanager.agent.storage.core.service.PendingReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 人工审核记录服务（仅负责创建审核记录）
 *
 * 职责：
 * 1. 创建审核记录（支持4种类型）
 * 2. 不涉及审核流程编排和恢复执行
 *
 * 设计理念：
 * - 单一职责：只负责创建审核记录
 * - 层级清晰：llm-agent 层的服务不依赖 llm-service 层
 * - 审核流程编排由 llm-service 层的 HumanReviewOrchestrationService 负责
 *
 * @author LLM Manager
 */
@Slf4j
@Service
public class HumanReviewRecordService {

    @Resource
    private PendingReviewService pendingReviewService;

    // ==================== 创建审核记录 ====================

    /**
     * 创建 Graph 工作流审核记录
     *
     * @param graphTaskId    Graph 任务 ID
     * @param currentNode    当前节点名称
     * @param reviewerPrompt 审核提示
     * @param snapshot       Graph 状态快照
     * @return 审核记录
     */
    @Transactional(rollbackFor = Exception.class)
    public PendingReview createGraphNodeReview(Long graphTaskId, String currentNode,
                                                 String reviewerPrompt, GraphStateSnapshot snapshot) {
        log.info("[HumanReviewRecordService] 创建 Graph 审核记录，任务ID: {}, 节点: {}", graphTaskId, currentNode);

        try {
            // 将快照序列化为 JSON
            Map<String, Object> contextData = Map.of(
                    "snapshot", snapshot,
                    "snapshotType", "GraphStateSnapshot"
            );

            PendingReview review = PendingReview.createGraphNodeReview(
                    graphTaskId,
                    currentNode,
                    reviewerPrompt,
                    contextData
            );

            return pendingReviewService.create(review);
        } catch (Exception e) {
            log.error("[HumanReviewRecordService] 创建 Graph 审核记录失败", e);
            throw new RuntimeException("创建 Graph 审核记录失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建 ReactAgent 工具审核记录
     *
     * @param conversationCode 会话标识
     * @param agentConfigCode  Agent 配置 Code
     * @param reviewerPrompt   审核提示
     * @param contextData      上下文数据
     * @return 审核记录
     */
    @Transactional(rollbackFor = Exception.class)
    public PendingReview createReactAgentToolReview(String conversationCode, String agentConfigCode,
                                                     String reviewerPrompt, Map<String, Object> contextData) {
        log.info("[HumanReviewRecordService] 创建 ReactAgent 工具审核记录，会话: {}", conversationCode);

        PendingReview review = PendingReview.createReactAgentToolReview(
                conversationCode,
                agentConfigCode,
                reviewerPrompt,
                contextData
        );

        return pendingReviewService.create(review);
    }

    /**
     * 创建 SEQUENTIAL 工作流审核记录
     *
     * @param conversationCode 会话标识
     * @param agentConfigCode  Agent 配置 Code
     * @param currentAgent     当前 Agent 名称
     * @param reviewerPrompt   审核提示
     * @param snapshot         Sequential 状态快照
     * @return 审核记录
     */
    @Transactional(rollbackFor = Exception.class)
    public PendingReview createSequentialReview(String conversationCode, String agentConfigCode,
                                                 String currentAgent, String reviewerPrompt,
                                                 SequentialStateSnapshot snapshot) {
        log.info("[HumanReviewRecordService] 创建 Sequential 审核记录，会话: {}, Agent: {}", conversationCode, currentAgent);

        try {
            Map<String, Object> contextData = Map.of(
                    "snapshot", snapshot,
                    "snapshotType", "SequentialStateSnapshot"
            );

            PendingReview review = PendingReview.createSequentialReview(
                    conversationCode,
                    agentConfigCode,
                    currentAgent,
                    reviewerPrompt,
                    contextData
            );

            return pendingReviewService.create(review);
        } catch (Exception e) {
            log.error("[HumanReviewRecordService] 创建 Sequential 审核记录失败", e);
            throw new RuntimeException("创建 Sequential 审核记录失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建 SUPERVISOR 团队审核记录
     *
     * @param conversationCode 会话标识
     * @param agentConfigCode  Agent 配置 Code
     * @param currentNode      当前节点名称（可选）
     * @param reviewerPrompt   审核提示
     * @param snapshot         Supervisor 状态快照
     * @return 审核记录
     */
    @Transactional(rollbackFor = Exception.class)
    public PendingReview createSupervisorReview(String conversationCode, String agentConfigCode,
                                                 String currentNode, String reviewerPrompt,
                                                 SupervisorStateSnapshot snapshot) {
        log.info("[HumanReviewRecordService] 创建 Supervisor 审核记录，会话: {}", conversationCode);

        try {
            Map<String, Object> contextData = Map.of(
                    "snapshot", snapshot,
                    "snapshotType", "SupervisorStateSnapshot"
            );

            PendingReview review = PendingReview.createSupervisorReview(
                    conversationCode,
                    agentConfigCode,
                    currentNode,
                    reviewerPrompt,
                    contextData
            );

            return pendingReviewService.create(review);
        } catch (Exception e) {
            log.error("[HumanReviewRecordService] 创建 Supervisor 审核记录失败", e);
            throw new RuntimeException("创建 Supervisor 审核记录失败: " + e.getMessage(), e);
        }
    }
}
