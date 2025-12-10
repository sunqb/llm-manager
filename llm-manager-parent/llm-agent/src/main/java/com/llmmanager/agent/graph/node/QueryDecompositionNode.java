package com.llmmanager.agent.graph.node;

import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.llmmanager.agent.graph.state.ResearchState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 问题分解节点
 * 将复杂问题分解为可搜索的子问题
 */
@Slf4j
public class QueryDecompositionNode implements AsyncNodeAction {

    private final ChatClient chatClient;

    public QueryDecompositionNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return CompletableFuture.supplyAsync(() -> {
            String question = state.<String>value(ResearchState.KEY_QUESTION).orElse("");
            log.info("[QueryDecomposition] 开始分解问题: {}", question);

            Map<String, Object> updates = new HashMap<>();
            updates.put(ResearchState.KEY_CURRENT_NODE, "query_decomposition");

            try {
                String prompt = String.format("""
                    你是一个研究助手。请将以下复杂问题分解为3-5个可以独立搜索的子问题。
                    每个子问题应该是具体的、可搜索的。
                    
                    原始问题: %s
                    
                    请直接列出子问题，每行一个，不要编号，不要其他解释。
                    """, question);

                String response = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();

                List<String> subQuestions = Arrays.stream(response.split("\n"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .limit(5)
                        .toList();

                log.info("[QueryDecomposition] 分解出 {} 个子问题", subQuestions.size());
                updates.put(ResearchState.KEY_SUB_QUESTIONS, subQuestions);

                return updates;
            } catch (Exception e) {
                log.error("[QueryDecomposition] 问题分解失败", e);
                updates.put(ResearchState.KEY_ERROR_MESSAGE, "问题分解失败: " + e.getMessage());
                return updates;
            }
        });
    }
}
