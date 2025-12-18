package com.llmmanager.agent.reactagent.core;

import com.alibaba.cloud.ai.graph.agent.AgentTool;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;

/**
 * Agent-as-Tool 适配器
 *
 * 将 ReactAgent 包装为 ToolCallback，使其可以被其他 Agent 作为工具调用。
 * 这是实现 Supervisor + Workers 模式的核心组件。
 *
 * 核心功能：
 * - 将 Agent 转换为 Tool
 * - 支持 Supervisor Agent 自主决定调用哪个 Worker Agent
 *
 * 使用示例：
 * <pre>{@code
 * // 创建 Worker Agent
 * AgentWrapper researchAgent = AgentWrapper.builder()
 *     .name("research-agent")
 *     .chatModel(chatModel)
 *     .instruction("你是一个研究专家...")
 *     .build();
 *
 * // 将 Agent 转换为 Tool
 * ToolCallback researchTool = AgentToolAdapter.asTool(researchAgent);
 *
 * // Supervisor Agent 使用 Worker Agent 作为工具
 * AgentWrapper supervisor = AgentWrapper.builder()
 *     .name("supervisor")
 *     .chatModel(chatModel)
 *     .instruction("你是一个任务协调者...")
 *     .tools(List.of(researchTool, analysisTool))
 *     .build();
 * }</pre>
 *
 * 注意：Agent 的 name 和 description 需要在创建 ReactAgent 时设置，
 * AgentTool.create() 会自动使用这些属性。
 *
 * @author LLM Manager
 */
@Slf4j
public class AgentToolAdapter {

    private AgentToolAdapter() {
        // 工具类，禁止实例化
    }

    /**
     * 将 AgentWrapper 转换为 ToolCallback
     *
     * 使用 Spring AI Alibaba 的 AgentTool.create() 方法将 ReactAgent 转换为 ToolCallback。
     * Agent 的 name 和 description 会自动从 ReactAgent 中获取。
     *
     * @param agentWrapper 要转换的 AgentWrapper
     * @return ToolCallback 实例
     */
    public static ToolCallback asTool(AgentWrapper agentWrapper) {
        if (agentWrapper == null) {
            throw new IllegalArgumentException("AgentWrapper 不能为空");
        }

        ReactAgent reactAgent = agentWrapper.getReactAgent();

        log.info("[AgentToolAdapter] 将 Agent '{}' 转换为 Tool", agentWrapper.getName());

        // 使用 Spring AI Alibaba 的 AgentTool.create() 静态方法
        return AgentTool.create(reactAgent);
    }

    /**
     * 将 ReactAgent 直接转换为 ToolCallback
     *
     * @param reactAgent 要转换的 ReactAgent
     * @return ToolCallback 实例
     */
    public static ToolCallback asTool(ReactAgent reactAgent) {
        if (reactAgent == null) {
            throw new IllegalArgumentException("ReactAgent 不能为空");
        }

        log.info("[AgentToolAdapter] 将 ReactAgent '{}' 转换为 Tool", reactAgent.name());

        return AgentTool.create(reactAgent);
    }
}

