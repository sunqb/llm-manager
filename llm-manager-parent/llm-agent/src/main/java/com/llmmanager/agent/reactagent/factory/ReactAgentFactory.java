package com.llmmanager.agent.reactagent.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmmanager.agent.mcp.McpClientManager;
import com.llmmanager.agent.reactagent.autonomous.SupervisorAgentTeam;
import com.llmmanager.agent.reactagent.config.ReactAgentConfigDTO;
import com.llmmanager.agent.reactagent.configurable.ConfigurableAgentWorkflow;
import com.llmmanager.agent.reactagent.configurable.WorkflowPattern;
import com.llmmanager.agent.reactagent.configurable.config.AgentConfig;
import com.llmmanager.agent.reactagent.configurable.config.AgentWorkflowConfig;
import com.llmmanager.agent.reactagent.core.AgentWrapper;
import com.llmmanager.agent.reactagent.registry.ToolRegistry;
import com.llmmanager.agent.storage.core.entity.ReactAgent;
import com.llmmanager.agent.storage.core.mapper.ReactAgentMapper;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ReactAgent 工厂
 * 从数据库配置构建 Agent 实例
 *
 * @author LLM Manager
 */
@Slf4j
@Component
public class ReactAgentFactory {

    @Resource
    private ReactAgentMapper reactAgentMapper;

    @Resource
    private ToolRegistry toolRegistry;

    @Resource
    private McpClientManager mcpClientManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ChatModel 缓存
    private final Map<String, OpenAiChatModel> chatModelCache = new ConcurrentHashMap<>();

    /**
     * 根据 slug 构建 SINGLE 类型的 AgentWrapper
     */
    public AgentWrapper buildSingleAgent(String slug, ChatModel chatModel) {
        ReactAgent config = getAgentBySlug(slug);
        return buildSingleAgentFromConfig(config, chatModel);
    }

    /**
     * 根据 slug 构建 SEQUENTIAL 类型的工作流
     */
    public ConfigurableAgentWorkflow buildSequentialWorkflow(String slug, ChatModel chatModel) {
        ReactAgent config = getAgentBySlug(slug);
        return buildSequentialWorkflowFromConfig(config, chatModel);
    }

    /**
     * 根据 slug 构建 SUPERVISOR 类型的团队
     */
    public SupervisorAgentTeam buildSupervisorTeam(String slug, ChatModel chatModel) {
        ReactAgent config = getAgentBySlug(slug);
        return buildSupervisorTeamFromConfig(config, chatModel);
    }

    /**
     * 从数据库配置构建 SINGLE Agent
     */
    public AgentWrapper buildSingleAgentFromConfig(ReactAgent config, ChatModel chatModel) {
        try {
            ReactAgentConfigDTO dto = parseConfig(config.getAgentConfig());

            List<ToolCallback> localTools = toolRegistry.getToolCallbacks(dto.getTools());
            List<ToolCallback> mcpTools = resolveMcpToolCallbacks(
                    dto.getEnableMcpTools(),
                    dto.getMcpServerCodes(),
                    dto.getMcpToolNames()
            );
            List<ToolCallback> tools = mergeToolCallbacks(localTools, mcpTools);

            return AgentWrapper.builder()
                    .name(config.getName())
                    .description(config.getDescription())
                    .chatModel(chatModel)
                    .instruction(dto.getInstruction())
                    .tools(tools)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("构建 SINGLE Agent 失败: " + config.getSlug(), e);
        }
    }

    /**
     * 从数据库配置构建 SEQUENTIAL 工作流
     */
    public ConfigurableAgentWorkflow buildSequentialWorkflowFromConfig(ReactAgent config, ChatModel chatModel) {
        try {
            ReactAgentConfigDTO dto = parseConfig(config.getAgentConfig());

            List<AgentConfig> agentConfigs = new ArrayList<>();
            for (ReactAgentConfigDTO.AgentDefinition agentDef : dto.getAgents()) {
                Boolean enableMcpTools = agentDef.getEnableMcpTools() != null
                        ? agentDef.getEnableMcpTools()
                        : dto.getEnableMcpTools();
                List<String> mcpServerCodes = agentDef.getMcpServerCodes() != null
                        ? agentDef.getMcpServerCodes()
                        : dto.getMcpServerCodes();
                List<String> mcpToolNames = agentDef.getMcpToolNames() != null
                        ? agentDef.getMcpToolNames()
                        : dto.getMcpToolNames();

                List<ToolCallback> localTools = toolRegistry.getToolCallbacks(agentDef.getTools());
                List<ToolCallback> mcpTools = resolveMcpToolCallbacks(enableMcpTools, mcpServerCodes, mcpToolNames);
                List<ToolCallback> tools = mergeToolCallbacks(localTools, mcpTools);

                AgentWrapper agent = AgentWrapper.builder()
                        .name(agentDef.getName())
                        .chatModel(chatModel)
                        .instruction(agentDef.getInstruction())
                        .tools(tools)
                        .build();

                agentConfigs.add(AgentConfig.of(agentDef.getName(), agent));
            }

            AgentWorkflowConfig workflowConfig = AgentWorkflowConfig.builder()
                    .workflowName(config.getName())
                    .pattern(WorkflowPattern.SEQUENTIAL)
                    .agents(agentConfigs)
                    .build();

            return ConfigurableAgentWorkflow.builder()
                    .config(workflowConfig)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("构建 SEQUENTIAL 工作流失败: " + config.getSlug(), e);
        }
    }

    /**
     * 从数据库配置构建 SUPERVISOR 团队
     */
    public SupervisorAgentTeam buildSupervisorTeamFromConfig(ReactAgent config, ChatModel chatModel) {
        try {
            ReactAgentConfigDTO dto = parseConfig(config.getAgentConfig());

            SupervisorAgentTeam.SupervisorAgentTeamBuilder teamBuilder = SupervisorAgentTeam.builder()
                    .supervisorName(config.getName())
                    .supervisorChatModel(chatModel)
                    .supervisorInstruction(dto.getSupervisorInstruction());

            // 添加 Workers
            for (ReactAgentConfigDTO.WorkerDefinition workerDef : dto.getWorkers()) {
                AgentWrapper workerAgent;

                if (workerDef.isReference()) {
                    // 引用已有 Agent
                    workerAgent = buildSingleAgent(workerDef.getRef(), chatModel);
                } else {
                    // 内联定义
                    Boolean enableMcpTools = workerDef.getEnableMcpTools() != null
                            ? workerDef.getEnableMcpTools()
                            : dto.getEnableMcpTools();
                    List<String> mcpServerCodes = workerDef.getMcpServerCodes() != null
                            ? workerDef.getMcpServerCodes()
                            : dto.getMcpServerCodes();
                    List<String> mcpToolNames = workerDef.getMcpToolNames() != null
                            ? workerDef.getMcpToolNames()
                            : dto.getMcpToolNames();

                    List<ToolCallback> localTools = toolRegistry.getToolCallbacks(workerDef.getTools());
                    List<ToolCallback> mcpTools = resolveMcpToolCallbacks(enableMcpTools, mcpServerCodes, mcpToolNames);
                    List<ToolCallback> tools = mergeToolCallbacks(localTools, mcpTools);
                    workerAgent = AgentWrapper.builder()
                            .name(workerDef.getName())
                            .chatModel(chatModel)
                            .instruction(workerDef.getInstruction())
                            .tools(tools)
                            .build();
                }

                teamBuilder.worker(workerAgent);
            }

            return teamBuilder.build();
        } catch (Exception e) {
            throw new RuntimeException("构建 SUPERVISOR 团队失败: " + config.getSlug(), e);
        }
    }

    /**
     * 根据 slug 获取 Agent 配置
     */
    public ReactAgent getAgentBySlug(String slug) {
        ReactAgent agent = reactAgentMapper.selectBySlug(slug);
        if (agent == null) {
            throw new IllegalArgumentException("Agent 不存在: " + slug);
        }
        if (!Boolean.TRUE.equals(agent.getIsActive())) {
            throw new IllegalArgumentException("Agent 未启用: " + slug);
        }
        return agent;
    }

    /**
     * 解析 agent_config JSON
     */
    private ReactAgentConfigDTO parseConfig(String agentConfig) {
        if (agentConfig == null || agentConfig.isEmpty()) {
            return new ReactAgentConfigDTO();
        }
        try {
            return objectMapper.readValue(agentConfig, ReactAgentConfigDTO.class);
        } catch (Exception e) {
            throw new RuntimeException("解析 agent_config 失败: " + e.getMessage(), e);
        }
    }

    private List<ToolCallback> resolveMcpToolCallbacks(Boolean enableMcpTools,
                                                      List<String> mcpServerCodes,
                                                      List<String> mcpToolNames) {
        if (!Boolean.TRUE.equals(enableMcpTools) || mcpClientManager == null) {
            return List.of();
        }

        List<ToolCallback> callbacks = new ArrayList<>();

        if (mcpServerCodes != null && !mcpServerCodes.isEmpty()) {
            for (String serverCode : mcpServerCodes) {
                if (serverCode == null || serverCode.isBlank()) {
                    continue;
                }
                ToolCallback[] serverCallbacks = mcpClientManager.getToolCallbacks(serverCode);
                if (serverCallbacks != null && serverCallbacks.length > 0) {
                    for (ToolCallback callback : serverCallbacks) {
                        if (callback != null) {
                            callbacks.add(callback);
                        }
                    }
                }
            }
        } else {
            ToolCallback[] allCallbacks = mcpClientManager.getAllToolCallbacks();
            if (allCallbacks != null && allCallbacks.length > 0) {
                for (ToolCallback callback : allCallbacks) {
                    if (callback != null) {
                        callbacks.add(callback);
                    }
                }
            }
        }

        if (mcpToolNames == null || mcpToolNames.isEmpty() || callbacks.isEmpty()) {
            return callbacks;
        }

        Set<String> allow = new HashSet<>();
        for (String name : mcpToolNames) {
            if (name != null && !name.isBlank()) {
                allow.add(name.trim().toLowerCase());
            }
        }
        if (allow.isEmpty()) {
            return callbacks;
        }

        List<ToolCallback> filtered = new ArrayList<>();
        for (ToolCallback callback : callbacks) {
            if (callback == null || callback.getToolDefinition() == null) {
                continue;
            }
            String toolName = callback.getToolDefinition().name();
            if (toolName != null && allow.contains(toolName.toLowerCase())) {
                filtered.add(callback);
            }
        }
        return filtered;
    }

    private List<ToolCallback> mergeToolCallbacks(List<ToolCallback> first, List<ToolCallback> second) {
        if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) {
            return List.of();
        }

        List<ToolCallback> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        addUniqueToolCallbacks(merged, seen, first);
        addUniqueToolCallbacks(merged, seen, second);

        return merged;
    }

    private void addUniqueToolCallbacks(List<ToolCallback> merged, Set<String> seen, List<ToolCallback> tools) {
        if (tools == null || tools.isEmpty()) {
            return;
        }
        for (ToolCallback callback : tools) {
            if (callback == null || callback.getToolDefinition() == null) {
                continue;
            }
            String name = callback.getToolDefinition().name();
            if (name == null || name.isBlank()) {
                continue;
            }
            String key = name.toLowerCase();
            if (seen.add(key)) {
                merged.add(callback);
            }
        }
    }

    /**
     * 创建 ChatModel（根据 baseUrl 和 apiKey）
     */
    public OpenAiChatModel createChatModel(String baseUrl, String apiKey, String modelIdentifier) {
        String cacheKey = String.format("%s_%s_%s", baseUrl, apiKey, modelIdentifier);

        return chatModelCache.computeIfAbsent(cacheKey, k -> {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .build();

            return OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(modelIdentifier)
                            .temperature(0.7)
                            .build())
                    .build();
        });
    }

    /**
     * 获取所有启用的 Agent 配置
     */
    public List<ReactAgent> getActiveAgents() {
        return reactAgentMapper.selectActiveAgents();
    }

    /**
     * 根据类型获取 Agent 配置
     */
    public List<ReactAgent> getAgentsByType(String agentType) {
        return reactAgentMapper.selectByAgentType(agentType);
    }
}
