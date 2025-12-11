package com.llmmanager.agent.graph.dynamic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 边配置 DTO
 * 用于定义工作流中节点间的连接关系
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdgeConfig {

    /**
     * 起始节点 ID（"START" 表示工作流起点）
     */
    private String from;

    /**
     * 目标节点 ID（"END" 表示工作流终点）
     */
    private String to;

    /**
     * 边的类型：SIMPLE（简单边）、CONDITIONAL（条件边）
     */
    private EdgeType type;

    /**
     * 条件路由映射（仅用于 CONDITIONAL 类型）
     * 格式：{"路由值" -> "目标节点ID"}
     * 示例：{"continue" -> "node_2", "end" -> "END"}
     */
    private Map<String, String> routes;

    /**
     * 边类型枚举
     */
    public enum EdgeType {
        /** 简单边：固定连接 */
        SIMPLE,

        /** 条件边：根据状态值动态路由 */
        CONDITIONAL
    }

    /**
     * 判断是否为简单边
     */
    public boolean isSimple() {
        return type == EdgeType.SIMPLE;
    }

    /**
     * 判断是否为条件边
     */
    public boolean isConditional() {
        return type == EdgeType.CONDITIONAL;
    }
}
