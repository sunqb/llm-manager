package com.llmmanager.service.orchestration;

import com.llmmanager.agent.reactagent.autonomous.SupervisorAgentTeam;
import com.llmmanager.agent.reactagent.configurable.ConfigurableAgentWorkflow;
import com.llmmanager.agent.reactagent.core.AgentWrapper;
import com.llmmanager.agent.reactagent.factory.ReactAgentFactory;
import com.llmmanager.agent.storage.core.entity.ReactAgent;
import com.llmmanager.agent.storage.core.service.ReactAgentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态 ReactAgent 执行服务
 *
 * 从数据库动态加载 ReactAgent 配置并执行，支持：
 * - SINGLE：单个 Agent
 * - SEQUENTIAL：顺序工作流
 * - SUPERVISOR：Supervisor 团队
 *
 * 与 DynamicWorkflowExecutionService 风格保持一致
 *
 * @author LLM Manager
 */
@Slf4j
@Service
public class DynamicReactAgentExecutionService {

    @Resource
    private ChatModelProvider chatModelProvider;

    @Resource
    private ReactAgentExecutionService reactAgentExecutionService;

    @Resource
    private ReactAgentFactory reactAgentFactory;

    @Resource
    private ReactAgentService reactAgentService;

    /**
     * 根据 slug 从数据库加载 Agent 并执行
     *
     * 支持三种类型：SINGLE、SEQUENTIAL、SUPERVISOR
     * 根据数据库中的 agent_type 自动选择执行方式
     * 使用 Agent 配置中的 modelId 创建 ChatModel
     *
     * @param slug    Agent 唯一标识
     * @param message 用户消息
     * @return 执行结果
     */
    public Map<String, Object> execute(String slug, String message) {
        log.info("[DynamicReactAgent] 从数据库加载 Agent，slug: {}", slug);

        try {
            // 1. 获取 Agent 配置
            ReactAgent agentConfig = reactAgentService.getBySlug(slug);
            if (agentConfig == null) {
                throw new IllegalArgumentException("Agent 不存在: " + slug);
            }

            // 2. 创建 ChatModel（使用 Agent 配置中的 modelId）
            Long modelId = agentConfig.getModelId();
            if (modelId == null) {
                throw new IllegalArgumentException("Agent 未配置模型: " + slug);
            }
            OpenAiChatModel chatModel = chatModelProvider.getChatModelByModelId(modelId);

            // 3. 根据类型执行（复用 ReactAgentExecutionService 的公共执行方法）
            ReactAgent.AgentType agentType = ReactAgent.AgentType.valueOf(agentConfig.getAgentType());

            Map<String, Object> response = switch (agentType) {
                case SINGLE -> executeSingleAgent(agentConfig, chatModel, message);
                case SEQUENTIAL -> executeSequentialWorkflow(agentConfig, chatModel, message);
                case SUPERVISOR -> executeSupervisorTeam(agentConfig, chatModel, message);
            };

            // 添加额外信息
            response.put("slug", slug);
            response.put("agentConfigName", agentConfig.getName());

            return response;

        } catch (Exception e) {
            log.error("[DynamicReactAgent] 执行失败，slug: {}", slug, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }

    /**
     * 执行单个 Agent
     * 复用 ReactAgentExecutionService 的公共执行方法
     */
    private Map<String, Object> executeSingleAgent(ReactAgent agentConfig, OpenAiChatModel chatModel, String message) {
        AgentWrapper agent = reactAgentFactory.buildSingleAgentFromConfig(agentConfig, chatModel);
        return reactAgentExecutionService.executeAgent(agent, message);
    }

    /**
     * 执行顺序工作流
     * 复用 ReactAgentExecutionService 的公共执行方法
     */
    private Map<String, Object> executeSequentialWorkflow(ReactAgent agentConfig, OpenAiChatModel chatModel, String message) {
        ConfigurableAgentWorkflow workflow = reactAgentFactory.buildSequentialWorkflowFromConfig(agentConfig, chatModel);
        return reactAgentExecutionService.executeWorkflow(workflow, message);
    }

    /**
     * 执行 Supervisor 团队
     * 复用 ReactAgentExecutionService 的公共执行方法
     */
    private Map<String, Object> executeSupervisorTeam(ReactAgent agentConfig, OpenAiChatModel chatModel, String message) {
        SupervisorAgentTeam team = reactAgentFactory.buildSupervisorTeamFromConfig(agentConfig, chatModel);
        return reactAgentExecutionService.executeTeam(team, message);
    }

    /**
     * 获取所有可用的 Agent 配置列表
     */
    public List<ReactAgent> getActiveAgents() {
        return reactAgentService.findActiveAgents();
    }

    /**
     * 根据 slug 获取 Agent 配置详情
     */
    public ReactAgent getAgentBySlug(String slug) {
        return reactAgentService.findBySlug(slug);
    }
}

