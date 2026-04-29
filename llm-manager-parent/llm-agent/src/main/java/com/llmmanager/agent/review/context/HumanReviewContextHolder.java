package com.llmmanager.agent.review.context;

import lombok.extern.slf4j.Slf4j;

/**
 * 人工审核上下文持有器
 *
 * 使用 ThreadLocal 管理 ReactAgent 执行过程中的上下文信息。
 * 这样 HumanReviewTool 可以在任意位置获取当前的会话标识等信息。
 *
 * 使用方式：
 * <pre>
 * // 设置上下文（在 ReactAgent 执行开始时）
 * HumanReviewContextHolder.setContext(HumanReviewContext.builder()
 *     .conversationCode("conv-123")
 *     .agentConfigCode("agent-001")
 *     .agentName("全能助手")
 *     .agentType("SINGLE")
 *     .build());
 *
 * try {
 *     // 执行 Agent...（内部调用的 HumanReviewTool 可以获取上下文）
 * } finally {
 *     // 清除上下文（必须在 finally 中执行，防止内存泄漏）
 *     HumanReviewContextHolder.clear();
 * }
 * </pre>
 *
 * @author LLM Manager
 */
@Slf4j
public class HumanReviewContextHolder {

    private static final ThreadLocal<HumanReviewContext> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 设置上下文
     *
     * @param context 上下文对象
     */
    public static void setContext(HumanReviewContext context) {
        CONTEXT_HOLDER.set(context);
        log.debug("[HumanReviewContextHolder] 设置上下文: {}", context);
    }

    /**
     * 获取上下文
     *
     * @return 上下文对象，如果不存在返回 null
     */
    public static HumanReviewContext getContext() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 获取会话标识
     *
     * @return 会话标识，如果不存在返回 null
     */
    public static String getConversationCode() {
        HumanReviewContext context = CONTEXT_HOLDER.get();
        return context != null ? context.getConversationCode() : null;
    }

    /**
     * 获取 Agent 配置 Code
     *
     * @return Agent 配置 Code，如果不存在返回 null
     */
    public static String getAgentConfigCode() {
        HumanReviewContext context = CONTEXT_HOLDER.get();
        return context != null ? context.getAgentConfigCode() : null;
    }

    /**
     * 获取 Agent 名称
     *
     * @return Agent 名称，如果不存在返回 null
     */
    public static String getAgentName() {
        HumanReviewContext context = CONTEXT_HOLDER.get();
        return context != null ? context.getAgentName() : null;
    }

    /**
     * 获取 Agent 类型
     *
     * @return Agent 类型，如果不存在返回 null
     */
    public static String getAgentType() {
        HumanReviewContext context = CONTEXT_HOLDER.get();
        return context != null ? context.getAgentType() : null;
    }

    /**
     * 清除上下文
     *
     * 重要：必须在 Agent 执行完成后调用，防止内存泄漏
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
        log.debug("[HumanReviewContextHolder] 已清除上下文");
    }

    /**
     * 检查上下文是否存在
     *
     * @return 上下文是否存在
     */
    public static boolean hasContext() {
        return CONTEXT_HOLDER.get() != null;
    }

    /**
     * 检查会话标识是否存在
     *
     * @return 会话标识是否存在
     */
    public static boolean hasConversationCode() {
        HumanReviewContext context = CONTEXT_HOLDER.get();
        return context != null && context.getConversationCode() != null;
    }
}
