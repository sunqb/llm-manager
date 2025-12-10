package com.llmmanager.service.orchestration;

import com.llmmanager.agent.dto.ChatRequest;
import com.llmmanager.agent.graph.GraphWorkflowService;
import com.llmmanager.agent.graph.workflow.DeepResearchWorkflow.ResearchProgress;
import com.llmmanager.agent.graph.workflow.DeepResearchWorkflow.ResearchResult;
import com.llmmanager.service.core.entity.Channel;
import com.llmmanager.service.core.entity.LlmModel;
import com.llmmanager.service.core.service.ChannelService;
import com.llmmanager.service.core.service.LlmModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.util.List;

/**
 * Graph 工作流执行服务 - 业务逻辑编排层
 * 
 * 提供 DeepResearch 等高级工作流功能的业务编排
 */
@Slf4j
@Service
public class GraphExecutionService {

    @Resource
    private LlmModelService llmModelService;

    @Resource
    private ChannelService channelService;

    @Resource
    private GraphWorkflowService graphWorkflowService;

    @Value("${spring.ai.openai.api-key:}")
    private String defaultApiKey;

    @Value("${spring.ai.openai.base-url:https://api.openai.com}")
    private String defaultBaseUrl;

    /**
     * 同步执行深度研究
     */
    public ResearchResult deepResearch(Long modelId, String question) {
        log.info("[GraphExecution] 开始深度研究, modelId: {}", modelId);
        
        ChatRequest request = buildChatRequest(modelId);
        return graphWorkflowService.deepResearch(request, question);
    }

    /**
     * 流式执行深度研究
     */
    public Flux<ResearchProgress> deepResearchStream(Long modelId, String question) {
        log.info("[GraphExecution] 开始流式深度研究, modelId: {}", modelId);
        
        ChatRequest request = buildChatRequest(modelId);
        return graphWorkflowService.deepResearchStream(request, question);
    }

    /**
     * 同步执行深度研究（带进度）
     */
    public List<ResearchProgress> deepResearchWithProgress(Long modelId, String question) {
        log.info("[GraphExecution] 开始带进度深度研究, modelId: {}", modelId);
        
        // 使用流式方法收集所有进度
        ChatRequest request = buildChatRequest(modelId);
        return graphWorkflowService.deepResearchStream(request, question)
                .collectList()
                .block();
    }

    /**
     * 构建 ChatRequest
     */
    private ChatRequest buildChatRequest(Long modelId) {
        LlmModel model = getModel(modelId);
        Channel channel = getChannel(model);

        return ChatRequest.builder()
                .channelId(channel.getId())
                .apiKey(getApiKey(channel))
                .baseUrl(getBaseUrl(channel))
                .modelIdentifier(model.getModelIdentifier())
                .temperature(model.getTemperature())
                .build();
    }

    private LlmModel getModel(Long modelId) {
        LlmModel model = llmModelService.getById(modelId);
        if (model == null) {
            throw new RuntimeException("Model not found: " + modelId);
        }
        return model;
    }

    private Channel getChannel(LlmModel model) {
        Channel channel = channelService.getById(model.getChannelId());
        if (channel == null) {
            throw new RuntimeException("Channel not found for model: " + model.getId());
        }
        return channel;
    }

    private String getApiKey(Channel channel) {
        return channel.getApiKey() != null && !channel.getApiKey().isEmpty()
                ? channel.getApiKey()
                : defaultApiKey;
    }

    private String getBaseUrl(Channel channel) {
        return channel.getBaseUrl() != null && !channel.getBaseUrl().isEmpty()
                ? channel.getBaseUrl()
                : defaultBaseUrl;
    }
}
