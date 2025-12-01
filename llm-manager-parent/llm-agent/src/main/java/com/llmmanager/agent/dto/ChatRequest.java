package com.llmmanager.agent.dto;

import lombok.Builder;
import lombok.Data;

/**
 * LLM对话请求封装
 */
@Data
@Builder
public class ChatRequest {

    /**
     * Channel配置：API密钥
     */
    private String apiKey;

    /**
     * Channel配置：基础URL
     */
    private String baseUrl;

    /**
     * 模型标识符（如：gpt-4, gpt-3.5-turbo）
     */
    private String modelIdentifier;

    /**
     * 温度参数（0.0-2.0）
     */
    private Double temperature;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 用户消息
     */
    private String userMessage;

    /**
     * Channel ID（用于缓存）
     */
    private Long channelId;
}
