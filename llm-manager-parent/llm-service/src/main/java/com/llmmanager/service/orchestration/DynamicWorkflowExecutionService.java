package com.llmmanager.service.orchestration;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmmanager.agent.graph.GraphWorkflowExecutor;
import com.llmmanager.agent.graph.dynamic.DynamicGraphBuilder;
import com.llmmanager.agent.graph.dynamic.dto.GraphWorkflowConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 动态工作流执行服务
 *
 * 提供工作流执行能力，支持：
 * - 从 JSON 配置构建并执行工作流
 * - 从模型 ID 获取 ChatClient（通过 ChatModelProvider）
 * - 复用 GraphWorkflowExecutor 的公共执行方法
 *
 * 与 GraphExecutionService 的关系：
 * - GraphExecutionService：硬编码的 DeepResearch 工作流
 * - DynamicWorkflowExecutionService：从 JSON 配置动态构建的工作流
 * - 两者都复用 GraphWorkflowExecutor 的公共执行方法
 *
 * @author LLM Manager
 */
@Slf4j
@Service
public class DynamicWorkflowExecutionService {

    @Resource
    private DynamicGraphBuilder graphBuilder;

    @Resource
    private ChatModelProvider chatModelProvider;

    @Resource
    private GraphWorkflowExecutor graphWorkflowExecutor;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
            log.info("[DynamicWorkflow] 开始执行工作流，模型 ID: {}", modelId);

            // 1. 解析工作流配置
            GraphWorkflowConfig config = objectMapper.readValue(workflowConfigJson, GraphWorkflowConfig.class);
            log.info("[DynamicWorkflow] 工作流名称: {}, 节点数: {}, 边数: {}",
                    config.getName(), config.getNodes().size(), config.getEdges().size());

            // 2. 获取 ChatClient
            ChatClient chatClient = chatModelProvider.getChatClientByModelId(modelId);

            // 3. 构建 CompiledGraph
            CompiledGraph compiledGraph = graphBuilder.build(config, chatClient);

            // 4. 复用 GraphWorkflowExecutor 的公共执行方法
            Map<String, Object> result = graphWorkflowExecutor.execute(compiledGraph, initialState);

            // 5. 添加额外信息
            result.put("workflowName", config.getName());
            return result;

        } catch (JsonProcessingException e) {
            log.error("[DynamicWorkflow] 工作流配置 JSON 解析失败", e);
            return createErrorResult("工作流配置格式错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("[DynamicWorkflow] 工作流执行失败", e);
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
     * 创建错误结果
     */
    private Map<String, Object> createErrorResult(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("error", message);
        return result;
    }
}

