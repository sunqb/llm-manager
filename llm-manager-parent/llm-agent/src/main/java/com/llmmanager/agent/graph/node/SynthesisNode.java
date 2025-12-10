package com.llmmanager.agent.graph.node;

import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.llmmanager.agent.graph.state.ResearchState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 综合节点
 * 将分析结果综合为最终报告
 */
@Slf4j
public class SynthesisNode implements AsyncNodeAction {

    private final ChatClient chatClient;

    public SynthesisNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("[Synthesis] 开始综合生成报告");

            Map<String, Object> updates = new HashMap<>();
            updates.put(ResearchState.KEY_CURRENT_NODE, "synthesis");

            try {
                String question = state.<String>value(ResearchState.KEY_QUESTION).orElse("");
                String analysis = state.<String>value(ResearchState.KEY_ANALYSIS).orElse("");
                List<String> searchResults = (List<String>) state.value(ResearchState.KEY_SEARCH_RESULTS)
                        .orElse(List.of());

                String prompt = String.format("""
                    你是一个专业的研究报告撰写者。请基于以下分析结果，撰写一份全面的研究报告。
                    
                    原始问题: %s
                    
                    分析结果:
                    %s
                    
                    原始信息数量: %d 条
                    
                    请撰写一份结构化的研究报告，包括：
                    1. 执行摘要
                    2. 主要发现
                    3. 详细分析
                    4. 结论与建议
                    5. 局限性说明
                    
                    报告应该直接回答用户的问题，语言清晰、逻辑严谨。
                    """, question, analysis, searchResults.size());

                String finalAnswer = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();

                updates.put(ResearchState.KEY_FINAL_ANSWER, finalAnswer);
                log.info("[Synthesis] 报告生成完成");

                return updates;
            } catch (Exception e) {
                log.error("[Synthesis] 综合失败", e);
                updates.put(ResearchState.KEY_ERROR_MESSAGE, "综合失败: " + e.getMessage());
                return updates;
            }
        });
    }
}
