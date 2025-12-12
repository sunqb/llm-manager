package com.llmmanager.agent.graph.dynamic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 状态键配置 DTO
 * 用于定义工作流状态中的键和更新策略，数据库的 p_graph_workflows 表的 graph_config 字段中的 stateConfig 部分映射到此类
 *
 * JSON 格式示例：
 * {"key": "question", "append": false, "description": "原始研究问题"}
 * {"key": "search_results", "append": true, "description": "搜索结果（追加模式）"}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StateKeyConfig {

    /**
     * 状态键名称
     */
    private String key;

    /**
     * 是否为追加模式（JSON 配置使用）
     * - false: 替换策略（ReplaceStrategy）
     * - true: 追加策略（AppendStrategy）
     */
    private Boolean append;

    /**
     * 状态键描述（可选，用于文档说明）
     */
    private String description;

    /**
     * 更新策略（代码使用，可选）
     * 如果为 null，则根据 append 字段推断
     */
    private StateStrategy strategy;

    /**
     * 状态更新策略枚举
     */
    public enum StateStrategy {
        /** 替换策略：新值覆盖旧值 */
        REPLACE,

        /** 追加策略：新值追加到列表（适用于累积数据） */
        APPEND
    }

    /**
     * 判断是否为替换策略
     * 优先使用 strategy，否则根据 append 推断
     */
    public boolean isReplace() {
        if (strategy != null) {
            return strategy == StateStrategy.REPLACE;
        }
        // append 为 null 或 false 时，默认为替换策略
        return append == null || !append;
    }

    /**
     * 判断是否为追加策略
     * 优先使用 strategy，否则根据 append 推断
     */
    public boolean isAppend() {
        if (strategy != null) {
            return strategy == StateStrategy.APPEND;
        }
        // append 为 true 时才是追加策略
        return Boolean.TRUE.equals(append);
    }
}
