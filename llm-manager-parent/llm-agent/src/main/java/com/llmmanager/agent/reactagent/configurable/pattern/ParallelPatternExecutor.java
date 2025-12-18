package com.llmmanager.agent.reactagent.configurable.pattern;

import com.llmmanager.agent.reactagent.configurable.WorkflowPattern;
import com.llmmanager.agent.reactagent.configurable.config.AgentConfig;
import com.llmmanager.agent.reactagent.configurable.config.AgentWorkflowConfig;
import com.llmmanager.agent.reactagent.core.AgentWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 并行执行模式执行器
 * 
 * 多个 Agent 同时执行，最后合并结果。
 * 
 * 执行流程：
 * 1. 将用户输入同时发送给所有 Agent
 * 2. 并行等待所有 Agent 执行完成
 * 3. 收集所有 Agent 的输出
 * 4. 使用 LLM 合并结果（如果配置了合并提示词）
 * 5. 返回合并后的最终结果
 * 
 * @author LLM Manager
 */
@Slf4j
public class ParallelPatternExecutor implements PatternExecutor {

    private final ExecutorService executorService;

    public ParallelPatternExecutor() {
        this.executorService = Executors.newCachedThreadPool();
    }

    public ParallelPatternExecutor(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public WorkflowResult execute(String input, AgentWorkflowConfig config) {
        long startTime = System.currentTimeMillis();

        List<AgentConfig> agents = config.getAgents();
        if (agents == null || agents.isEmpty()) {
            return WorkflowResult.failure("没有配置任何 Agent");
        }

        // 过滤启用的 Agent
        List<AgentConfig> enabledAgents = agents.stream()
                .filter(AgentConfig::isEnabled)
                .toList();

        log.info("[ParallelPattern] 开始并行执行, Agent 数量: {}", enabledAgents.size());

        // 创建并行任务
        Map<String, Future<String>> futures = new HashMap<>();
        Map<String, Long> startTimes = new HashMap<>();

        for (AgentConfig agentConfig : enabledAgents) {
            String agentName = agentConfig.getName();
            AgentWrapper agent = agentConfig.getAgent();
            
            startTimes.put(agentName, System.currentTimeMillis());
            
            Future<String> future = executorService.submit(() -> {
                log.info("[ParallelPattern] Agent '{}' 开始执行", agentName);
                return agent.call(input);
            });
            
            futures.put(agentName, future);
        }

        // 收集结果
        Map<String, WorkflowResult.AgentStepResult> agentResults = new HashMap<>();
        List<String> outputs = new ArrayList<>();
        boolean allSuccess = true;
        StringBuilder errorMessages = new StringBuilder();

        for (Map.Entry<String, Future<String>> entry : futures.entrySet()) {
            String agentName = entry.getKey();
            Future<String> future = entry.getValue();

            try {
                long timeout = config.getGlobalTimeoutMs();
                String output = future.get(timeout, TimeUnit.MILLISECONDS);
                long executionTime = System.currentTimeMillis() - startTimes.get(agentName);

                log.info("[ParallelPattern] Agent '{}' 执行完成, 耗时: {}ms", agentName, executionTime);

                agentResults.put(agentName, WorkflowResult.AgentStepResult.builder()
                        .agentName(agentName)
                        .input(input)
                        .output(output)
                        .executionTimeMs(executionTime)
                        .success(true)
                        .build());

                outputs.add(String.format("【%s 的分析结果】\n%s", agentName, output));

            } catch (TimeoutException e) {
                log.error("[ParallelPattern] Agent '{}' 执行超时", agentName);
                allSuccess = false;
                errorMessages.append(agentName).append(": 执行超时; ");
                future.cancel(true);
            } catch (Exception e) {
                log.error("[ParallelPattern] Agent '{}' 执行失败: {}", agentName, e.getMessage());
                allSuccess = false;
                errorMessages.append(agentName).append(": ").append(e.getMessage()).append("; ");
            }
        }

        // 合并结果
        String finalResult = mergeResults(outputs, config);
        long totalTime = System.currentTimeMillis() - startTime;

        log.info("[ParallelPattern] 并行执行完成, 总耗时: {}ms", totalTime);

        return WorkflowResult.builder()
                .success(allSuccess)
                .finalResult(finalResult)
                .errorMessage(allSuccess ? null : errorMessages.toString())
                .agentResults(agentResults)
                .pattern(WorkflowPattern.PARALLEL.getCode())
                .totalExecutionTimeMs(totalTime)
                .build();
    }

    private String mergeResults(List<String> outputs, AgentWorkflowConfig config) {
        if (outputs.isEmpty()) {
            return "没有收集到任何结果";
        }

        // 如果没有配置合并 ChatModel，直接拼接结果
        ChatModel mergeChatModel = config.getMergeChatModel();
        String mergePrompt = config.getParallelMergePrompt();

        if (mergeChatModel == null || !StringUtils.hasText(mergePrompt)) {
            return String.join("\n\n", outputs);
        }

        // 使用 LLM 合并结果
        String combinedOutputs = String.join("\n\n", outputs);
        String fullPrompt = mergePrompt + "\n\n以下是各个专家的分析结果：\n\n" + combinedOutputs;

        try {
            return mergeChatModel.call(new Prompt(fullPrompt))
                    .getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("[ParallelPattern] 合并结果失败: {}", e.getMessage());
            return combinedOutputs;  // 降级为直接拼接
        }
    }

    @Override
    public String getPatternCode() {
        return WorkflowPattern.PARALLEL.getCode();
    }
}

