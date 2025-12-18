package com.llmmanager.agent.reactagent.configurable;

import com.llmmanager.agent.reactagent.configurable.config.AgentWorkflowConfig;
import com.llmmanager.agent.reactagent.configurable.pattern.*;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 可配置的 Agent 工作流
 * 
 * 方案A：配置驱动的多 Agent 协作框架。
 * 
 * 核心特点：
 * - 大流程由人工配置（Sequential/Parallel/Routing）
 * - 每个节点内的 Agent 自主推理和工具调用
 * - 流程可预测、可控制
 * 
 * 支持的模式：
 * - SEQUENTIAL：顺序执行，前一个输出作为后一个输入
 * - PARALLEL：并行执行，最后合并结果
 * - ROUTING：LLM 动态路由到合适的 Agent
 * - LOOP：循环执行直到满足条件
 * 
 * 使用示例：
 * <pre>{@code
 * // 创建工作流配置
 * AgentWorkflowConfig config = AgentWorkflowConfig.builder()
 *     .pattern(WorkflowPattern.SEQUENTIAL)
 *     .agents(List.of(
 *         AgentConfig.of("researcher", researchAgent),
 *         AgentConfig.of("analyzer", analyzeAgent),
 *         AgentConfig.of("writer", writeAgent)
 *     ))
 *     .build();
 * 
 * // 创建工作流
 * ConfigurableAgentWorkflow workflow = new ConfigurableAgentWorkflow(config);
 * 
 * // 执行
 * WorkflowResult result = workflow.execute("写一篇关于AI的报告");
 * System.out.println(result.getFinalResult());
 * }</pre>
 * 
 * @author LLM Manager
 */
@Slf4j
public class ConfigurableAgentWorkflow {

    private final AgentWorkflowConfig config;
    private final Map<String, PatternExecutor> executors;

    /**
     * 创建工作流
     * 
     * @param config 工作流配置
     */
    public ConfigurableAgentWorkflow(AgentWorkflowConfig config) {
        this.config = config;
        this.executors = initializeExecutors();
        validateConfig();
    }

    /**
     * 初始化模式执行器
     */
    private Map<String, PatternExecutor> initializeExecutors() {
        Map<String, PatternExecutor> map = new HashMap<>();
        map.put(WorkflowPattern.SEQUENTIAL.getCode(), new SequentialPatternExecutor());
        map.put(WorkflowPattern.PARALLEL.getCode(), new ParallelPatternExecutor());
        map.put(WorkflowPattern.ROUTING.getCode(), new RoutingPatternExecutor());
        // LOOP 模式可以后续添加
        return map;
    }

    /**
     * 验证配置
     */
    private void validateConfig() {
        if (config == null) {
            throw new IllegalArgumentException("工作流配置不能为空");
        }
        if (config.getPattern() == null) {
            throw new IllegalArgumentException("必须指定工作流模式");
        }
        if (config.getAgents() == null || config.getAgents().isEmpty()) {
            throw new IllegalArgumentException("必须配置至少一个 Agent");
        }

        // 验证模式特定的配置
        WorkflowPattern pattern = config.getPattern();
        if (pattern == WorkflowPattern.ROUTING && config.getRoutingChatModel() == null) {
            throw new IllegalArgumentException("路由模式需要配置 routingChatModel");
        }
    }

    /**
     * 执行工作流
     * 
     * @param input 用户输入
     * @return 执行结果
     */
    public WorkflowResult execute(String input) {
        WorkflowPattern pattern = config.getPattern();
        log.info("[ConfigurableAgentWorkflow] 开始执行工作流, 模式: {}, 输入: {}", 
                pattern.getDescription(), 
                input.length() > 100 ? input.substring(0, 100) + "..." : input);

        PatternExecutor executor = executors.get(pattern.getCode());
        if (executor == null) {
            return WorkflowResult.failure("不支持的工作流模式: " + pattern.getCode());
        }

        try {
            WorkflowResult result = executor.execute(input, config);
            
            if (result.isSuccess()) {
                log.info("[ConfigurableAgentWorkflow] 工作流执行成功, 耗时: {}ms", 
                        result.getTotalExecutionTimeMs());
            } else {
                log.warn("[ConfigurableAgentWorkflow] 工作流执行失败: {}", 
                        result.getErrorMessage());
            }
            
            return result;
        } catch (Exception e) {
            log.error("[ConfigurableAgentWorkflow] 工作流执行异常: {}", e.getMessage(), e);
            return WorkflowResult.failure("工作流执行异常: " + e.getMessage());
        }
    }

    /**
     * 获取工作流配置
     */
    public AgentWorkflowConfig getConfig() {
        return config;
    }

    /**
     * 获取工作流模式
     */
    public WorkflowPattern getPattern() {
        return config.getPattern();
    }

    /**
     * 创建 Builder
     */
    public static ConfigurableAgentWorkflowBuilder builder() {
        return new ConfigurableAgentWorkflowBuilder();
    }

    /**
     * 工作流构建器
     */
    public static class ConfigurableAgentWorkflowBuilder {
        private final AgentWorkflowConfig.AgentWorkflowConfigBuilder configBuilder;

        public ConfigurableAgentWorkflowBuilder() {
            this.configBuilder = AgentWorkflowConfig.builder();
        }

        public ConfigurableAgentWorkflowBuilder config(AgentWorkflowConfig config) {
            return new ConfigurableAgentWorkflowBuilder() {
                @Override
                public ConfigurableAgentWorkflow build() {
                    return new ConfigurableAgentWorkflow(config);
                }
            };
        }

        public ConfigurableAgentWorkflow build() {
            return new ConfigurableAgentWorkflow(configBuilder.build());
        }
    }
}

