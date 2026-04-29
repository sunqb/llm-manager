package com.llmmanager.service.orchestration;

import com.llmmanager.agent.reactagent.autonomous.SupervisorAgentTeam;
import com.llmmanager.agent.reactagent.configurable.ConfigurableAgentWorkflow;
import com.llmmanager.agent.reactagent.configurable.pattern.SequentialPatternExecutor;
import com.llmmanager.agent.reactagent.configurable.pattern.WorkflowResult;
import com.llmmanager.agent.reactagent.core.AgentWrapper;
import com.llmmanager.agent.reactagent.factory.ReactAgentFactory;
import com.llmmanager.agent.review.context.HumanReviewContext;
import com.llmmanager.agent.review.context.HumanReviewContextHolder;
import com.llmmanager.agent.review.exception.HumanReviewRequiredException;
import com.llmmanager.agent.review.snapshot.SequentialStateSnapshot;
import com.llmmanager.agent.review.snapshot.SupervisorStateSnapshot;
import com.llmmanager.agent.storage.core.entity.PendingReview;
import com.llmmanager.agent.storage.core.entity.ReactAgent;
import com.llmmanager.agent.storage.core.service.ReactAgentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        return execute(slug, message, null);
    }

    /**
     * 根据 slug 从数据库加载 Agent 并执行（带会话标识）
     *
     * 支持三种类型：SINGLE、SEQUENTIAL、SUPERVISOR
     * 根据数据库中的 agent_type 自动选择执行方式
     * 使用 Agent 配置中的 modelId 创建 ChatModel
     *
     * @param slug             Agent 唯一标识
     * @param message          用户消息
     * @param conversationCode 会话标识（用于人工审核上下文）
     * @return 执行结果
     */
    public Map<String, Object> execute(String slug, String message, String conversationCode) {
        log.info("[DynamicReactAgent] 从数据库加载 Agent，slug: {}", slug);

        try {
            // 1. 获取 Agent 配置
            ReactAgent agentConfig = reactAgentService.getBySlug(slug);
            if (agentConfig == null) {
                throw new IllegalArgumentException("Agent 不存在: " + slug);
            }

            // 2. 设置人工审核上下文（供 HumanReviewTool 使用）
            String effectiveConversationCode = conversationCode != null
                    ? conversationCode
                    : UUID.randomUUID().toString().replace("-", "");

            HumanReviewContext reviewContext = HumanReviewContext.builder()
                    .conversationCode(effectiveConversationCode)
                    .agentConfigCode(agentConfig.getSlug())
                    .agentName(agentConfig.getName())
                    .agentType(agentConfig.getAgentType())
                    .originalTask(message)
                    .build();
            HumanReviewContextHolder.setContext(reviewContext);

            try {
                // 3. 创建 ChatModel（使用 Agent 配置中的 modelId）
                Long modelId = agentConfig.getModelId();
                if (modelId == null) {
                    throw new IllegalArgumentException("Agent 未配置模型: " + slug);
                }
                OpenAiChatModel chatModel = chatModelProvider.getChatModelByModelId(modelId);

                // 4. 根据类型执行（复用 ReactAgentExecutionService 的公共执行方法）
                ReactAgent.AgentType agentType = ReactAgent.AgentType.valueOf(agentConfig.getAgentType());

                Map<String, Object> response = switch (agentType) {
                    case SINGLE -> executeSingleAgent(agentConfig, chatModel, message);
                    case SEQUENTIAL -> executeSequentialWorkflow(agentConfig, chatModel, message);
                    case SUPERVISOR -> executeSupervisorTeam(agentConfig, chatModel, message);
                };

                // 添加额外信息
                response.put("slug", slug);
                response.put("agentConfigName", agentConfig.getName());
                response.put("conversationCode", effectiveConversationCode);

                return response;

            } catch (HumanReviewRequiredException e) {
                // 人工审核异常 - 需要暂停执行等待审核
                log.info("[DynamicReactAgent] 执行暂停，等待人工审核，reviewCode: {}", e.getReviewCode());
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("pendingReview", true);
                response.put("reviewCode", e.getReviewCode());
                response.put("reviewPrompt", e.getReviewPrompt());
                response.put("reviewType", e.getReviewType());
                response.put("slug", slug);
                response.put("conversationCode", effectiveConversationCode);
                return response;
            } finally {
                // 确保清除上下文，防止内存泄漏
                HumanReviewContextHolder.clear();
            }

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

    // ==================== 人工审核恢复执行 ====================

    /**
     * 从审核记录恢复 ReactAgent 执行（带完整上下文）
     *
     * 使用 PendingReview.contextData 中的 originalTask + content 重建富上下文消息，
     * 让 Agent 知道：之前在做什么任务、提交了什么内容、审核结果如何。
     *
     * @param review 审核记录（含 contextData）
     * @return 执行结果
     */
    public Map<String, Object> resumeFromReview(PendingReview review) {
        log.info("[DynamicReactAgent] 从审核记录恢复执行，reviewCode: {}", review.getReviewCode());

        String conversationCode = review.getConversationCode();
        String agentConfigCode = review.getAgentConfigCode();

        // 从 contextData 中提取原始任务和审核内容
        Map<String, Object> contextData = review.getContextData();
        String originalTask = contextData != null ? (String) contextData.get("originalTask") : null;
        String submittedContent = contextData != null ? (String) contextData.get("content") : null;

        // 构建审核结果描述
        boolean approved = Boolean.TRUE.equals(review.getReviewResult());
        String reviewResultText = approved ? "已批准" : "已拒绝";
        String reviewComment = review.getReviewComment();

        // 构建富上下文恢复消息
        String resumeMessage = buildRichResumeMessage(originalTask, submittedContent, reviewResultText, reviewComment);

        log.info("[DynamicReactAgent] 构建恢复消息，originalTask 存在: {}, submittedContent 存在: {}",
                originalTask != null, submittedContent != null);

        // 若无 agentConfigCode，尝试用 modelId fallback（single/modelId 路径）
        if (agentConfigCode == null || agentConfigCode.isBlank()) {
            Long modelId = null;
            if (contextData != null && contextData.get("modelId") != null) {
                Object rawModelId = contextData.get("modelId");
                if (rawModelId instanceof Number) {
                    modelId = ((Number) rawModelId).longValue();
                }
            }
            if (modelId != null) {
                log.info("[DynamicReactAgent] agentConfigCode 为空，使用 modelId={} 恢复执行", modelId);
                return reactAgentExecutionService.executeAllInOneAgent(modelId, resumeMessage);
            } else {
                log.warn("[DynamicReactAgent] agentConfigCode 和 modelId 均为空，无法恢复执行");
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "恢复执行失败：缺少 agentConfigCode 和 modelId");
                return errorResponse;
            }
        }

        return resumeFromReview(conversationCode, agentConfigCode, resumeMessage);
    }

    /**
     * 从审核记录恢复 ReactAgent 执行（原有方法，保持向后兼容）
     *
     * @param conversationCode 会话标识
     * @param agentConfigCode  Agent 配置 Code（slug）
     * @param reviewMessage    审核结果消息
     * @return 执行结果
     */
    public Map<String, Object> resumeFromReview(String conversationCode, String agentConfigCode, String reviewMessage) {
        log.info("[DynamicReactAgent] 恢复执行，会话: {}, Agent: {}", conversationCode, agentConfigCode);

        try {
            // 1. 获取 Agent 配置
            ReactAgent agentConfig = reactAgentService.getBySlug(agentConfigCode);
            if (agentConfig == null) {
                throw new IllegalArgumentException("Agent 不存在: " + agentConfigCode);
            }

            // 2. 构建审核结果消息
            String resumeMessage = buildResumeMessage(reviewMessage);

            // 3. 使用原会话标识继续执行
            return execute(agentConfigCode, resumeMessage, conversationCode);

        } catch (Exception e) {
            log.error("[DynamicReactAgent] 恢复执行失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "恢复执行失败: " + e.getMessage());
            return errorResponse;
        }
    }

    /**
     * 构建恢复执行消息（简单版）
     */
    private String buildResumeMessage(String reviewMessage) {
        return String.format(
                "[系统消息] 人工审核已完成。审核结果：%s\n\n请继续执行之前的任务。",
                reviewMessage
        );
    }

    /**
     * 构建富上下文恢复消息（包含原始任务、提交内容、审核结果）
     */
    private String buildRichResumeMessage(String originalTask, String submittedContent,
                                          String reviewResultText, String reviewComment) {
        StringBuilder sb = new StringBuilder();
        sb.append("你之前在处理一个任务，并提交了内容请求人工审核。\n\n");

        if (originalTask != null && !originalTask.isBlank()) {
            sb.append("原始任务：\n").append(originalTask).append("\n\n");
        }

        if (submittedContent != null && !submittedContent.isBlank()) {
            sb.append("你提交审核的内容：\n").append(submittedContent).append("\n\n");
        }

        sb.append("审核结果：").append(reviewResultText).append("\n");
        if (reviewComment != null && !reviewComment.isBlank()) {
            sb.append("审核意见：").append(reviewComment).append("\n");
        }

        sb.append("\n请根据审核结果继续完成原始任务。");
        return sb.toString();
    }

    // ==================== SEQUENTIAL 工作流恢复 ====================

    /**
     * 恢复 SEQUENTIAL 工作流执行
     *
     * 恢复流程：
     * 1. 从快照获取中间结果和恢复起点
     * 2. 重建工作流配置
     * 3. 调用 SequentialPatternExecutor.resumeFromCheckpoint 继续执行
     *
     * @param review   审核记录
     * @param snapshot 状态快照
     * @return 执行结果
     */
    public Map<String, Object> resumeSequentialFromReview(PendingReview review, SequentialStateSnapshot snapshot) {
        log.info("[DynamicReactAgent] 恢复 SEQUENTIAL 执行，会话: {}, Agent: {}",
                snapshot.getConversationCode(), snapshot.getAgentConfigCode());

        try {
            // 1. 获取 Agent 配置
            String agentConfigCode = snapshot.getAgentConfigCode();
            ReactAgent agentConfig = reactAgentService.getBySlug(agentConfigCode);
            if (agentConfig == null) {
                throw new IllegalArgumentException("Agent 不存在: " + agentConfigCode);
            }

            // 2. 验证是 SEQUENTIAL 类型
            if (!ReactAgent.AgentType.SEQUENTIAL.name().equals(agentConfig.getAgentType())) {
                throw new IllegalArgumentException("Agent 类型不匹配，期望 SEQUENTIAL，实际: " + agentConfig.getAgentType());
            }

            // 3. 创建 ChatModel
            Long modelId = snapshot.getModelId() != null ? snapshot.getModelId() : agentConfig.getModelId();
            if (modelId == null) {
                throw new IllegalArgumentException("未配置模型 ID");
            }
            OpenAiChatModel chatModel = chatModelProvider.getChatModelByModelId(modelId);

            // 4. 重建工作流
            ConfigurableAgentWorkflow workflow = reactAgentFactory.buildSequentialWorkflowFromConfig(agentConfig, chatModel);

            // 5. 调用 SequentialPatternExecutor.resumeFromCheckpoint
            SequentialPatternExecutor executor = new SequentialPatternExecutor();
            WorkflowResult workflowResult = executor.resumeFromCheckpoint(review, snapshot, workflow.getConfig());

            // 6. 转换结果
            Map<String, Object> result = new HashMap<>();
            result.put("success", workflowResult.isSuccess());
            result.put("finalResult", workflowResult.getFinalResult());
            result.put("totalExecutionTimeMs", workflowResult.getTotalExecutionTimeMs());
            result.put("pattern", workflowResult.getPattern());
            result.put("slug", agentConfigCode);
            result.put("agentConfigName", agentConfig.getName());
            result.put("conversationCode", snapshot.getConversationCode());

            if (!workflowResult.isSuccess()) {
                result.put("error", workflowResult.getErrorMessage());
            }

            return result;

        } catch (Exception e) {
            log.error("[DynamicReactAgent] 恢复 SEQUENTIAL 执行失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "恢复 SEQUENTIAL 执行失败: " + e.getMessage());
            return errorResponse;
        }
    }

    // ==================== SUPERVISOR 团队恢复 ====================

    /**
     * 恢复 SUPERVISOR 团队执行
     *
     * 恢复策略：
     * - FINAL_REVIEW 模式：直接返回已批准的最终结果
     * - WORKER_REVIEW 模式：重建团队，将审核结果作为继续执行的输入
     *
     * @param review   审核记录
     * @param snapshot 状态快照
     * @return 执行结果
     */
    public Map<String, Object> resumeSupervisorFromReview(PendingReview review, SupervisorStateSnapshot snapshot) {
        log.info("[DynamicReactAgent] 恢复 SUPERVISOR 执行，会话: {}, Agent: {}, 模式: {}",
                snapshot.getConversationCode(), snapshot.getAgentConfigCode(), snapshot.getReviewMode());

        try {
            // 1. 验证审核状态
            if (!review.isApproved()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("error", "审核未通过，无法恢复执行");
                return result;
            }

            // 2. 根据审核模式处理
            if (snapshot.isFinalReview()) {
                // FINAL_REVIEW 模式：直接返回已批准的最终结果
                return resumeSupervisorFinalReview(review, snapshot);
            } else {
                // WORKER_REVIEW 模式：继续执行
                return resumeSupervisorWorkerReview(review, snapshot);
            }

        } catch (Exception e) {
            log.error("[DynamicReactAgent] 恢复 SUPERVISOR 执行失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "恢复 SUPERVISOR 执行失败: " + e.getMessage());
            return errorResponse;
        }
    }

    /**
     * FINAL_REVIEW 模式恢复：直接返回已批准的最终结果
     */
    private Map<String, Object> resumeSupervisorFinalReview(PendingReview review, SupervisorStateSnapshot snapshot) {
        log.info("[DynamicReactAgent] FINAL_REVIEW 模式，返回已批准的最终结果");

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);

        // 使用快照中保存的最终结果
        String finalResult = snapshot.getFinalResult();
        if (finalResult == null || finalResult.isEmpty()) {
            // 如果没有保存最终结果，使用审核意见作为结果
            finalResult = review.getReviewComment() != null
                    ? "审核通过: " + review.getReviewComment()
                    : "审核通过";
        }

        result.put("result", finalResult);
        result.put("reviewMode", "FINAL_REVIEW");
        result.put("slug", snapshot.getAgentConfigCode());
        result.put("conversationCode", snapshot.getConversationCode());

        return result;
    }

    /**
     * WORKER_REVIEW 模式恢复：重建团队并继续执行
     */
    private Map<String, Object> resumeSupervisorWorkerReview(PendingReview review, SupervisorStateSnapshot snapshot) {
        log.info("[DynamicReactAgent] WORKER_REVIEW 模式，重建团队继续执行");

        // 1. 获取 Agent 配置
        String agentConfigCode = snapshot.getAgentConfigCode();
        ReactAgent agentConfig = reactAgentService.getBySlug(agentConfigCode);
        if (agentConfig == null) {
            throw new IllegalArgumentException("Agent 不存在: " + agentConfigCode);
        }

        // 2. 验证是 SUPERVISOR 类型
        if (!ReactAgent.AgentType.SUPERVISOR.name().equals(agentConfig.getAgentType())) {
            throw new IllegalArgumentException("Agent 类型不匹配，期望 SUPERVISOR，实际: " + agentConfig.getAgentType());
        }

        // 3. 创建 ChatModel
        Long modelId = snapshot.getModelId() != null ? snapshot.getModelId() : agentConfig.getModelId();
        if (modelId == null) {
            throw new IllegalArgumentException("未配置模型 ID");
        }
        OpenAiChatModel chatModel = chatModelProvider.getChatModelByModelId(modelId);

        // 4. 重建 Supervisor 团队
        SupervisorAgentTeam team = reactAgentFactory.buildSupervisorTeamFromConfig(agentConfig, chatModel);

        // 5. 构建恢复消息
        // 包含：原始请求摘要 + 执行历史 + 审核结果
        String resumeMessage = buildSupervisorResumeMessage(review, snapshot);

        // 6. 设置人工审核上下文（供可能的后续审核使用）
        String conversationCode = snapshot.getConversationCode() != null
                ? snapshot.getConversationCode()
                : UUID.randomUUID().toString().replace("-", "");

        HumanReviewContext reviewContext = HumanReviewContext.builder()
                .conversationCode(conversationCode)
                .agentConfigCode(agentConfigCode)
                .agentName(agentConfig.getName())
                .agentType(agentConfig.getAgentType())
                .build();
        HumanReviewContextHolder.setContext(reviewContext);

        try {
            // 7. 执行 Supervisor 团队
            String teamResult = team.execute(resumeMessage);

            // 8. 构建返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("result", teamResult);
            result.put("reviewMode", "WORKER_REVIEW");
            result.put("slug", agentConfigCode);
            result.put("agentConfigName", agentConfig.getName());
            result.put("conversationCode", conversationCode);

            return result;

        } catch (HumanReviewRequiredException e) {
            // 又触发了新的人工审核
            log.info("[DynamicReactAgent] Supervisor 恢复执行中又触发人工审核，reviewCode: {}", e.getReviewCode());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("pendingReview", true);
            response.put("reviewCode", e.getReviewCode());
            response.put("reviewPrompt", e.getReviewPrompt());
            response.put("reviewType", e.getReviewType());
            response.put("slug", agentConfigCode);
            response.put("conversationCode", conversationCode);
            return response;
        } finally {
            HumanReviewContextHolder.clear();
        }
    }

    /**
     * 构建 Supervisor 恢复消息
     */
    private String buildSupervisorResumeMessage(PendingReview review, SupervisorStateSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();

        sb.append("[系统消息] 人工审核已完成，请继续执行任务。\n\n");

        // 添加审核结果
        sb.append("【审核结果】\n");
        sb.append("状态: ").append(review.isApproved() ? "通过" : "拒绝").append("\n");
        if (review.getReviewComment() != null && !review.getReviewComment().isEmpty()) {
            sb.append("审核意见: ").append(review.getReviewComment()).append("\n");
        }
        sb.append("\n");

        // 添加原始消息（如果有）
        if (snapshot.getOriginalMessage() != null && !snapshot.getOriginalMessage().isEmpty()) {
            sb.append("【原始任务】\n");
            sb.append(snapshot.getOriginalMessage()).append("\n\n");
        }

        // 添加执行历史摘要（如果有）
        String executionSummary = snapshot.getExecutionSummary();
        if (executionSummary != null && !"无执行历史".equals(executionSummary)) {
            sb.append("【已完成的步骤】\n");
            sb.append(executionSummary).append("\n\n");
        }

        sb.append("请根据审核结果继续执行任务。");

        return sb.toString();
    }
}

