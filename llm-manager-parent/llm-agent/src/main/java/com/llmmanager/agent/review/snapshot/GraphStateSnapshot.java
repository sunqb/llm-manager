package com.llmmanager.agent.review.snapshot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * Graph 工作流状态快照
 *
 * 用于保存 Graph 工作流的执行状态，以便在审核通过后恢复执行。
 *
 * 保存内容：
 * - 当前节点 ID
 * - OverAllState 的所有状态值
 * - Graph 配置（JSON 格式）
 * - 关联的任务 ID
 *
 * 恢复流程：
 * 1. 从快照重建 OverAllState
 * 2. 将审核结果添加到状态中
 * 3. 从下一个节点继续执行
 *
 * @author LLM Manager
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphStateSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 关联的 Graph 任务 ID（a_graph_tasks 表）
     */
    private Long graphTaskId;

    /**
     * 当前节点 ID（暂停点）
     */
    private String currentNodeId;

    /**
     * 下一个节点 ID（恢复点，null 表示需要通过条件路由决定）
     */
    private String nextNodeId;

    /**
     * OverAllState 的所有状态值（Map<String, Object>）
     * 从 OverAllState.values() 获取
     */
    private Map<String, Object> stateValues;

    /**
     * Graph 配置（JSON 格式）
     * 包含 nodes、edges、state_config 等信息
     */
    private String graphConfigJson;

    /**
     * Graph 工作流类型（如 DEEP_RESEARCH、CUSTOM）
     */
    private String workflowType;

    /**
     * 使用的模型 ID
     */
    private Long modelId;

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
        return currentNodeId != null && stateValues != null;
    }

    /**
     * 获取状态值
     *
     * @param key 状态键
     * @return 状态值（可能为 null）
     */
    public Object getStateValue(String key) {
        return stateValues != null ? stateValues.get(key) : null;
    }

    /**
     * 添加状态值
     *
     * @param key 状态键
     * @param value 状态值
     */
    public void putStateValue(String key, Object value) {
        if (stateValues != null) {
            stateValues.put(key, value);
        }
    }

    @Override
    public String toString() {
        return "GraphStateSnapshot{" +
                "graphTaskId=" + graphTaskId +
                ", currentNodeId='" + currentNodeId + '\'' +
                ", nextNodeId='" + nextNodeId + '\'' +
                ", workflowType='" + workflowType + '\'' +
                ", modelId=" + modelId +
                ", snapshotVersion='" + snapshotVersion + '\'' +
                '}';
    }
}
