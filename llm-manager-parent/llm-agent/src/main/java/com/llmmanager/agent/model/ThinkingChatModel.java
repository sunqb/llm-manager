package com.llmmanager.agent.model;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import reactor.core.publisher.Flux;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * 支持 Thinking 参数的 OpenAiChatModel 包装器
 *
 * 问题背景：
 * Spring AI 的 ModelOptionsUtils.merge() 会丢弃 OpenAiChatOptions.extraBody，
 * 因为 ChatCompletionRequest.extraBody 字段没有 @JsonProperty 注解。
 *
 * 解决方案：
 * 1. ThinkingAdvisor 将 thinking 参数放入 OpenAiChatOptions.extraBody
 * 2. 本类通过反射调用 createRequest() 后，手动注入 thinking 到 ChatCompletionRequest.extraBody
 * 3. 直接调用 OpenAiApi 发送请求
 * 4. ChatCompletionRequest.extraBody() getter 方法有 @JsonAnyGetter 注解
 * 5. Jackson 序列化时自动将 extraBody 打平到 JSON 根层级
 *
 * 最终效果：
 * {
 *   "model": "doubao-xxx",
 *   "thinking": {"type": "enabled"}  // ← 正确出现在根层级
 * }
 */
@Slf4j
public class ThinkingChatModel implements ChatModel {

    private final OpenAiChatModel delegate;
    private final OpenAiApi openAiApi;
    private final Method createRequestMethod;

    public ThinkingChatModel(OpenAiChatModel delegate) {
        this.delegate = delegate;
        this.openAiApi = extractOpenAiApi(delegate);
        this.createRequestMethod = getCreateRequestMethod();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        // 检查是否需要注入 thinking
        Map<String, Object> thinkingParams = extractThinkingParams(prompt);
        if (thinkingParams == null || thinkingParams.isEmpty()) {
            // 没有 thinking 参数，直接委托
            return delegate.call(prompt);
        }

        // 有 thinking 参数，需要手动处理
        try {
            // 调用 createRequest（通过反射）
            ChatCompletionRequest request = invokeCreateRequest(prompt, false);

            // 注入 thinking 参数到 extraBody。
            ChatCompletionRequest modifiedRequest = injectThinkingParams(request, thinkingParams);

            // 打印最终请求
            logFinalRequest(modifiedRequest);

            // 直接调用 OpenAiApi
            var response = openAiApi.chatCompletionEntity(modifiedRequest);

            // 转换为 ChatResponse（简化处理，实际可能需要更完整的转换）
            return convertToChatResponse(response.getBody());
        } catch (Exception e) {
            log.error("[ThinkingChatModel] 处理 thinking 参数失败，回退到默认实现", e);
            return delegate.call(prompt);
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // 检查是否需要注入 thinking
        Map<String, Object> thinkingParams = extractThinkingParams(prompt);
        if (thinkingParams == null || thinkingParams.isEmpty()) {
            return delegate.stream(prompt);
        }

        // 有 thinking 参数，需要手动处理
        try {
            ChatCompletionRequest request = invokeCreateRequest(prompt, true);
            ChatCompletionRequest modifiedRequest = injectThinkingParams(request, thinkingParams);

            logFinalRequest(modifiedRequest);

            // 调用流式 API
            return openAiApi.chatCompletionStream(modifiedRequest)
                    .map(this::convertChunkToChatResponse);
        } catch (Exception e) {
            log.error("[ThinkingChatModel] 处理 thinking 参数失败，回退到默认实现", e);
            return delegate.stream(prompt);
        }
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return delegate.getDefaultOptions();
    }

    // ==================== 私有方法 ====================

    /**
     * 提取 thinking 相关参数
     */
    private Map<String, Object> extractThinkingParams(Prompt prompt) {
        if (prompt == null || prompt.getOptions() == null) {
            return null;
        }

        ChatOptions options = prompt.getOptions();
        if (!(options instanceof OpenAiChatOptions openAiOptions)) {
            return null;
        }

        Map<String, Object> extraBody = openAiOptions.getExtraBody();
        if (extraBody == null || extraBody.isEmpty()) {
            return null;
        }

        // 只返回 thinking 相关的参数
        if (extraBody.containsKey("thinking") || extraBody.containsKey("reasoning_effort")) {
            log.info("[ThinkingChatModel] 检测到 thinking 参数: {}", extraBody);
            return extraBody;
        }

        return null;
    }

    /**
     * 通过反射调用 createRequest
     */
    private ChatCompletionRequest invokeCreateRequest(Prompt prompt, boolean stream) throws Exception {
        if (createRequestMethod == null) {
            throw new IllegalStateException("createRequest 方法不可用");
        }
        return (ChatCompletionRequest) createRequestMethod.invoke(delegate, prompt, stream);
    }

    /**
     * 注入 thinking 参数到 ChatCompletionRequest.extraBody
     */
    private ChatCompletionRequest injectThinkingParams(ChatCompletionRequest request, Map<String, Object> thinkingParams) {
        // ChatCompletionRequest 是 record，extraBody 在构造时可以是 mutable HashMap
        // 直接往 extraBody 中添加参数
        Map<String, Object> extraBody = request.extraBody();
        if (extraBody != null) {
            extraBody.putAll(thinkingParams);
            log.info("[ThinkingChatModel] 已注入 thinking 参数到 extraBody: {}", extraBody);
        }
        return request;
    }

    /**
     * 打印最终请求（调试用）
     */
    private void logFinalRequest(ChatCompletionRequest request) {
        if (log.isInfoEnabled()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
                String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);
                log.info("[ThinkingChatModel] 最终 HTTP 请求体:\n{}", json);
            } catch (Exception e) {
                log.warn("[ThinkingChatModel] 序列化请求失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 转换 ChatCompletion 为 ChatResponse
     */
    private ChatResponse convertToChatResponse(OpenAiApi.ChatCompletion completion) {
        if (completion == null || completion.choices() == null || completion.choices().isEmpty()) {
            return new ChatResponse(java.util.List.of());
        }

        var choice = completion.choices().get(0);
        var message = choice.message();

        // 使用 Builder 模式创建 AssistantMessage
        org.springframework.ai.chat.messages.AssistantMessage assistantMessage =
                org.springframework.ai.chat.messages.AssistantMessage.builder()
                        .content(message.content())
                        .properties(java.util.Map.of(
                                "reasoningContent", message.reasoningContent() != null ? message.reasoningContent() : ""
                        ))
                        .build();

        org.springframework.ai.chat.model.Generation generation =
                new org.springframework.ai.chat.model.Generation(assistantMessage);

        return new ChatResponse(java.util.List.of(generation));
    }

    /**
     * 转换 ChatCompletionChunk 为 ChatResponse
     */
    private ChatResponse convertChunkToChatResponse(OpenAiApi.ChatCompletionChunk chunk) {
        if (chunk == null || chunk.choices() == null || chunk.choices().isEmpty()) {
            return new ChatResponse(java.util.List.of());
        }

        var choice = chunk.choices().get(0);
        var delta = choice.delta();

        String content = delta.content() != null ? delta.content() : "";
        String reasoningContent = delta.reasoningContent() != null ? delta.reasoningContent() : "";

        org.springframework.ai.chat.messages.AssistantMessage assistantMessage =
                org.springframework.ai.chat.messages.AssistantMessage.builder()
                        .content(content)
                        .properties(java.util.Map.of("reasoningContent", reasoningContent))
                        .build();

        org.springframework.ai.chat.model.Generation generation =
                new org.springframework.ai.chat.model.Generation(assistantMessage);

        return new ChatResponse(java.util.List.of(generation));
    }

    /**
     * 通过反射提取 OpenAiApi
     */
    private OpenAiApi extractOpenAiApi(OpenAiChatModel model) {
        try {
            Field field = OpenAiChatModel.class.getDeclaredField("openAiApi");
            field.setAccessible(true);
            return (OpenAiApi) field.get(model);
        } catch (Exception e) {
            log.error("[ThinkingChatModel] 无法提取 OpenAiApi: {}", e.getMessage());
            throw new IllegalStateException("无法提取 OpenAiApi", e);
        }
    }

    /**
     * 获取 createRequest 方法（通过反射）
     */
    private Method getCreateRequestMethod() {
        try {
            Method method = OpenAiChatModel.class.getDeclaredMethod("createRequest", Prompt.class, boolean.class);
            method.setAccessible(true);
            return method;
        } catch (Exception e) {
            log.error("[ThinkingChatModel] 无法获取 createRequest 方法: {}", e.getMessage());
            throw new IllegalStateException("无法获取 createRequest 方法", e);
        }
    }
}
