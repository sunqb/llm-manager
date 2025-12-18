package com.llmmanager.service.orchestration;

import com.llmmanager.agent.reactagent.autonomous.SupervisorAgentTeam;
import com.llmmanager.agent.reactagent.configurable.ConfigurableAgentWorkflow;
import com.llmmanager.agent.reactagent.configurable.WorkflowPattern;
import com.llmmanager.agent.reactagent.configurable.config.AgentConfig;
import com.llmmanager.agent.reactagent.configurable.config.AgentWorkflowConfig;
import com.llmmanager.agent.reactagent.configurable.pattern.WorkflowResult;
import com.llmmanager.agent.reactagent.core.AgentWrapper;
import com.llmmanager.agent.reactagent.registry.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ReactAgent 执行服务
 *
 * 提供 ReactAgent 的执行能力：
 * 1. 公共执行方法（供其他服务复用）：
 *    - executeAgent(AgentWrapper, message) - 执行单个 Agent
 *    - executeWorkflow(ConfigurableAgentWorkflow, message) - 执行顺序工作流
 *    - executeTeam(SupervisorAgentTeam, message) - 执行 Supervisor 团队
 *
 * 2. 预定义场景（硬编码）：
 *    - 全能助手（单个 Agent）
 *    - 研究流水线（顺序工作流）
 *    - 企业智能助手团队（Supervisor 团队）
 *
 * ChatModel 管理已统一由 ChatModelProvider 提供
 *
 * @author LLM Manager
 */
@Slf4j
@Service
public class ReactAgentExecutionService {

    @Resource
    private ChatModelProvider chatModelProvider;

    @Resource
    private ToolRegistry toolRegistry;

    // ==================== 公共执行方法（供其他服务复用） ====================

    /**
     * 执行单个 Agent（公共方法）
     *
     * @param agent   AgentWrapper 实例
     * @param message 用户消息
     * @return 执行结果
     */
    public Map<String, Object> executeAgent(AgentWrapper agent, String message) {
        Map<String, Object> response = new HashMap<>();
        try {
            String result = agent.call(message);
            response.put("success", true);
            response.put("result", result);
            response.put("agentName", agent.getName());
            response.put("agentType", "SINGLE");
        } catch (Exception e) {
            log.error("[ReactAgentExecution] Agent 执行失败: {}", agent.getName(), e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return response;
    }

    /**
     * 执行顺序工作流（公共方法）
     *
     * @param workflow ConfigurableAgentWorkflow 实例
     * @param message  用户消息
     * @return 执行结果
     */
    public Map<String, Object> executeWorkflow(ConfigurableAgentWorkflow workflow, String message) {
        Map<String, Object> response = new HashMap<>();
        try {
            WorkflowResult result = workflow.execute(message);
            response.put("success", result.isSuccess());
            response.put("finalResult", result.getFinalResult());
            response.put("agentResults", result.getAgentResults());
            response.put("executionTimeMs", result.getTotalExecutionTimeMs());
            response.put("agentType", "SEQUENTIAL");
        } catch (Exception e) {
            log.error("[ReactAgentExecution] 工作流执行失败", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return response;
    }

    /**
     * 执行 Supervisor 团队（公共方法）
     *
     * @param team    SupervisorAgentTeam 实例
     * @param message 用户消息
     * @return 执行结果
     */
    public Map<String, Object> executeTeam(SupervisorAgentTeam team, String message) {
        Map<String, Object> response = new HashMap<>();
        try {
            String result = team.execute(message);
            response.put("success", true);
            response.put("result", result);
            response.put("workerCount", team.getWorkerCount());
            response.put("agentType", "SUPERVISOR");
        } catch (Exception e) {
            log.error("[ReactAgentExecution] Supervisor 团队执行失败", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return response;
    }

    // ==================== 硬编码 Agent 执行（预定义场景） ====================

    /**
     * 执行全能助手（硬编码方式）
     *
     * 预定义的单个 Agent，拥有所有工具能力
     */
    public Map<String, Object> executeAllInOneAgent(Long modelId, String message) {
        log.info("[ReactAgentExecution] 执行全能助手，modelId: {}", modelId);

        OpenAiChatModel chatModel = chatModelProvider.getChatModelByModelId(modelId);
        List<ToolCallback> tools = toolRegistry.getAllToolCallbacks();

        AgentWrapper agent = AgentWrapper.builder()
                .name("全能助手")
                .description("一个全能助手，可以查询天气、股票、新闻、翻译、日期计算、企业知识库等")
                .chatModel(chatModel)
                .instruction("你是一个智能助手，拥有以下能力：\n" +
                        "1. 查询天气信息\n" +
                        "2. 数学计算\n" +
                        "3. 股票行情查询和分析\n" +
                        "4. 文本翻译和语言检测\n" +
                        "5. 新闻资讯获取\n" +
                        "6. 日期时间查询和计算\n" +
                        "7. 企业知识库查询（请假、报销、入职等规定）\n" +
                        "请根据用户的问题，自主决定调用哪些工具来完成任务。")
                .tools(tools)
                .build();

        // 使用公共执行方法
        return executeAgent(agent, message);
    }

    /**
     * 执行研究流水线（硬编码方式）
     *
     * 预定义的顺序工作流：研究 → 分析 → 总结
     */
    public Map<String, Object> executeResearchPipeline(Long modelId, String message) {
        log.info("[ReactAgentExecution] 执行研究流水线，modelId: {}", modelId);

        OpenAiChatModel chatModel = chatModelProvider.getChatModelByModelId(modelId);

        // 创建多个专业 Agent
        AgentWrapper researchAgent = AgentWrapper.builder()
                .name("researcher")
                .description("研究员，负责收集和整理信息")
                .chatModel(chatModel)
                .instruction("你是一个研究员。请对用户提供的主题进行深入研究，" +
                        "列出关键要点和重要信息。输出格式：要点列表。")
                .build();

        AgentWrapper analysisAgent = AgentWrapper.builder()
                .name("analyst")
                .description("分析师，负责分析和评估")
                .chatModel(chatModel)
                .instruction("你是一个分析师。请对上一步的研究结果进行深入分析，" +
                        "找出趋势、机会和挑战。输出格式：分析报告。")
                .build();

        AgentWrapper summaryAgent = AgentWrapper.builder()
                .name("summarizer")
                .description("总结专家，负责生成最终报告")
                .chatModel(chatModel)
                .instruction("你是一个总结专家。请将之前的研究和分析整合成一份" +
                        "简洁明了的执行摘要，突出关键结论和建议。")
                .build();

        // 构建顺序工作流配置
        AgentWorkflowConfig config = AgentWorkflowConfig.builder()
                .workflowName("research-pipeline")
                .pattern(WorkflowPattern.SEQUENTIAL)
                .agents(List.of(
                        AgentConfig.of("researcher", researchAgent),
                        AgentConfig.of("analyst", analysisAgent),
                        AgentConfig.of("summarizer", summaryAgent)
                ))
                .chainOutput(true)
                .build();

        ConfigurableAgentWorkflow workflow = ConfigurableAgentWorkflow.builder()
                .config(config)
                .build();

        // 使用公共执行方法
        return executeWorkflow(workflow, message);
    }

    /**
     * 执行企业智能助手团队（硬编码方式）
     *
     * 预定义的 Supervisor 团队：天气专家、股票专家、新闻专家、HR专家、翻译专家
     */
    public Map<String, Object> executeEnterpriseTeam(Long modelId, String message) {
        log.info("[ReactAgentExecution] 执行企业智能助手团队，modelId: {}", modelId);

        OpenAiChatModel chatModel = chatModelProvider.getChatModelByModelId(modelId);

        // 创建专业 Worker Agents
        AgentWrapper weatherAgent = AgentWrapper.builder()
                .name("weather-expert")
                .description("天气专家，可以查询任何城市的天气信息")
                .chatModel(chatModel)
                .instruction("你是天气专家。使用天气工具查询用户询问的城市天气。")
                .tools(toolRegistry.getToolCallbacks(List.of("weatherTools")))
                .build();

        AgentWrapper stockAgent = AgentWrapper.builder()
                .name("stock-expert")
                .description("股票专家，可以查询股票行情和提供投资分析")
                .chatModel(chatModel)
                .instruction("你是股票分析专家。使用股票工具查询行情并提供专业分析。")
                .tools(toolRegistry.getToolCallbacks(List.of("stockTools")))
                .build();

        AgentWrapper newsAgent = AgentWrapper.builder()
                .name("news-expert")
                .description("新闻专家，可以获取各类新闻资讯")
                .chatModel(chatModel)
                .instruction("你是新闻编辑。使用新闻工具获取最新资讯并整理汇报。")
                .tools(toolRegistry.getToolCallbacks(List.of("newsTools")))
                .build();

        AgentWrapper hrAgent = AgentWrapper.builder()
                .name("hr-expert")
                .description("HR专家，可以解答公司规章制度、请假报销等问题")
                .chatModel(chatModel)
                .instruction("你是HR专家。使用知识库工具查询公司规章制度并解答员工问题。")
                .tools(toolRegistry.getToolCallbacks(List.of("knowledgeTools")))
                .build();

        AgentWrapper translatorAgent = AgentWrapper.builder()
                .name("translator-expert")
                .description("翻译专家，可以进行多语言翻译")
                .chatModel(chatModel)
                .instruction("你是翻译专家。使用翻译工具完成用户的翻译需求。")
                .tools(toolRegistry.getToolCallbacks(List.of("translationTools")))
                .build();

        // 构建 Supervisor 团队
        SupervisorAgentTeam team = SupervisorAgentTeam.builder()
                .supervisorName("enterprise-assistant")
                .supervisorChatModel(chatModel)
                .supervisorInstruction("你是企业智能助手的调度员。根据用户需求，调用合适的专家来完成任务。" +
                        "可用专家：天气专家、股票专家、新闻专家、HR专家、翻译专家。")
                .worker(weatherAgent)
                .worker(stockAgent)
                .worker(newsAgent)
                .worker(hrAgent)
                .worker(translatorAgent)
                .build();

        // 使用公共执行方法
        return executeTeam(team, message);
    }

    /**
     * 获取 ChatModelProvider（供其他服务复用）
     */
    public ChatModelProvider getChatModelProvider() {
        return chatModelProvider;
    }
}
