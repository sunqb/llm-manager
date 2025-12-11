package com.llmmanager.agent.graph.dynamic;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmmanager.agent.graph.dynamic.dto.GraphWorkflowConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 动态工作流测试服务
 *
 * 用于快速测试 DynamicGraphBuilder 功能
 */
@Slf4j
@Service
public class DynamicGraphTestService {

    private final DynamicGraphBuilder graphBuilder;
    private final ObjectMapper objectMapper;

    @Autowired
    public DynamicGraphTestService(DynamicGraphBuilder graphBuilder) {
        this.graphBuilder = graphBuilder;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 从 JSON 字符串执行工作流
     *
     * @param configJson  工作流配置 JSON
     * @param apiKey      OpenAI API Key
     * @param baseUrl     OpenAI Base URL
     * @param model       模型标识符
     * @param initialState 初始状态
     * @return 执行结果
     */
    public Map<String, Object> executeFromJson(
            String configJson,
            String apiKey,
            String baseUrl,
            String model,
            Map<String, Object> initialState
    ) {
        try {
            log.info("[DynamicGraphTestService] 开始解析工作流配置");

            // 1. 解析 JSON 配置
            GraphWorkflowConfig config = objectMapper.readValue(configJson, GraphWorkflowConfig.class);
            log.info("[DynamicGraphTestService] 工作流名称: {}, 节点数: {}, 边数: {}",
                    config.getName(), config.getNodes().size(), config.getEdges().size());

            // 2. 创建 ChatClient
            ChatClient chatClient = createChatClient(apiKey, baseUrl, model);

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
                log.info("[DynamicGraphTestService] 工作流执行成功，最终状态键: {}", finalState.keySet());
                return finalState;
            } else {
                log.warn("[DynamicGraphTestService] 工作流执行返回空结果");
                return Map.of("error", "执行失败");
            }

        } catch (Exception e) {
            log.error("[DynamicGraphTestService] 工作流执行失败", e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 创建 ChatClient
     */
    private ChatClient createChatClient(String apiKey, String baseUrl, String model) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(0.7)
                        .build())
                .build();

        return ChatClient.builder(chatModel).build();
    }

    /**
     * 获取所有已注册的节点类型
     */
    public Map<String, String> getAvailableNodeTypes() {
        return graphBuilder.getRegisteredNodeTypes();
    }
}
