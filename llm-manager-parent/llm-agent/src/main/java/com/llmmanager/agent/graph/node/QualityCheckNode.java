package com.llmmanager.agent.graph.node;

import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.llmmanager.agent.graph.state.ResearchState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 质量检查节点
 * 评估报告质量，决定是否需要迭代
 */
@Slf4j
public class QualityCheckNode implements AsyncNodeAction {

    private final ChatClient chatClient;
    private final int maxIterations;

    public QualityCheckNode(ChatClient chatClient, int maxIterations) {
        this.chatClient = chatClient;
        this.maxIterations = maxIterations;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("[QualityCheck] 开始质量评估");

            Map<String, Object> updates = new HashMap<>();
            updates.put(ResearchState.KEY_CURRENT_NODE, "quality_check");

            try {
                String question = state.<String>value(ResearchState.KEY_QUESTION).orElse("");
                String finalAnswer = state.<String>value(ResearchState.KEY_FINAL_ANSWER).orElse("");
                int iterationCount = state.<Integer>value(ResearchState.KEY_ITERATION_COUNT).orElse(0);

                // 增加迭代计数
                iterationCount++;
                updates.put(ResearchState.KEY_ITERATION_COUNT, iterationCount);

                // 如果已达到最大迭代次数，直接通过
                if (iterationCount >= maxIterations) {
                    log.info("[QualityCheck] 达到最大迭代次数 {}，结束研究", maxIterations);
                    updates.put(ResearchState.KEY_QUALITY_SCORE, 80);
                    return updates;
                }

                String prompt = String.format("""
                    请评估以下研究报告的质量。
                    
                    原始问题: %s
                    
                    研究报告:
                    %s
                    
                    请从以下维度评分（0-100）：
                    1. 完整性：是否全面回答了问题
                    2. 准确性：信息是否准确
                    3. 逻辑性：论证是否有逻辑
                    4. 实用性：是否提供了有价值的见解
                    
                    只需要返回一个总体评分数字（0-100），不要其他内容。
                    """, question, finalAnswer);

                String scoreStr = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content()
                        .trim()
                        .replaceAll("[^0-9]", "");

                int score = 85; // 默认分数
                try {
                    score = Integer.parseInt(scoreStr);
                    score = Math.min(100, Math.max(0, score));
                } catch (NumberFormatException ignored) {
                }

                updates.put(ResearchState.KEY_QUALITY_SCORE, score);
                log.info("[QualityCheck] 质量评分: {}, 迭代次数: {}", score, iterationCount);

                return updates;
            } catch (Exception e) {
                log.error("[QualityCheck] 质量检查失败", e);
                updates.put(ResearchState.KEY_QUALITY_SCORE, 70);
                return updates;
            }
        });
    }
}
