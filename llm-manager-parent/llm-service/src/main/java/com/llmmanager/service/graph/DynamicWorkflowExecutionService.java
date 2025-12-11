package com.llmmanager.service.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmmanager.agent.graph.dynamic.DynamicGraphBuilder;
import com.llmmanager.agent.graph.dynamic.dto.GraphWorkflowConfig;
import com.llmmanager.service.core.entity.Channel;
import com.llmmanager.service.core.entity.LlmModel;
import com.llmmanager.service.core.service.ChannelService;
import com.llmmanager.service.core.service.LlmModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态工作流执行服务
 *
 * 提供工作流执行能力，支持：
 * - 从 JSON 配置构建并执行工作流
 * - 从模型 ID 获取 ChatClient
 * - 缓存已编译的工作流
 */
@Slf4j
@Service
public class DynamicWorkflowExecutionService {

    @Resource
    private DynamicGraphBuilder graphBuilder;

    @Resource
    private LlmModelService llmModelService;

    @Resource
    private ChannelService channelService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ChatModel 缓存（复用现有逻辑）
    private final Map<String, OpenAiChatModel> chatModelCache = new ConcurrentHashMap<>();

    /**
     * 执行工作流
     *
     * @param workflowConfigJson 工作流配置 JSON
     * @param modelId            模型 ID
     * @param initialState       初始状态
     * @return 执行结果
     */
    public Map<String, Object> executeWorkflow(
            String workflowConfigJson,
            Long modelId,
            Map<String, Object> initialState) {

        try {
            log.info("[DynamicWorkflowExecutionService] 开始执行工作流，模型 ID: {}", modelId);

            // 1. 解析工作流配置
            GraphWorkflowConfig config = objectMapper.readValue(workflowConfigJson, GraphWorkflowConfig.class);
            log.info("[DynamicWorkflowExecutionService] 工作流名称: {}, 节点数: {}, 边数: {}",
                    config.getName(), config.getNodes().size(), config.getEdges().size());

            // 2. 获取 ChatClient
            ChatClient chatClient = getChatClientByModelId(modelId);

            // 3. 构建 CompiledGraph
            CompiledGraph compiledGraph = graphBuilder.build(config, chatClient);

            // 4. 执行工作流
            RunnableConfig runnableConfig = RunnableConfig.builder()
                    .threadId(UUID.randomUUID().toString())
                    .build();

            Optional<OverAllState> result = compiledGraph.invoke(initialState, runnableConfig);

            // 5. 返回结果
            if (result.isPresent()) {
                Map<String, Object> finalState = result.get().data();
                log.info("[DynamicWorkflowExecutionService] 工作流执行成功，最终状态键: {}", finalState.keySet());
                return createSuccessResult(finalState);
            } else {
                log.warn("[DynamicWorkflowExecutionService] 工作流执行返回空结果");
                return createErrorResult("工作流执行返回空结果");
            }

        } catch (JsonProcessingException e) {
            log.error("[DynamicWorkflowExecutionService] 工作流配置 JSON 解析失败", e);
            return createErrorResult("工作流配置格式错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("[DynamicWorkflowExecutionService] 工作流执行失败", e);
            return createErrorResult("工作流执行失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有已注册的节点类型
     */
    public Map<String, String> getAvailableNodeTypes() {
        return graphBuilder.getRegisteredNodeTypes();
    }

    /**
     * 验证工作流配置
     */
    public Map<String, Object> validateWorkflowConfig(String workflowConfigJson) {
        try {
            GraphWorkflowConfig config = objectMapper.readValue(workflowConfigJson, GraphWorkflowConfig.class);
            config.validate();
            return Map.of(
                    "valid", true,
                    "name", config.getName(),
                    "nodeCount", config.getNodes().size(),
                    "edgeCount", config.getEdges().size()
            );
        } catch (JsonProcessingException e) {
            return Map.of("valid", false, "error", "JSON 格式错误: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return Map.of("valid", false, "error", "配置验证失败: " + e.getMessage());
        }
    }

    /**
     * 根据模型 ID 获取 ChatClient
     */
    private ChatClient getChatClientByModelId(Long modelId) {
        // 获取模型配置
        LlmModel model = llmModelService.getById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在: " + modelId);
        }

        // 获取渠道配置
        Channel channel = channelService.getById(model.getChannelId());
        if (channel == null) {
            throw new IllegalArgumentException("渠道不存在: " + model.getChannelId());
        }

        // 构建缓存 key
        String cacheKey = String.format("%d_%s_%s", channel.getId(), channel.getApiKey(), channel.getBaseUrl());

        // 获取或创建 ChatModel
        OpenAiChatModel chatModel = chatModelCache.computeIfAbsent(cacheKey, k -> {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .apiKey(channel.getApiKey())
                    .baseUrl(channel.getBaseUrl())
                    .build();

            return OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(model.getModelIdentifier())
                            .temperature(model.getTemperature() != null ? model.getTemperature() : 0.7)
                            .build())
                    .build();
        });

        return ChatClient.builder(chatModel).build();
    }

    /**
     * 创建成功结果
     */
    private Map<String, Object> createSuccessResult(Map<String, Object> state) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", state);
        return result;
    }

    /**
     * 创建错误结果
     */
    private Map<String, Object> createErrorResult(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("error", message);
        return result;
    }

    /**
     * 清除指定渠道的缓存
     */
    public void clearCacheForChannel(Long channelId) {
        chatModelCache.entrySet().removeIf(entry -> entry.getKey().startsWith(channelId + "_"));
        log.info("[DynamicWorkflowExecutionService] 已清除渠道 {} 的缓存", channelId);
    }
}
