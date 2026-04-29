package com.llmmanager.agent.review.snapshot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * SUPERVISOR 团队状态快照
 *
 * 用于保存 SUPERVISOR Agent 团队的执行状态，以便在审核通过后恢复执行。
 *
 * SUPERVISOR 模式的审核策略：
 * 1. **HumanReviewTool 作为 Worker**（主要方式）：
 *    - Supervisor 可以调用 HumanReviewWorker，由人工提供反馈
 *    - 保存：ChatMemory 历史 + 当前对话状态
 *    - 恢复：继续 Supervisor 的对话，将审核结果作为 Worker 返回
 *
 * 2. **整体审核**（辅助方式）：
 *    - 团队执行完成后，整体提交审核
 *    - 保存：最终结果 + 执行历史
 *    - 恢复：直接返回批准的最终结果
 *
 * @author LLM Manager
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupervisorStateSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 关联的会话标识
     */
    private String conversationCode;

    /**
     * 关联的 Agent 配置 Code
     */
    private String agentConfigCode;

    /**
     * 审核模式：WORKER_REVIEW（作为 Worker 审核）/ FINAL_REVIEW（整体审核）
     */
    private String reviewMode;

    /**
     * Supervisor 提示词
     */
    private String supervisorPrompt;

    /**
     * Worker 列表（Agent slug 或配置）
     */
    private List<String> workers;

    /**
     * 当前执行状态（可选）
     * 例如：当前正在执行的 Worker、已完成的 Worker 列表等
     */
    private String currentState;

    /**
     * 最终结果（用于整体审核模式）
     */
    private String finalResult;

    /**
     * 执行历史（可选）
     * 记录 Supervisor 调用 Worker 的历史
     */
    private List<String> executionHistory;

    /**
     * 团队配置（JSON 格式）
     */
    private String teamConfigJson;

    /**
     * 使用的模型 ID
     */
    private Long modelId;

    /**
     * 原始用户消息
     */
    private String originalMessage;

    /**
     * 额外的元数据（可选）
     */
    private Map<String, Object> metadata;

    /**
     * 快照创建时间（毫秒时间戳）
     */
    @Builder.Default
    private Long snapshotTimestamp = System.currentTimeMillis();

    /**
     * 快照版本（用于兼容性检查）
     */
    @Builder.Default
    private String snapshotVersion = "1.0";

    /**
     * 审核模式枚举
     */
    public enum ReviewMode {
        /**
         * 作为 Worker 审核（Supervisor 调用 HumanReviewWorker）
         */
        WORKER_REVIEW,

        /**
         * 整体审核（团队执行完成后整体提交）
         */
        FINAL_REVIEW
    }

    /**
     * 验证快照是否有效
     *
     * @return 是否有效
     */
    public boolean isValid() {
        return conversationCode != null && reviewMode != null;
    }

    /**
     * 检查是否为 Worker 审核模式
     *
     * @return 是否为 Worker 审核
     */
    public boolean isWorkerReview() {
        return ReviewMode.WORKER_REVIEW.name().equals(reviewMode);
    }

    /**
     * 检查是否为整体审核模式
     *
     * @return 是否为整体审核
     */
    public boolean isFinalReview() {
        return ReviewMode.FINAL_REVIEW.name().equals(reviewMode);
    }

    /**
     * 添加执行历史
     *
     * @param historyEntry 历史记录
     */
    public void addExecutionHistory(String historyEntry) {
        if (executionHistory != null) {
            executionHistory.add(historyEntry);
        }
    }

    /**
     * 获取执行历史摘要
     *
     * @return 历史摘要字符串
     */
    public String getExecutionSummary() {
        if (executionHistory == null || executionHistory.isEmpty()) {
            return "无执行历史";
        }
        return String.join("\n", executionHistory);
    }

    @Override
    public String toString() {
        return "SupervisorStateSnapshot{" +
                "conversationCode='" + conversationCode + '\'' +
                ", agentConfigCode='" + agentConfigCode + '\'' +
                ", reviewMode='" + reviewMode + '\'' +
                ", workersCount=" + (workers != null ? workers.size() : 0) +
                ", historyCount=" + (executionHistory != null ? executionHistory.size() : 0) +
                ", snapshotVersion='" + snapshotVersion + '\'' +
                '}';
    }
}
