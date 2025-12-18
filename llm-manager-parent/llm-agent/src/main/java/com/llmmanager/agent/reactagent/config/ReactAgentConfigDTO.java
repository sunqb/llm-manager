package com.llmmanager.agent.reactagent.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ReactAgent 配置 DTO
 * 用于解析 agent_config JSON 字段
 *
 * @author LLM Manager
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactAgentConfigDTO {

    // ==================== SINGLE 类型配置 ====================

    /**
     * 系统指令
     */
    private String instruction;

    /**
     * 工具列表
     */
    private List<String> tools;

    // ==================== SEQUENTIAL 类型配置 ====================

    /**
     * Agent 序列（SEQUENTIAL 类型使用）
     */
    private List<AgentDefinition> agents;

    // ==================== SUPERVISOR 类型配置 ====================

    /**
     * Supervisor 调度指令
     */
    private String supervisorInstruction;

    /**
     * Worker 列表（SUPERVISOR 类型使用）
     */
    private List<WorkerDefinition> workers;

    // ==================== 通用配置 ====================

    /**
     * 最大迭代次数（可选）
     */
    private Integer maxIterations;

    /**
     * 温度参数（可选）
     */
    private Double temperature;

    /**
     * Agent 定义（用于 SEQUENTIAL）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentDefinition {
        /**
         * Agent 名称
         */
        private String name;

        /**
         * 系统指令
         */
        private String instruction;

        /**
         * 工具列表
         */
        private List<String> tools;
    }

    /**
     * Worker 定义（用于 SUPERVISOR）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkerDefinition {
        /**
         * 引用已有 Agent 的 slug（二选一）
         */
        private String ref;

        /**
         * 内联定义的 Agent 名称（二选一）
         */
        private String name;

        /**
         * 内联定义的系统指令
         */
        private String instruction;

        /**
         * 内联定义的工具列表
         */
        private List<String> tools;

        /**
         * 是否为引用类型
         */
        public boolean isReference() {
            return ref != null && !ref.isEmpty();
        }
    }
}

