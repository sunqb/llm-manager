package com.llmmanager.agent.reactagent.configurable.pattern;

import com.llmmanager.agent.reactagent.configurable.WorkflowPattern;
import com.llmmanager.agent.reactagent.configurable.config.AgentConfig;
import com.llmmanager.agent.reactagent.configurable.config.AgentWorkflowConfig;
import com.llmmanager.agent.reactagent.core.AgentWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LLM 路由模式执行器
 * 
 * 由 LLM 动态决定将任务路由到哪个 Agent。
 * 
 * 执行流程：
 * 1. 构建路由决策提示词，包含所有可用 Agent 的描述
 * 2. 调用 LLM 决定应该路由到哪个 Agent
 * 3. 解析 LLM 的决策结果
 * 4. 将任务发送给选中的 Agent 执行
 * 5. 返回执行结果
 * 
 * @author LLM Manager
 */
@Slf4j
public class RoutingPatternExecutor implements PatternExecutor {

    private static final String DEFAULT_ROUTING_PROMPT = """
            你是一个智能路由器，需要根据用户的问题决定应该由哪个专家来处理。
            
            可用的专家列表：
            %s
            
            用户问题：%s
            
            请分析用户问题，选择最合适的专家来处理。
            只需要回复专家的名称（name），不要包含其他内容。
            """;

    @Override
    public WorkflowResult execute(String input, AgentWorkflowConfig config) {
        long startTime = System.currentTimeMillis();

        List<AgentConfig> agents = config.getAgents();
        if (agents == null || agents.isEmpty()) {
            return WorkflowResult.failure("没有配置任何 Agent");
        }

        ChatModel routingChatModel = config.getRoutingChatModel();
        if (routingChatModel == null) {
            return WorkflowResult.failure("路由模式需要配置 routingChatModel");
        }

        // 过滤启用的 Agent
        List<AgentConfig> enabledAgents = agents.stream()
                .filter(AgentConfig::isEnabled)
                .toList();

        log.info("[RoutingPattern] 开始路由决策, 可用 Agent 数量: {}", enabledAgents.size());

        // 构建 Agent 描述列表
        String agentDescriptions = enabledAgents.stream()
                .map(a -> String.format("- name: %s, 描述: %s", 
                        a.getName(), 
                        StringUtils.hasText(a.getDescription()) ? a.getDescription() : "无描述"))
                .collect(Collectors.joining("\n"));

        // 构建路由提示词
        String routingPrompt = StringUtils.hasText(config.getRoutingSystemPrompt()) 
                ? config.getRoutingSystemPrompt() 
                : String.format(DEFAULT_ROUTING_PROMPT, agentDescriptions, input);

        if (!StringUtils.hasText(config.getRoutingSystemPrompt())) {
            routingPrompt = String.format(DEFAULT_ROUTING_PROMPT, agentDescriptions, input);
        } else {
            routingPrompt = config.getRoutingSystemPrompt() + "\n\n可用专家：\n" + agentDescriptions 
                    + "\n\n用户问题：" + input;
        }

        // 调用 LLM 进行路由决策
        String selectedAgentName;
        try {
            String decision = routingChatModel.call(new Prompt(routingPrompt))
                    .getResult().getOutput().getText().trim();
            selectedAgentName = parseRoutingDecision(decision, enabledAgents);
            log.info("[RoutingPattern] LLM 决策结果: {} -> 选中 Agent: {}", decision, selectedAgentName);
        } catch (Exception e) {
            log.error("[RoutingPattern] 路由决策失败: {}", e.getMessage());
            return WorkflowResult.failure("路由决策失败: " + e.getMessage());
        }

        // 查找选中的 Agent
        AgentConfig selectedAgent = enabledAgents.stream()
                .filter(a -> a.getName().equalsIgnoreCase(selectedAgentName))
                .findFirst()
                .orElse(null);

        if (selectedAgent == null) {
            log.warn("[RoutingPattern] 未找到 Agent: {}, 使用第一个 Agent", selectedAgentName);
            selectedAgent = enabledAgents.get(0);
        }

        // 执行选中的 Agent
        log.info("[RoutingPattern] 执行 Agent: {}", selectedAgent.getName());
        long agentStartTime = System.currentTimeMillis();

        try {
            AgentWrapper agent = selectedAgent.getAgent();
            String output = agent.call(input);
            long agentExecutionTime = System.currentTimeMillis() - agentStartTime;
            long totalTime = System.currentTimeMillis() - startTime;

            log.info("[RoutingPattern] Agent '{}' 执行完成, 耗时: {}ms, 总耗时: {}ms", 
                    selectedAgent.getName(), agentExecutionTime, totalTime);

            Map<String, WorkflowResult.AgentStepResult> agentResults = new HashMap<>();
            agentResults.put(selectedAgent.getName(), WorkflowResult.AgentStepResult.builder()
                    .agentName(selectedAgent.getName())
                    .input(input)
                    .output(output)
                    .executionTimeMs(agentExecutionTime)
                    .success(true)
                    .build());

            return WorkflowResult.builder()
                    .success(true)
                    .finalResult(output)
                    .agentResults(agentResults)
                    .pattern(WorkflowPattern.ROUTING.getCode())
                    .totalExecutionTimeMs(totalTime)
                    .build();

        } catch (Exception e) {
            log.error("[RoutingPattern] Agent '{}' 执行失败: {}", selectedAgent.getName(), e.getMessage());
            return WorkflowResult.failure("Agent 执行失败: " + e.getMessage());
        }
    }

    private String parseRoutingDecision(String decision, List<AgentConfig> agents) {
        // 尝试精确匹配
        for (AgentConfig agent : agents) {
            if (decision.equalsIgnoreCase(agent.getName())) {
                return agent.getName();
            }
        }
        // 尝试包含匹配
        for (AgentConfig agent : agents) {
            if (decision.toLowerCase().contains(agent.getName().toLowerCase())) {
                return agent.getName();
            }
        }
        // 返回原始决策
        return decision;
    }

    @Override
    public String getPatternCode() {
        return WorkflowPattern.ROUTING.getCode();
    }
}

