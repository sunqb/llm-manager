package com.llmmanager.openapi.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * OpenAI 兼容的 Chat Completion 响应
 *
 * @author LLM Manager
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatCompletionResponse {

    /**
     * 响应 ID
     */
    @Builder.Default
    private String id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

    /**
     * 对象类型
     */
    @Builder.Default
    private String object = "chat.completion";

    /**
     * 创建时间（Unix 时间戳）
     */
    @Builder.Default
    private Long created = System.currentTimeMillis() / 1000;

    /**
     * 模型标识
     */
    private String model;

    /**
     * 选择列表
     */
    private List<Choice> choices;

    /**
     * Token 使用统计
     */
    private Usage usage;

    /**
     * 系统指纹（可选）
     */
    @JsonProperty("system_fingerprint")
    private String systemFingerprint;

    /**
     * 选择项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice {
        /**
         * 索引
         */
        @Builder.Default
        private Integer index = 0;

        /**
         * 消息内容
         */
        private Message message;

        /**
         * 流式 delta（流式响应时使用）
         */
        private Delta delta;

        /**
         * 完成原因
         */
        @JsonProperty("finish_reason")
        private String finishReason;

        /**
         * 日志概率（可选）
         */
        private Object logprobs;
    }

    /**
     * 消息（非流式响应）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        /**
         * 角色
         */
        @Builder.Default
        private String role = "assistant";

        /**
         * 内容
         */
        private String content;

        /**
         * 工具调用（可选）
         */
        @JsonProperty("tool_calls")
        private List<Object> toolCalls;
    }

    /**
     * Delta（流式响应）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Delta {
        /**
         * 角色（首次发送）
         */
        private String role;

        /**
         * 内容片段
         */
        private String content;

        /**
         * 推理内容（思考模式）
         */
        @JsonProperty("reasoning_content")
        private String reasoningContent;
    }

    /**
     * Token 使用统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        /**
         * 提示 Token 数
         */
        @JsonProperty("prompt_tokens")
        @Builder.Default
        private Integer promptTokens = 0;

        /**
         * 完成 Token 数
         */
        @JsonProperty("completion_tokens")
        @Builder.Default
        private Integer completionTokens = 0;

        /**
         * 总 Token 数
         */
        @JsonProperty("total_tokens")
        @Builder.Default
        private Integer totalTokens = 0;
    }

    /**
     * 创建成功响应
     */
    public static ChatCompletionResponse success(String model, String content) {
        return ChatCompletionResponse.builder()
                .model(model)
                .choices(List.of(
                        Choice.builder()
                                .index(0)
                                .message(Message.builder().content(content).build())
                                .finishReason("stop")
                                .build()
                ))
                .usage(Usage.builder().build())
                .build();
    }

    /**
     * 创建流式 chunk 响应
     */
    public static ChatCompletionResponse streamChunk(String model, String content, boolean isFirst) {
        Delta delta = Delta.builder()
                .role(isFirst ? "assistant" : null)
                .content(content)
                .build();

        return ChatCompletionResponse.builder()
                .object("chat.completion.chunk")
                .model(model)
                .choices(List.of(
                        Choice.builder()
                                .index(0)
                                .delta(delta)
                                .finishReason(null)
                                .build()
                ))
                .build();
    }

    /**
     * 创建流式结束响应
     */
    public static ChatCompletionResponse streamDone(String model) {
        return ChatCompletionResponse.builder()
                .object("chat.completion.chunk")
                .model(model)
                .choices(List.of(
                        Choice.builder()
                                .index(0)
                                .delta(Delta.builder().build())
                                .finishReason("stop")
                                .build()
                ))
                .build();
    }
}
