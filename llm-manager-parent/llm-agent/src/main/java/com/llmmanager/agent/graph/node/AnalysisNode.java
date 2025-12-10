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
 * 分析节点
 * 分析收集到的信息，提取关键发现
 */
@Slf4j
public class AnalysisNode implements AsyncNodeAction {

    private final ChatClient chatClient;

    public AnalysisNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("[Analysis] 开始分析信息");

            Map<String, Object> updates = new HashMap<>();
            updates.put(ResearchState.KEY_CURRENT_NODE, "analysis");

            try {
                String question = state.<String>value(ResearchState.KEY_QUESTION).orElse("");
                List<String> searchResults = (List<String>) state.value(ResearchState.KEY_SEARCH_RESULTS)
                        .orElse(List.of());

                String allResults = String.join("\n\n---\n\n", searchResults);

                String prompt = String.format("""
                    你是一个高级研究分析师。请分析以下收集到的信息，并提取关键发现。
                    
                    原始问题: %s
                    
                    收集到的信息:
                    %s
                    
                    请提供：
                    1. 主要发现摘要
                    2. 关键数据点
                    3. 信息一致性分析
                    4. 信息缺口（还需要了解什么）
                    5. 初步结论
                    """, question, allResults);

                String analysis = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();

                updates.put(ResearchState.KEY_ANALYSIS, analysis);
                log.info("[Analysis] 分析完成");

                return updates;
            } catch (Exception e) {
                log.error("[Analysis] 分析失败", e);
                updates.put(ResearchState.KEY_ERROR_MESSAGE, "分析失败: " + e.getMessage());
                return updates;
            }
        });
    }
}
