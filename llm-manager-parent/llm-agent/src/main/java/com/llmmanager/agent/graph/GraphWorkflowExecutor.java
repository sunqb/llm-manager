package com.llmmanager.agent.graph;

import com.llmmanager.agent.dto.ChatRequest;
import com.llmmanager.agent.graph.workflow.DeepResearchWorkflow;
import com.llmmanager.agent.graph.workflow.DeepResearchWorkflow.ResearchProgress;
import com.llmmanager.agent.graph.workflow.DeepResearchWorkflow.ResearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Graph 工作流执行器
 * 提供 DeepResearch 等高级工作流的执行能力
 *
 * 职责：业务编排层 - 执行工作流
 * 区别于 storage.core.service.GraphWorkflowService（数据访问层 - CRUD）
 */
@Slf4j
@Service
public class GraphWorkflowExecutor {

    private final Map<String, DeepResearchWorkflow> workflowCache = new ConcurrentHashMap<>();

    /**
     * 执行深度研究（同步）
     */
    public ResearchResult deepResearch(ChatRequest request, String question) {
        log.info("[Graph] 开始深度研究: {}", question);
        DeepResearchWorkflow workflow = getOrCreateWorkflow(request);
        return workflow.research(question);
    }

    /**
     * 执行深度研究（流式）
     */
    public Flux<ResearchProgress> deepResearchStream(ChatRequest request, String question) {
        log.info("[Graph] 开始流式深度研究: {}", question);
        DeepResearchWorkflow workflow = getOrCreateWorkflow(request);
        return workflow.researchStream(question);
    }

    /**
     * 获取或创建工作流实例
     */
    private DeepResearchWorkflow getOrCreateWorkflow(ChatRequest request) {
        String cacheKey = buildCacheKey(request);
        return workflowCache.computeIfAbsent(cacheKey, k -> {
            ChatClient chatClient = createChatClient(request);
            return new DeepResearchWorkflow(chatClient, 3);
        });
    }

    /**
     * 创建 ChatClient
     */
    private ChatClient createChatClient(ChatRequest request) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(request.getBaseUrl())
                .apiKey(request.getApiKey())
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(org.springframework.ai.openai.OpenAiChatOptions.builder()
                        .model(request.getModelIdentifier())
                        .temperature(request.getTemperature() != null ? request.getTemperature() : 0.7)
                        .build())
                .build();

        return ChatClient.builder(chatModel).build();
    }

    private String buildCacheKey(ChatRequest request) {
        return request.getChannelId() + "_" + request.getApiKey() + "_" + request.getBaseUrl() + "_" + request.getModelIdentifier();
    }

    /**
     * 清除指定渠道的缓存
     */
    public void clearCacheForChannel(Long channelId) {
        workflowCache.entrySet().removeIf(entry -> entry.getKey().startsWith(channelId + "_"));
    }

    /**
     * 清除所有缓存
     */
    public void clearAllCache() {
        workflowCache.clear();
    }
}
