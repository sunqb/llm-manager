package com.llmmanager.service.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 流式对话响应块
 *
 * 统一封装 LLM 流式响应的数据结构，支持：
 * - content：回答内容
 * - reasoning：思考过程（DeepSeek R1 等模型）
 * - metadata：扩展元数据
 *
 * 设计原则：
 * - Service 层返回此 DTO，不关心 HTTP 格式
 * - Controller 层负责将 DTO 转换为 SSE/JSON
 */
@Data
@Builder
public class ChatStreamChunk {

    /**
     * 回答内容
     */
    private String content;

    /**
     * 思考过程（reasoning_content）
     * 仅支持思考的模型（如 DeepSeek R1）会返回
     */
    private String reasoning;

    /**
     * 扩展元数据
     */
    private Map<String, Object> metadata;

    /**
     * 是否为最后一个块
     */
    private boolean done;

    /**
     * 创建内容块
     */
    public static ChatStreamChunk ofContent(String content) {
        return ChatStreamChunk.builder()
                .content(content)
                .build();
    }

    /**
     * 创建带 reasoning 的块
     */
    public static ChatStreamChunk of(String content, String reasoning) {
        return ChatStreamChunk.builder()
                .content(content)
                .reasoning(reasoning)
                .build();
    }

    /**
     * 创建结束标记
     */
    public static ChatStreamChunk done() {
        return ChatStreamChunk.builder()
                .done(true)
                .build();
    }

    /**
     * 是否有有效内容
     */
    public boolean hasContent() {
        return (content != null && !content.isEmpty())
                || (reasoning != null && !reasoning.isEmpty());
    }
}
