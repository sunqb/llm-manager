package com.llmmanager.service.dto;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 流式响应格式化器
 *
 * 职责：将 ChatStreamChunk 转换为 SSE 格式
 *
 * 设计原则：
 * - Service 层返回 ChatStreamChunk（业务数据）
 * - 本类只做协议转换（ChatStreamChunk → SSE JSON）
 * - 格式符合 OpenAI 兼容标准
 */
@Component
public class StreamResponseFormatter {

    /**
     * 格式化 ChatStreamChunk 流为 SSE
     *
     * @param chunkFlux ChatStreamChunk 流
     * @return SSE 事件流
     */
    public Flux<ServerSentEvent<String>> format(Flux<ChatStreamChunk> chunkFlux) {
        return chunkFlux
                .filter(chunk -> chunk != null && (chunk.hasContent() || chunk.isDone()))
                .map(this::toSseEvent);
    }

    /**
     * 将 ChatStreamChunk 转换为 SSE 事件
     */
    private ServerSentEvent<String> toSseEvent(ChatStreamChunk chunk) {
        if (chunk.isDone()) {
            return ServerSentEvent.<String>builder().data("[DONE]").build();
        }
        return ServerSentEvent.<String>builder().data(toJson(chunk)).build();
    }

    /**
     * 将 ChatStreamChunk 转换为 JSON
     *
     * 格式：{"choices":[{"delta":{"content":"...", "reasoning_content":"..."}}]}
     */
    private String toJson(ChatStreamChunk chunk) {
        StringBuilder json = new StringBuilder("{\"choices\":[{\"delta\":{");

        boolean hasReasoning = chunk.getReasoning() != null && !chunk.getReasoning().isEmpty();
        boolean hasContent = chunk.getContent() != null && !chunk.getContent().isEmpty();

        if (hasReasoning) {
            json.append("\"reasoning_content\":\"").append(escapeJson(chunk.getReasoning())).append("\"");
            if (hasContent) {
                json.append(",");
            }
        }

        if (hasContent) {
            json.append("\"content\":\"").append(escapeJson(chunk.getContent())).append("\"");
        }

        json.append("}}]}");
        return json.toString();
    }

    /**
     * JSON 字符串转义
     */
    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

