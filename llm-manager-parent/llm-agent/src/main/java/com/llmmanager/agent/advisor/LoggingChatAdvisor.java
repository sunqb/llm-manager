package com.llmmanager.agent.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

/**
 * 对话日志记录 Advisor
 *
 * 职责：
 * - 记录所有对话的请求和响应
 * - 记录 Token 使用情况
 * - 记录响应耗时
 * - 用于审计、监控和调试
 *
 * 注意：默认不自动注册，避免影响性能。如需使用，手动注册：
 * advisorManager.registerAdvisor(loggingChatAdvisor);
 */
@Component
public class LoggingChatAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(LoggingChatAdvisor.class);

    private static final String REQUEST_START_TIME = "request_start_time";

    // 移除自动注册，避免影响所有请求的性能

    @Override
    public String getName() {
        return "LoggingChatAdvisor";
    }

    @Override
    public int getOrder() {
        return 0; // 最高优先级，最先执行
    }

    /**
     * 请求前处理：记录请求信息
     */
    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        // 记录开始时间
        request.context().put(REQUEST_START_TIME, System.currentTimeMillis());

        // 提取会话ID
        String conversationId = extractConversationId(request);

        // 提取用户消息
        String userMessage = extractUserMessage(request);

        // 记录请求
        log.info("=== 对话请求 ===");
        log.info("会话ID: {}", conversationId);
        log.info("用户消息: {}", truncate(userMessage, 200));

        return request;
    }

    /**
     * 响应后处理：记录响应信息和耗时
     */
    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        // 计算耗时
        Long startTime = (Long) response.context().get(REQUEST_START_TIME);
        long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;

        // 提取会话ID
        String conversationId = extractConversationId(response.context());

        // 记录响应
        if (response.chatResponse() != null && response.chatResponse().getResult() != null) {
            String assistantMessage = response.chatResponse().getResult().getOutput().getText();

            log.info("=== 对话响应 ===");
            log.info("会话ID: {}", conversationId);
            log.info("助手回复: {}", truncate(assistantMessage, 200));
            log.info("耗时: {} ms", duration);

            // 记录 Token 使用
            if (response.chatResponse().getMetadata() != null
                && response.chatResponse().getMetadata().getUsage() != null) {
                var usage = response.chatResponse().getMetadata().getUsage();
                log.info("Token 使用 - 输入: {}, 输出: {}, 总计: {}",
                        usage.getPromptTokens(),
                        usage.getCompletionTokens(),
                        usage.getTotalTokens());
            }
        } else {
            log.warn("响应为空");
        }

        return response;
    }

    /**
     * 从请求中提取会话ID
     */
    private String extractConversationId(ChatClientRequest request) {
        if (request.context() != null) {
            Object convId = request.context().get(ChatMemory.CONVERSATION_ID);
            if (convId != null) {
                return convId.toString();
            }
        }
        return "unknown";
    }

    /**
     * 从上下文中提取会话ID
     */
    private String extractConversationId(java.util.Map<String, Object> context) {
        if (context != null) {
            Object convId = context.get(ChatMemory.CONVERSATION_ID);
            if (convId != null) {
                return convId.toString();
            }
        }
        return "unknown";
    }

    /**
     * 提取用户消息
     */
    private String extractUserMessage(ChatClientRequest request) {
        // 尝试从 Prompt 中获取用户消息
        if (request.prompt() != null && request.prompt().getUserMessage() != null) {
            return request.prompt().getUserMessage().getText();
        }

        // 尝试从所有消息中获取最后一条用户消息
        if (request.prompt() != null && request.prompt().getUserMessages() != null
            && !request.prompt().getUserMessages().isEmpty()) {
            var userMessages = request.prompt().getUserMessages();
            return userMessages.get(userMessages.size() - 1).getText();
        }

        // 尝试从所有指令中获取
        if (request.prompt() != null && request.prompt().getInstructions() != null
            && !request.prompt().getInstructions().isEmpty()) {
            var instructions = request.prompt().getInstructions();
            for (int i = instructions.size() - 1; i >= 0; i--) {
                var message = instructions.get(i);
                if (message.getMessageType() == org.springframework.ai.chat.messages.MessageType.USER) {
                    return message.getText();
                }
            }
        }

        return "[无消息内容]";
    }

    /**
     * 截断文本（避免日志过长）
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "... (共 " + text.length() + " 字符)";
    }
}
