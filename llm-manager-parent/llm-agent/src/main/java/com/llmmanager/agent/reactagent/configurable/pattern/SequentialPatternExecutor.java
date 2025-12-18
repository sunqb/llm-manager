package com.llmmanager.agent.reactagent.configurable.pattern;

import com.llmmanager.agent.reactagent.configurable.WorkflowPattern;
import com.llmmanager.agent.reactagent.configurable.config.AgentConfig;
import com.llmmanager.agent.reactagent.configurable.config.AgentWorkflowConfig;
import com.llmmanager.agent.reactagent.core.AgentWrapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 顺序执行模式执行器
 * 
 * Agent 按照配置的顺序依次执行，前一个 Agent 的输出作为后一个的输入。
 * 
 * 执行流程：
 * 1. 获取第一个 Agent，使用用户输入执行
 * 2. 将第一个 Agent 的输出作为第二个 Agent 的输入
 * 3. 依次类推，直到所有 Agent 执行完毕
 * 4. 返回最后一个 Agent 的输出作为最终结果
 * 
 * @author LLM Manager
 */
@Slf4j
public class SequentialPatternExecutor implements PatternExecutor {

    @Override
    public WorkflowResult execute(String input, AgentWorkflowConfig config) {
        long startTime = System.currentTimeMillis();
        WorkflowResult.WorkflowResultBuilder resultBuilder = WorkflowResult.builder()
                .pattern(WorkflowPattern.SEQUENTIAL.getCode());

        List<AgentConfig> agents = config.getAgents();
        if (agents == null || agents.isEmpty()) {
            return WorkflowResult.failure("没有配置任何 Agent");
        }

        log.info("[SequentialPattern] 开始顺序执行, Agent 数量: {}", agents.size());

        String currentInput = input;
        String lastOutput = null;
        int stepNumber = 0;

        for (AgentConfig agentConfig : agents) {
            if (!agentConfig.isEnabled()) {
                log.info("[SequentialPattern] 跳过禁用的 Agent: {}", agentConfig.getName());
                continue;
            }

            stepNumber++;
            AgentWrapper agent = agentConfig.getAgent();
            String agentName = agentConfig.getName();

            log.info("[SequentialPattern] 步骤 {}: 执行 Agent '{}'", stepNumber, agentName);
            if (config.isVerboseLogging()) {
                log.info("[SequentialPattern] 输入: {}", currentInput);
            }

            long agentStartTime = System.currentTimeMillis();

            try {
                // 执行 Agent
                String output = agent.call(currentInput);
                long agentExecutionTime = System.currentTimeMillis() - agentStartTime;

                // 记录结果
                WorkflowResult.AgentStepResult stepResult = WorkflowResult.AgentStepResult.builder()
                        .agentName(agentName)
                        .input(currentInput)
                        .output(output)
                        .executionTimeMs(agentExecutionTime)
                        .success(true)
                        .build();
                resultBuilder.agentResults(new java.util.HashMap<>());

                // 记录执行步骤
                WorkflowResult.ExecutionStep step = WorkflowResult.ExecutionStep.builder()
                        .stepNumber(stepNumber)
                        .agentName(agentName)
                        .action("execute")
                        .timestamp(System.currentTimeMillis())
                        .details("执行成功，耗时 " + agentExecutionTime + "ms")
                        .build();

                log.info("[SequentialPattern] Agent '{}' 执行完成, 耗时: {}ms", 
                        agentName, agentExecutionTime);
                if (config.isVerboseLogging()) {
                    log.info("[SequentialPattern] 输出: {}", output);
                }

                // 如果配置了链式输出，将当前输出作为下一个输入
                if (config.isChainOutput()) {
                    currentInput = output;
                }
                lastOutput = output;

            } catch (Exception e) {
                log.error("[SequentialPattern] Agent '{}' 执行失败: {}", agentName, e.getMessage(), e);
                
                long totalTime = System.currentTimeMillis() - startTime;
                return WorkflowResult.builder()
                        .success(false)
                        .errorMessage("Agent '" + agentName + "' 执行失败: " + e.getMessage())
                        .pattern(WorkflowPattern.SEQUENTIAL.getCode())
                        .totalExecutionTimeMs(totalTime)
                        .build();
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("[SequentialPattern] 顺序执行完成, 总耗时: {}ms", totalTime);

        return resultBuilder
                .success(true)
                .finalResult(lastOutput)
                .totalExecutionTimeMs(totalTime)
                .build();
    }

    @Override
    public String getPatternCode() {
        return WorkflowPattern.SEQUENTIAL.getCode();
    }
}

