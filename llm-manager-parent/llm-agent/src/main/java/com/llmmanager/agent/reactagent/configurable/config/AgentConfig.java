package com.llmmanager.agent.reactagent.configurable.config;

import com.llmmanager.agent.reactagent.core.AgentWrapper;
import lombok.Builder;
import lombok.Data;

/**
 * 单个 Agent 配置
 * 
 * 用于在工作流中配置单个 Agent 的属性。
 * 
 * @author LLM Manager
 */
@Data
@Builder
public class AgentConfig {

    /**
     * Agent 名称/标识符
     */
    private String name;

    /**
     * Agent 实例
     */
    private AgentWrapper agent;

    /**
     * Agent 描述（用于路由模式的 LLM 决策）
     */
    private String description;

    /**
     * 是否启用
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * 执行超时时间（毫秒）
     */
    @Builder.Default
    private long timeoutMs = 60000;

    /**
     * 快速创建 AgentConfig
     * 
     * @param name Agent 名称
     * @param agent Agent 实例
     * @return AgentConfig
     */
    public static AgentConfig of(String name, AgentWrapper agent) {
        return AgentConfig.builder()
                .name(name)
                .agent(agent)
                .build();
    }

    /**
     * 快速创建 AgentConfig（带描述）
     * 
     * @param name Agent 名称
     * @param agent Agent 实例
     * @param description Agent 描述
     * @return AgentConfig
     */
    public static AgentConfig of(String name, AgentWrapper agent, String description) {
        return AgentConfig.builder()
                .name(name)
                .agent(agent)
                .description(description)
                .build();
    }
}

