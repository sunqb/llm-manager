package com.llmmanager.service.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmmanager.agent.graph.GraphWorkflowExecutor;
import com.llmmanager.agent.review.snapshot.GraphStateSnapshot;
import com.llmmanager.agent.review.snapshot.SequentialStateSnapshot;
import com.llmmanager.agent.review.snapshot.SupervisorStateSnapshot;
import com.llmmanager.agent.storage.core.entity.PendingReview;
import com.llmmanager.agent.storage.core.service.PendingReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 人工审核编排服务（审核流程编排）
 *
 * 职责：
 * 1. 提交审核结果（批准/拒绝）
 * 2. 异步恢复执行
 * 3. 分类型恢复方法（Graph/ReactAgent/Sequential/Supervisor）
 *
 * 设计理念：
 * - 单一职责：只负责审核流程编排，不负责创建审核记录
 * - 层级清晰：llm-service 层可以直接依赖其他 service 层服务
 * - 无需依赖倒置：直接注入 DynamicReactAgentExecutionService 等服务
 *
 * 与 HumanReviewRecordService 的分工：
 * - HumanReviewRecordService (llm-agent): 创建审核记录
 * - HumanReviewOrchestrationService (llm-service): 审核流程编排和恢复执行
 *
 * @author LLM Manager
 */
@Slf4j
@Service
public class HumanReviewOrchestrationService {

    @Resource
    private PendingReviewService pendingReviewService;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private GraphWorkflowExecutor graphWorkflowExecutor;

    @Resource
    private DynamicReactAgentExecutionService dynamicReactAgentExecutionService;

    // ==================== 提交审核结果 ====================

    /**
     * 批准审核
     *
     * @param reviewCode    审核标识
     * @param reviewerId    审核人 ID
     * @param reviewComment 审核意见
     */
    @Transactional(rollbackFor = Exception.class)
    public void approveReview(String reviewCode, Long reviewerId, String reviewComment) {
        log.info("[HumanReviewOrchestration] 批准审核: {}, 审核人: {}", reviewCode, reviewerId);

        pendingReviewService.approve(reviewCode, reviewerId, reviewComment);

        // 获取审核记录
        PendingReview review = pendingReviewService.getByReviewCode(reviewCode);

        // 如果配置了自动恢复，则异步恢复执行
        if (Boolean.TRUE.equals(review.getResumeAfterApproval())) {
            resumeExecutionAsync(reviewCode);
        } else {
            log.info("[HumanReviewOrchestration] 审核已批准，但未配置自动恢复: {}", reviewCode);
        }
    }

    /**
     * 拒绝审核
     *
     * @param reviewCode    审核标识
     * @param reviewerId    审核人 ID
     * @param reviewComment 拒绝原因
     */
    @Transactional(rollbackFor = Exception.class)
    public void rejectReview(String reviewCode, Long reviewerId, String reviewComment) {
        log.info("[HumanReviewOrchestration] 拒绝审核: {}, 审核人: {}, 原因: {}", reviewCode, reviewerId, reviewComment);

        pendingReviewService.reject(reviewCode, reviewerId, reviewComment);

        // 拒绝审核后不恢复执行（第一版策略：拒绝 = 终止）
        log.info("[HumanReviewOrchestration] 审核已拒绝，执行已终止: {}", reviewCode);
    }

    // ==================== 异步恢复执行 ====================

    /**
     * 异步恢复执行（审核批准后调用）
     *
     * 设计理念：
     * - 使用 @Async 注解异步执行，不阻塞审核提交请求
     * - 根据审核类型路由到不同的恢复方法
     * - 失败后自动重试（受 max_retry_count 限制）
     *
     * @param reviewCode 审核标识
     */
    @Async
    public void resumeExecutionAsync(String reviewCode) {
        log.info("[HumanReviewOrchestration] 开始异步恢复执行: {}", reviewCode);

        try {
            PendingReview review = pendingReviewService.getByReviewCode(reviewCode);

            // 检查审核状态
            if (!review.isApproved()) {
                log.warn("[HumanReviewOrchestration] 审核未通过，无法恢复执行: {}, 状态: {}",
                        reviewCode, review.getStatus());
                return;
            }

            // 检查重试次数
            if (!review.canRetry()) {
                log.error("[HumanReviewOrchestration] 超过最大重试次数，放弃恢复: {}, 重试次数: {}/{}",
                        reviewCode, review.getCurrentRetryCount(), review.getMaxRetryCount());
                return;
            }

            // 根据审核类型路由到不同的恢复方法
            PendingReview.ReviewType reviewType = PendingReview.ReviewType.valueOf(review.getReviewType());

            switch (reviewType) {
                case GRAPH_NODE:
                    resumeGraphWorkflow(review);
                    break;
                case REACT_AGENT_TOOL:
                    resumeReactAgent(review);
                    break;
                case REACT_AGENT_SEQUENTIAL:
                    resumeSequentialAgent(review);
                    break;
                case REACT_AGENT_SUPERVISOR:
                    resumeSupervisorAgent(review);
                    break;
                default:
                    log.error("[HumanReviewOrchestration] 未知的审核类型: {}", review.getReviewType());
            }

        } catch (Exception e) {
            log.error("[HumanReviewOrchestration] 恢复执行失败: {}", reviewCode, e);

            // 增加重试次数
            pendingReviewService.incrementRetryCount(reviewCode);
        }
    }

    // ==================== 分类型恢复方法 ====================

    /**
     * 恢复 Graph 工作流执行
     *
     * 实现步骤：
     * 1. 从快照重建 OverAllState
     * 2. 将审核结果添加到状态中
     * 3. 调用 GraphWorkflowExecutor 从下一个节点继续执行
     *
     * @param review 审核记录
     */
    private void resumeGraphWorkflow(PendingReview review) {
        log.info("[HumanReviewOrchestration] 恢复 Graph 工作流执行: {}", review.getReviewCode());

        try {
            // 从 contextData 获取快照
            @SuppressWarnings("unchecked")
            Map<String, Object> snapshotMap = (Map<String, Object>) review.getContextData().get("snapshot");

            if (snapshotMap == null) {
                throw new IllegalStateException("快照数据不存在");
            }

            // 将 Map 转换为 GraphStateSnapshot 对象
            GraphStateSnapshot snapshot = objectMapper.convertValue(snapshotMap, GraphStateSnapshot.class);

            log.info("[HumanReviewOrchestration] Graph 快照已加载，节点: {}", snapshot.getCurrentNodeId());

            // 调用 GraphWorkflowExecutor.resumeFromReview
            Map<String, Object> result = graphWorkflowExecutor.resumeFromReview(review, snapshot);

            if (Boolean.TRUE.equals(result.get("success"))) {
                log.info("[HumanReviewOrchestration] Graph 工作流恢复执行成功");
            } else if (Boolean.TRUE.equals(result.get("needsRebuild"))) {
                log.warn("[HumanReviewOrchestration] Graph 工作流需要重新构建: {}", result.get("error"));
                // 可以在这里触发工作流重建逻辑（如果有的话）
            } else {
                log.error("[HumanReviewOrchestration] Graph 工作流恢复执行失败: {}", result.get("error"));
            }

        } catch (Exception e) {
            log.error("[HumanReviewOrchestration] 恢复 Graph 工作流失败", e);
            throw new RuntimeException("恢复 Graph 工作流失败: " + e.getMessage(), e);
        }
    }

    /**
     * 恢复 ReactAgent 执行
     *
     * 实现步骤：
     * 1. 从 conversationCode 加载 ChatMemory 历史
     * 2. 将审核结果作为新消息添加到历史
     * 3. 继续 Agent 对话
     *
     * @param review 审核记录
     */
    private void resumeReactAgent(PendingReview review) {
        log.info("[HumanReviewOrchestration] 恢复 ReactAgent 执行: {}", review.getReviewCode());

        try {
            log.info("[HumanReviewOrchestration] 会话: {}, Agent: {}",
                    review.getConversationCode(), review.getAgentConfigCode());

            // 传递完整 review 对象，让执行服务从 contextData 中提取 originalTask + content
            Map<String, Object> result = dynamicReactAgentExecutionService.resumeFromReview(review);

            if (Boolean.TRUE.equals(result.get("success"))) {
                log.info("[HumanReviewOrchestration] ReactAgent 恢复执行成功");
            } else {
                log.error("[HumanReviewOrchestration] ReactAgent 恢复执行失败: {}", result.get("error"));
            }

        } catch (Exception e) {
            log.error("[HumanReviewOrchestration] 恢复 ReactAgent 失败", e);
            throw new RuntimeException("恢复 ReactAgent 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 恢复 SEQUENTIAL 工作流执行
     *
     * 实现步骤：
     * 1. 从快照恢复中间结果
     * 2. 将审核结果添加到结果列表
     * 3. 从下一个 Agent 继续执行
     *
     * @param review 审核记录
     */
    private void resumeSequentialAgent(PendingReview review) {
        log.info("[HumanReviewOrchestration] 恢复 Sequential 工作流执行: {}", review.getReviewCode());

        try {
            // 从 contextData 获取快照
            @SuppressWarnings("unchecked")
            Map<String, Object> snapshotMap = (Map<String, Object>) review.getContextData().get("snapshot");

            if (snapshotMap == null) {
                throw new IllegalStateException("快照数据不存在");
            }

            SequentialStateSnapshot snapshot = objectMapper.convertValue(snapshotMap, SequentialStateSnapshot.class);

            log.info("[HumanReviewOrchestration] Sequential 快照已加载: {}", snapshot);

            // 调用 DynamicReactAgentExecutionService 恢复执行
            Map<String, Object> result = dynamicReactAgentExecutionService.resumeSequentialFromReview(review, snapshot);

            if (Boolean.TRUE.equals(result.get("success"))) {
                log.info("[HumanReviewOrchestration] Sequential 工作流恢复执行成功");
            } else {
                log.error("[HumanReviewOrchestration] Sequential 工作流恢复执行失败: {}", result.get("error"));
            }

        } catch (Exception e) {
            log.error("[HumanReviewOrchestration] 恢复 Sequential 工作流失败", e);
            throw new RuntimeException("恢复 Sequential 工作流失败: " + e.getMessage(), e);
        }
    }

    /**
     * 恢复 SUPERVISOR 团队执行
     *
     * 实现步骤：
     * 1. 判断审核模式（Worker 审核 / 整体审核）
     * 2. Worker 审核：将审核结果作为 Worker 返回，继续 Supervisor 对话
     * 3. 整体审核：直接返回批准的最终结果
     *
     * @param review 审核记录
     */
    private void resumeSupervisorAgent(PendingReview review) {
        log.info("[HumanReviewOrchestration] 恢复 Supervisor 团队执行: {}", review.getReviewCode());

        try {
            // 从 contextData 获取快照
            @SuppressWarnings("unchecked")
            Map<String, Object> snapshotMap = (Map<String, Object>) review.getContextData().get("snapshot");

            if (snapshotMap == null) {
                throw new IllegalStateException("快照数据不存在");
            }

            SupervisorStateSnapshot snapshot = objectMapper.convertValue(snapshotMap, SupervisorStateSnapshot.class);

            log.info("[HumanReviewOrchestration] Supervisor 快照已加载: {}, 模式: {}", snapshot, snapshot.getReviewMode());

            // 调用 DynamicReactAgentExecutionService 恢复执行
            Map<String, Object> result = dynamicReactAgentExecutionService.resumeSupervisorFromReview(review, snapshot);

            if (Boolean.TRUE.equals(result.get("success"))) {
                log.info("[HumanReviewOrchestration] Supervisor 团队恢复执行成功");
            } else if (Boolean.TRUE.equals(result.get("pendingReview"))) {
                log.info("[HumanReviewOrchestration] Supervisor 团队执行中又触发新审核: {}", result.get("reviewCode"));
            } else {
                log.error("[HumanReviewOrchestration] Supervisor 团队恢复执行失败: {}", result.get("error"));
            }

        } catch (Exception e) {
            log.error("[HumanReviewOrchestration] 恢复 Supervisor 团队失败", e);
            throw new RuntimeException("恢复 Supervisor 团队失败: " + e.getMessage(), e);
        }
    }
}
