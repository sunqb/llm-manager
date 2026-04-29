package com.llmmanager.agent.tools;

import com.llmmanager.agent.review.HumanReviewRecordService;
import com.llmmanager.agent.review.context.HumanReviewContext;
import com.llmmanager.agent.review.context.HumanReviewContextHolder;
import com.llmmanager.agent.review.exception.HumanReviewRequiredException;
import com.llmmanager.agent.storage.core.entity.PendingReview;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 人工审核工具 - 使用 Spring AI 原生 @Tool 注解
 *
 * 功能：让 LLM Agent 能够主动请求人工审核
 *
 * 使用场景：
 * 1. SINGLE Agent：Agent 在执行过程中决定某些内容需要人工确认
 * 2. SUPERVISOR Agent：作为一个 Worker，Supervisor 可以调用它请求人工审核
 *
 * 工作流程：
 * 1. LLM 分析用户请求，决定是否需要人工审核
 * 2. 调用 requestHumanReview 工具
 * 3. 工具创建审核记录
 * 4. 抛出 HumanReviewRequiredException 暂停 Agent 执行
 * 5. 审核完成后，HumanReviewService 恢复 Agent 对话
 *
 * 前置条件：
 * - 调用方必须使用 HumanReviewContextHolder 设置上下文
 * - 上下文中必须包含 conversationCode
 *
 * @author LLM Manager
 */
@Slf4j
@Component
public class HumanReviewTool {

    @Resource
    private HumanReviewRecordService humanReviewRecordService;

    /**
     * 请求人工审核
     *
     * 当 Agent 需要人工确认某些内容时，调用此工具。
     * 工具会创建审核记录并暂停 Agent 执行，等待人工审核完成后继续。
     *
     * @param content 需要审核的内容（例如：生成的报告、分析结果、执行计划等）
     * @param prompt  审核提示（向审核人说明需要审核什么，以及审核的标准）
     * @return 永远不会正常返回（会抛出异常暂停执行）
     * @throws HumanReviewRequiredException 暂停 Agent 执行，等待人工审核
     */
    @Tool(description = "请求人工审核 - 当你需要人工确认或批准某些内容时使用此工具。" +
            "例如：生成的重要报告需要确认、敏感操作需要批准、复杂分析结果需要验证等。" +
            "调用此工具后，执行将暂停，等待人工审核完成后继续。")
    public String requestHumanReview(
            @ToolParam(description = "需要审核的内容，例如生成的报告、分析结果、执行计划等")
            String content,
            @ToolParam(description = "审核提示，向审核人说明需要审核什么以及审核标准")
            String prompt) {

        log.info("[HumanReviewTool] LLM 请求人工审核，内容长度: {}", content != null ? content.length() : 0);

        // 1. 获取上下文
        HumanReviewContext context = HumanReviewContextHolder.getContext();

        if (context == null) {
            log.error("[HumanReviewTool] 上下文不存在，无法创建审核记录");
            return "错误：无法创建审核记录，上下文信息缺失。请确保在支持审核的环境中使用此工具。";
        }

        String conversationCode = context.getConversationCode();
        if (conversationCode == null || conversationCode.trim().isEmpty()) {
            log.error("[HumanReviewTool] 会话标识不存在");
            return "错误：无法创建审核记录，会话标识缺失。";
        }

        try {
            // 2. 构建审核提示（结合提示和内容）
            String reviewerPrompt = formatReviewerPrompt(prompt, content, context);

            // 3. 构建上下文数据
            Map<String, Object> contextData = buildContextData(content, context);

            // 4. 创建审核记录
            PendingReview review = humanReviewRecordService.createReactAgentToolReview(
                    conversationCode,
                    context.getAgentConfigCode(),
                    reviewerPrompt,
                    contextData
            );

            log.info("[HumanReviewTool] 审核记录创建成功，reviewCode: {}", review.getReviewCode());

            // 5. 抛出异常暂停执行
            throw new HumanReviewRequiredException(
                    review.getReviewCode(),
                    reviewerPrompt,
                    PendingReview.ReviewType.REACT_AGENT_TOOL.name()
            );

        } catch (HumanReviewRequiredException e) {
            // 重新抛出审核异常（不要吞掉）
            throw e;
        } catch (Exception e) {
            log.error("[HumanReviewTool] 创建审核记录失败", e);
            return "错误：创建审核记录失败 - " + e.getMessage();
        }
    }

    /**
     * 请求人工审核（简化版）
     *
     * 仅传入需要审核的内容，使用默认的审核提示
     *
     * @param content 需要审核的内容
     * @return 永远不会正常返回（会抛出异常暂停执行）
     * @throws HumanReviewRequiredException 暂停 Agent 执行，等待人工审核
     */
    @Tool(description = "请求人工审核（简化版）- 当你需要人工确认某些内容时使用此工具。" +
            "传入需要审核的内容，系统将使用默认的审核提示。")
    public String submitForReview(
            @ToolParam(description = "需要审核的内容")
            String content) {

        return requestHumanReview(content, "请审核以下内容是否正确、完整，并决定是否批准。");
    }

    /**
     * 格式化审核提示
     */
    private String formatReviewerPrompt(String prompt, String content, HumanReviewContext context) {
        StringBuilder sb = new StringBuilder();

        // 添加 Agent 信息
        if (context.getAgentName() != null) {
            sb.append("【Agent】").append(context.getAgentName());
            if (context.getAgentType() != null) {
                sb.append(" (").append(context.getAgentType()).append(")");
            }
            sb.append("\n\n");
        }

        // 添加审核提示
        sb.append("【审核说明】\n");
        sb.append(prompt).append("\n\n");

        // 添加审核内容
        sb.append("【待审核内容】\n");
        sb.append(content);

        return sb.toString();
    }

    /**
     * 构建上下文数据
     */
    private Map<String, Object> buildContextData(String content, HumanReviewContext context) {
        Map<String, Object> data = new HashMap<>();

        data.put("content", content);
        data.put("agentName", context.getAgentName());
        data.put("agentType", context.getAgentType());
        data.put("agentConfigCode", context.getAgentConfigCode());
        data.put("conversationCode", context.getConversationCode());
        if (context.getOriginalTask() != null) {
            data.put("originalTask", context.getOriginalTask());
        }
        if (context.getModelId() != null) {
            data.put("modelId", context.getModelId());
        }

        return data;
    }
}
