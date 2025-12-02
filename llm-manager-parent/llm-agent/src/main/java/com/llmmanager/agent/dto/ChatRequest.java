package com.llmmanager.agent.dto;

import lombok.Builder;
import lombok.Data;

/**
 * LLM对话请求封装（增强版）
 * 支持更多参数透传，调用层可根据需要传参
 */
@Data
@Builder
public class ChatRequest {

    // ==================== Channel 配置 ====================

    /**
     * Channel配置：API密钥
     */
    private String apiKey;

    /**
     * Channel配置：基础URL
     */
    private String baseUrl;

    /**
     * Channel ID（用于缓存）
     */
    private Long channelId;

    // ==================== 模型参数 ====================

    /**
     * 模型标识符（如：gpt-4, gpt-3.5-turbo）
     */
    private String modelIdentifier;

    /**
     * 温度参数（0.0-2.0，控制随机性）
     */
    private Double temperature;

    /**
     * Top-P 参数（0.0-1.0，核采样）
     */
    private Double topP;

    /**
     * 最大生成 Token 数
     */
    private Integer maxTokens;

    /**
     * Frequency Penalty（频率惩罚，-2.0-2.0）
     */
    private Double frequencyPenalty;

    /**
     * Presence Penalty（存在惩罚，-2.0-2.0）
     */
    private Double presencePenalty;

    // ==================== 消息内容 ====================

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 用户消息
     */
    private String userMessage;

    // ==================== 历史记忆相关 ====================

    /**
     * 会话ID（启用历史对话时必传）
     */
    private String conversationId;

    /**
     * 是否启用历史记忆（默认 false）
     */
    @Builder.Default
    private Boolean enableMemory = false;

    /**
     * 历史消息数量限制（仅 LOCAL 模式有效）
     */
    private Integer maxHistoryMessages;

    // ==================== 其他参数 ====================

    /**
     * 是否流式输出
     */
    @Builder.Default
    private Boolean stream = false;

    /**
     * 用户标识（用于审计日志）
     */
    private String userId;
}
