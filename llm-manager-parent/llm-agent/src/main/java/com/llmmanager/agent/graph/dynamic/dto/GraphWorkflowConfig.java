package com.llmmanager.agent.graph.dynamic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Graph 工作流配置 DTO
 * 完整定义一个可执行的工作流
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphWorkflowConfig {

    /**
     * 工作流名称
     */
    private String name;

    /**
     * 工作流描述
     */
    private String description;

    /**
     * 状态配置：定义所有状态键及其更新策略
     */
    private StateConfig stateConfig;

    /**
     * 节点列表
     */
    private List<NodeConfig> nodes;

    /**
     * 边列表
     */
    private List<EdgeConfig> edges;

    /**
     * 状态配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StateConfig {
        /**
         * 状态键列表
         */
        private List<StateKeyConfig> keys;

        /**
         * 初始状态值（可选）
         */
        private Map<String, Object> initialValues;
    }

    /**
     * 验证配置的完整性
     */
    public void validate() {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("工作流名称不能为空");
        }

        if (stateConfig == null || stateConfig.getKeys() == null || stateConfig.getKeys().isEmpty()) {
            throw new IllegalArgumentException("状态配置不能为空");
        }

        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("节点列表不能为空");
        }

        if (edges == null || edges.isEmpty()) {
            throw new IllegalArgumentException("边列表不能为空");
        }

        // 验证节点 ID 唯一性
        long uniqueNodeIds = nodes.stream()
                .map(NodeConfig::getId)
                .distinct()
                .count();
        if (uniqueNodeIds != nodes.size()) {
            throw new IllegalArgumentException("节点 ID 必须唯一");
        }

        // 验证边引用的节点存在
        List<String> nodeIds = nodes.stream()
                .map(NodeConfig::getId)
                .toList();

        for (EdgeConfig edge : edges) {
            if (!"START".equals(edge.getFrom()) && !nodeIds.contains(edge.getFrom())) {
                throw new IllegalArgumentException("边引用的起始节点不存在: " + edge.getFrom());
            }

            if (!"END".equals(edge.getTo()) && !nodeIds.contains(edge.getTo())) {
                throw new IllegalArgumentException("边引用的目标节点不存在: " + edge.getTo());
            }

            // 验证条件边必须有路由映射
            if (edge.isConditional() && (edge.getRoutes() == null || edge.getRoutes().isEmpty())) {
                throw new IllegalArgumentException("条件边必须配置路由映射");
            }
        }
    }
}
