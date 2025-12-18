package com.llmmanager.service.orchestration;

import com.llmmanager.agent.graph.GraphWorkflowExecutor;
import com.llmmanager.agent.graph.workflow.DeepResearchWorkflow.ResearchProgress;
import com.llmmanager.agent.graph.workflow.DeepResearchWorkflow.ResearchResult;
import com.llmmanager.service.core.entity.Channel;
import com.llmmanager.service.core.entity.LlmModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.util.List;

/**
 * Graph 工作流执行服务 - DeepResearch 业务层
 *
 * 提供 DeepResearch 深度研究工作流的业务编排
 *
 * 与 DynamicWorkflowExecutionService 的关系：
 * - GraphExecutionService：硬编码的 DeepResearch 工作流（专用返回类型）
 * - DynamicWorkflowExecutionService：从 JSON 配置动态构建的工作流
 * - 两者都复用 GraphWorkflowExecutor 的执行能力
 *
 * ChatModel 管理已统一由 ChatModelProvider 提供
 */
@Slf4j
@Service
public class GraphExecutionService {

    @Resource
    private ChatModelProvider chatModelProvider;

    @Resource
    private GraphWorkflowExecutor graphWorkflowExecutor;

    /**
     * 同步执行深度研究
     */
    public ResearchResult deepResearch(Long modelId, String question) {
        log.info("[GraphExecution] 开始深度研究, modelId: {}", modelId);

        ChatClient chatClient = chatModelProvider.getChatClientByModelId(modelId);
        String cacheKey = buildCacheKey(modelId);
        return graphWorkflowExecutor.deepResearch(chatClient, cacheKey, question);
    }

    /**
     * 流式执行深度研究
     */
    public Flux<ResearchProgress> deepResearchStream(Long modelId, String question) {
        log.info("[GraphExecution] 开始流式深度研究, modelId: {}", modelId);

        ChatClient chatClient = chatModelProvider.getChatClientByModelId(modelId);
        String cacheKey = buildCacheKey(modelId);
        return graphWorkflowExecutor.deepResearchStream(chatClient, cacheKey, question);
    }

    /**
     * 同步执行深度研究（带进度）
     */
    public List<ResearchProgress> deepResearchWithProgress(Long modelId, String question) {
        log.info("[GraphExecution] 开始带进度深度研究, modelId: {}", modelId);

        ChatClient chatClient = chatModelProvider.getChatClientByModelId(modelId);
        String cacheKey = buildCacheKey(modelId);
        return graphWorkflowExecutor.deepResearchStream(chatClient, cacheKey, question)
                .collectList()
                .block();
    }

    /**
     * 构建缓存键
     */
    private String buildCacheKey(Long modelId) {
        LlmModel model = chatModelProvider.getModel(modelId);
        Channel channel = chatModelProvider.getChannel(model);
        return String.format("%d_%s_%s_%s",
                channel.getId(),
                chatModelProvider.getApiKey(channel),
                chatModelProvider.getBaseUrl(channel),
                model.getModelIdentifier());
    }
}
