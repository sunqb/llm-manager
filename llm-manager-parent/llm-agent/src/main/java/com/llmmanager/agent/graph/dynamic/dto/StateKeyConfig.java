package com.llmmanager.agent.graph.dynamic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 状态键配置 DTO
 * 用于定义工作流状态中的键和更新策略
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
     * 更新策略：REPLACE（替换）、APPEND（追加）
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
     */
    public boolean isReplace() {
        return strategy == StateStrategy.REPLACE;
    }

    /**
     * 判断是否为追加策略
     */
    public boolean isAppend() {
        return strategy == StateStrategy.APPEND;
    }
}
