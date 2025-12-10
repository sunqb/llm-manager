package com.llmmanager.agent.graph.node;

import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.llmmanager.agent.graph.state.ResearchState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 信息收集节点
 * 模拟搜索并收集相关信息（实际项目中可集成搜索工具）
 */
@Slf4j
public class InformationGatheringNode implements AsyncNodeAction {

    private final ChatClient chatClient;

    public InformationGatheringNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("[InformationGathering] 开始收集信息");

            Map<String, Object> updates = new HashMap<>();
            updates.put(ResearchState.KEY_CURRENT_NODE, "information_gathering");

            try {
                List<String> subQuestions = (List<String>) state.value(ResearchState.KEY_SUB_QUESTIONS)
                        .orElse(List.of());

                if (subQuestions.isEmpty()) {
                    String question = state.<String>value(ResearchState.KEY_QUESTION).orElse("");
                    subQuestions = List.of(question);
                }

                List<String> searchResults = new ArrayList<>();
                for (String subQuestion : subQuestions) {
                    String prompt = String.format("""
                        作为研究助手，请提供关于以下问题的详细信息和见解。
                        假设你正在进行深度研究，提供全面、准确的信息。
                        
                        问题: %s
                        
                        请提供：
                        1. 关键事实和数据
                        2. 相关背景信息
                        3. 不同观点或争议（如果有）
                        """, subQuestion);

                    String searchResult = chatClient.prompt()
                            .user(prompt)
                            .call()
                            .content();

                    String formattedResult = String.format("【%s】\n%s", subQuestion, searchResult);
                    searchResults.add(formattedResult);
                    log.info("[InformationGathering] 完成子问题搜索: {}", 
                            subQuestion.substring(0, Math.min(50, subQuestion.length())));
                }

                updates.put(ResearchState.KEY_SEARCH_RESULTS, searchResults);
                return updates;
            } catch (Exception e) {
                log.error("[InformationGathering] 信息收集失败", e);
                updates.put(ResearchState.KEY_ERROR_MESSAGE, "信息收集失败: " + e.getMessage());
                return updates;
            }
        });
    }
}
