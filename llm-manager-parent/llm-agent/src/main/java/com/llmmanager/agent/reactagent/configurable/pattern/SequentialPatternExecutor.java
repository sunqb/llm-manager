package com.llmmanager.agent.reactagent.configurable.pattern;

import com.llmmanager.agent.reactagent.configurable.WorkflowPattern;
import com.llmmanager.agent.reactagent.configurable.config.AgentConfig;
import com.llmmanager.agent.reactagent.configurable.config.AgentWorkflowConfig;
import com.llmmanager.agent.reactagent.core.AgentWrapper;
import com.llmmanager.agent.review.snapshot.SequentialStateSnapshot;
import com.llmmanager.agent.storage.core.entity.PendingReview;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
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

    /**
     * 从检查点恢复执行（用于人工审核恢复）
     *
     * 恢复流程：
     * 1. 跳过已完成的 Agent（根据 snapshot.lastCompletedAgentIndex）
     * 2. 将审核结果添加到中间结果列表
     * 3. 从下一个 Agent 继续执行
     *
     * @param review   审核记录
     * @param snapshot 状态快照
     * @param config   工作流配置（包含重建的 AgentConfig 列表）
     * @return 工作流执行结果
     */
    public WorkflowResult resumeFromCheckpoint(PendingReview review, SequentialStateSnapshot snapshot, AgentWorkflowConfig config) {
        long startTime = System.currentTimeMillis();
        WorkflowResult.WorkflowResultBuilder resultBuilder = WorkflowResult.builder()
                .pattern(WorkflowPattern.SEQUENTIAL.getCode());

        // 1. 验证快照
        if (!snapshot.isValid()) {
            return WorkflowResult.failure("快照数据无效");
        }

        // 2. 验证审核状态
        if (!review.isApproved()) {
            return WorkflowResult.failure("审核未通过，无法恢复执行");
        }

        List<AgentConfig> agents = config.getAgents();
        if (agents == null || agents.isEmpty()) {
            return WorkflowResult.failure("没有配置任何 Agent");
        }

        // 3. 恢复中间结果
        List<String> intermediateResults = new ArrayList<>();
        if (snapshot.getIntermediateResults() != null) {
            intermediateResults.addAll(snapshot.getIntermediateResults());
        }

        // 4. 添加审核结果到中间结果（作为上一个 Agent 的补充）
        String reviewResultMessage = buildReviewResultMessage(review);
        if (!intermediateResults.isEmpty()) {
            // 将审核结果附加到最后一个结果
            String lastResult = intermediateResults.get(intermediateResults.size() - 1);
            intermediateResults.set(intermediateResults.size() - 1, lastResult + "\n\n[审核结果]\n" + reviewResultMessage);
        } else {
            intermediateResults.add("[审核结果]\n" + reviewResultMessage);
        }

        // 5. 计算恢复起点
        int startIndex = snapshot.getNextAgentIndex();
        if (startIndex >= agents.size()) {
            // 所有 Agent 已执行完毕，直接返回最后的结果
            log.info("[SequentialPattern] 所有 Agent 已执行完毕，返回最终结果");
            long totalTime = System.currentTimeMillis() - startTime;
            String finalResult = intermediateResults.isEmpty() ? "" : intermediateResults.get(intermediateResults.size() - 1);
            return resultBuilder
                    .success(true)
                    .finalResult(finalResult)
                    .totalExecutionTimeMs(totalTime)
                    .build();
        }

        log.info("[SequentialPattern] 从检查点恢复执行, 起始索引: {}, 总 Agent 数: {}", startIndex, agents.size());

        // 6. 确定当前输入（如果 chainOutput 启用，使用上一个结果）
        String currentInput;
        if (config.isChainOutput() && !intermediateResults.isEmpty()) {
            currentInput = intermediateResults.get(intermediateResults.size() - 1);
        } else {
            // 使用原始消息
            currentInput = snapshot.getOriginalMessage() != null ? snapshot.getOriginalMessage() : "";
        }

        String lastOutput = currentInput;
        int stepNumber = startIndex;

        // 7. 从起点继续执行
        for (int i = startIndex; i < agents.size(); i++) {
            AgentConfig agentConfig = agents.get(i);
            if (!agentConfig.isEnabled()) {
                log.info("[SequentialPattern] 跳过禁用的 Agent: {}", agentConfig.getName());
                continue;
            }

            stepNumber++;
            AgentWrapper agent = agentConfig.getAgent();
            String agentName = agentConfig.getName();

            log.info("[SequentialPattern] 恢复执行步骤 {}: Agent '{}'", stepNumber, agentName);
            if (config.isVerboseLogging()) {
                log.info("[SequentialPattern] 输入: {}", currentInput);
            }

            long agentStartTime = System.currentTimeMillis();

            try {
                // 执行 Agent
                String output = agent.call(currentInput);
                long agentExecutionTime = System.currentTimeMillis() - agentStartTime;

                // 记录中间结果
                intermediateResults.add(output);

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
        log.info("[SequentialPattern] 恢复执行完成, 总耗时: {}ms", totalTime);

        return resultBuilder
                .success(true)
                .finalResult(lastOutput)
                .totalExecutionTimeMs(totalTime)
                .build();
    }

    /**
     * 构建审核结果消息
     */
    private String buildReviewResultMessage(PendingReview review) {
        StringBuilder sb = new StringBuilder();
        sb.append("审核状态: ").append(review.isApproved() ? "通过" : "拒绝");
        if (review.getReviewComment() != null && !review.getReviewComment().isEmpty()) {
            sb.append("\n审核意见: ").append(review.getReviewComment());
        }
        return sb.toString();
    }

    @Override
    public String getPatternCode() {
        return WorkflowPattern.SEQUENTIAL.getCode();
    }
}

