package com.llmmanager.agent.review.snapshot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * SEQUENTIAL 工作流状态快照
 *
 * 用于保存 SEQUENTIAL Agent 工作流的执行状态，以便在审核通过后恢复执行。
 *
 * 保存内容：
 * - 最后完成的 Agent 索引
 * - 每个 Agent 的中间结果
 * - 工作流配置（Agent 列表、审核配置等）
 * - 关联的会话标识
 *
 * 恢复流程：
 * 1. 从快照恢复中间结果
 * 2. 将审核结果添加到结果列表
 * 3. 从下一个 Agent（lastCompletedAgentIndex + 1）继续执行
 *
 * @author LLM Manager
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SequentialStateSnapshot implements Serializable {

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
     * 最后完成的 Agent 索引（从 0 开始）
     * 下次从 lastCompletedAgentIndex + 1 继续
     */
    private Integer lastCompletedAgentIndex;

    /**
     * 每个 Agent 的中间结果（按顺序）
     * List<String>：每个元素是一个 Agent 的输出结果
     */
    private List<String> intermediateResults;

    /**
     * Agent 列表（顺序）
     * 每个元素是一个 Agent 的 slug 或配置
     */
    private List<String> agentSequence;

    /**
     * 审核配置的 Agent 列表（需要审核的 Agent 名称）
     * 例如：["researcher", "analyst"]
     */
    private List<String> reviewAfterAgents;

    /**
     * 工作流配置（JSON 格式）
     */
    private String workflowConfigJson;

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
     * 验证快照是否有效
     *
     * @return 是否有效
     */
    public boolean isValid() {
        return conversationCode != null
                && lastCompletedAgentIndex != null
                && intermediateResults != null
                && agentSequence != null;
    }

    /**
     * 获取下一个 Agent 索引
     *
     * @return 下一个 Agent 索引（可能超出范围）
     */
    public int getNextAgentIndex() {
        return lastCompletedAgentIndex != null ? lastCompletedAgentIndex + 1 : 0;
    }

    /**
     * 检查是否还有更多 Agent 需要执行
     *
     * @return 是否还有更多 Agent
     */
    public boolean hasMoreAgents() {
        return agentSequence != null && getNextAgentIndex() < agentSequence.size();
    }

    /**
     * 获取下一个 Agent 的标识
     *
     * @return 下一个 Agent 的 slug（可能为 null）
     */
    public String getNextAgentSlug() {
        int nextIndex = getNextAgentIndex();
        if (agentSequence != null && nextIndex >= 0 && nextIndex < agentSequence.size()) {
            return agentSequence.get(nextIndex);
        }
        return null;
    }

    /**
     * 添加中间结果
     *
     * @param result Agent 输出结果
     */
    public void addIntermediateResult(String result) {
        if (intermediateResults != null) {
            intermediateResults.add(result);
        }
    }

    /**
     * 获取所有中间结果的拼接字符串
     *
     * @return 拼接后的结果
     */
    public String getCombinedResults() {
        if (intermediateResults == null || intermediateResults.isEmpty()) {
            return "";
        }
        return String.join("\n\n---\n\n", intermediateResults);
    }

    @Override
    public String toString() {
        return "SequentialStateSnapshot{" +
                "conversationCode='" + conversationCode + '\'' +
                ", agentConfigCode='" + agentConfigCode + '\'' +
                ", lastCompletedAgentIndex=" + lastCompletedAgentIndex +
                ", agentCount=" + (agentSequence != null ? agentSequence.size() : 0) +
                ", resultsCount=" + (intermediateResults != null ? intermediateResults.size() : 0) +
                ", snapshotVersion='" + snapshotVersion + '\'' +
                '}';
    }
}
