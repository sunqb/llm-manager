package com.llmmanager.agent.dto;

import com.llmmanager.agent.message.MediaMessage;
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

    /**
     * 媒体内容列表（图片、文件等）
     * 支持多模态对话（如 GPT-4V 图片理解）
     */
    private java.util.List<MediaMessage.MediaContent> mediaContents;

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

    // ==================== 工具调用相关 ====================

    /**
     * 是否启用工具调用（Function Calling）
     */
    @Builder.Default
    private Boolean enableTools = false;

    /**
     * 指定可用的工具名称列表（null 表示使用所有已注册的工具）
     */
    private java.util.List<String> toolNames;

    /**
     * 工具调用模式（auto: 自动决定，none: 不使用工具，required: 必须使用工具）
     */
    @Builder.Default
    private String toolChoice = "auto";

    /**
     * 最大工具调用轮次（防止无限循环）
     */
    @Builder.Default
    private Integer maxToolRounds = 5;

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

    // ==================== 辅助方法 ====================

    /**
     * 是否包含媒体内容（图片、文件等）
     */
    public boolean hasMedia() {
        return mediaContents != null && !mediaContents.isEmpty();
    }
}
