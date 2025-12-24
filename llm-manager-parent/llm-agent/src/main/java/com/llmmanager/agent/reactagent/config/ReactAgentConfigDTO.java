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

    // ==================== MCP 工具配置（可选） ====================

    /**
     * 是否启用 MCP 工具（ToolCallback）
     * <p>
     * 说明：MCP 工具来自外部 MCP Server，需要先在 `a_mcp_servers` 配置并启用、连接成功后才可用。
     */
    private Boolean enableMcpTools;

    /**
     * 指定 MCP 服务器 code 列表（可选）
     * <p>
     * 为空时默认使用所有已连接的 MCP 服务器。
     */
    private List<String> mcpServerCodes;

    /**
     * MCP 工具名称白名单（可选）
     * <p>
     * 为空时不做过滤，使用所选服务器提供的全部 MCP 工具。
     */
    private List<String> mcpToolNames;

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

        // ========== MCP 工具配置（可选，覆盖/补充全局设置） ==========

        /**
         * 是否启用 MCP 工具（为空则继承全局 enableMcpTools）
         */
        private Boolean enableMcpTools;

        /**
         * 指定 MCP 服务器 code 列表（为空则继承全局 mcpServerCodes）
         */
        private List<String> mcpServerCodes;

        /**
         * MCP 工具名称白名单（为空则继承全局 mcpToolNames）
         */
        private List<String> mcpToolNames;
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

        // ========== MCP 工具配置（可选，覆盖/补充全局设置） ==========

        /**
         * 是否启用 MCP 工具（为空则继承全局 enableMcpTools）
         */
        private Boolean enableMcpTools;

        /**
         * 指定 MCP 服务器 code 列表（为空则继承全局 mcpServerCodes）
         */
        private List<String> mcpServerCodes;

        /**
         * MCP 工具名称白名单（为空则继承全局 mcpToolNames）
         */
        private List<String> mcpToolNames;

        /**
         * 是否为引用类型
         */
        public boolean isReference() {
            return ref != null && !ref.isEmpty();
        }
    }
}
