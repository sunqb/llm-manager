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

        // 通过反射获取 OpenAiChatModel 内部的 private 字段 openAiApi
        // 原因：需要直接调用 OpenAiApi.chatCompletionEntity() 来发送 HTTP 请求
        // 因为 OpenAiChatModel 的字段是私有的，只能通过反射访问
        this.openAiApi = extractOpenAiApi(delegate);

        // 通过反射获取 OpenAiChatModel 内部的 private 方法 createRequest(Prompt, boolean)
        // 原因：需要复用 OpenAiChatModel 的请求构建逻辑（将 Prompt 转换为 ChatCompletionRequest）
        // 因为 createRequest 是私有方法，只能通过反射调用
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
            // logFinalRequest(modifiedRequest);

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

            // logFinalRequest(modifiedRequest);

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
     * 通过反射调用 createRequest 方法
     *
     * 反射调用步骤：
     * 1. 使用之前获取并保存的 Method 对象（createRequestMethod）
     * 2. 调用 method.invoke(delegate, prompt, stream)
     *    - 第一个参数：要在哪个对象上调用方法（delegate 是 OpenAiChatModel 实例）
     *    - 后续参数：传递给方法的实参（prompt, stream）
     * 3. 返回值是 Object 类型，需要强制转换为 ChatCompletionRequest
     *
     * 等价于：
     * <pre>
     * ChatCompletionRequest request = delegate.createRequest(prompt, stream);
     * </pre>
     * 但由于 createRequest 是私有方法，必须通过反射调用
     */
    private ChatCompletionRequest invokeCreateRequest(Prompt prompt, boolean stream) throws Exception {
        if (createRequestMethod == null) {
            throw new IllegalStateException("createRequest 方法不可用");
        }
        // 通过反射调用：delegate.createRequest(prompt, stream)
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
     * 通过反射提取 OpenAiApi 实例
     *
     * OpenAiChatModel 内部结构：
     * <pre>
     * public class OpenAiChatModel implements ChatModel {
     *     private final OpenAiApi openAiApi;  // ← 私有字段，需要通过反射访问
     *     ...
     * }
     * </pre>
     *
     * 反射步骤：
     * 1. 通过 Class.getDeclaredField("openAiApi") 获取私有字段
     * 2. 调用 field.setAccessible(true) 打破私有访问限制
     * 3. 调用 field.get(model) 从 model 实例中读取字段值
     * 4. 强制类型转换为 OpenAiApi
     */
    private OpenAiApi extractOpenAiApi(OpenAiChatModel model) {
        try {
            // 步骤1：获取 OpenAiChatModel 类的私有字段 "openAiApi"
            Field field = OpenAiChatModel.class.getDeclaredField("openAiApi");

            // 步骤2：打破 Java 访问控制，允许访问私有字段
            field.setAccessible(true);

            // 步骤3：从 model 实例中读取该字段的值，并强制转换为 OpenAiApi 类型
            return (OpenAiApi) field.get(model);
        } catch (Exception e) {
            log.error("[ThinkingChatModel] 无法提取 OpenAiApi: {}", e.getMessage());
            throw new IllegalStateException("无法提取 OpenAiApi", e);
        }
    }

    /**
     * 获取 createRequest 方法（通过反射）
     *
     * OpenAiChatModel 内部结构：
     * <pre>
     * public class OpenAiChatModel implements ChatModel {
     *     // 私有方法，负责将 Prompt 转换为 ChatCompletionRequest
     *     private ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {
     *         // 复杂的转换逻辑...
     *     }
     * }
     * </pre>
     *
     * 反射步骤：
     * 1. 通过 Class.getDeclaredMethod("createRequest", Prompt.class, boolean.class) 获取私有方法
     *    - 第一个参数：方法名 "createRequest"
     *    - 后续参数：方法的参数类型列表 (Prompt.class, boolean.class)
     * 2. 调用 method.setAccessible(true) 打破私有访问限制
     * 3. 后续可通过 method.invoke(delegate, prompt, stream) 调用该方法
     *
     * 为什么需要反射调用：
     * - createRequest 是 OpenAiChatModel 的核心转换逻辑，包含了复杂的 options 合并、消息转换等
     * - 我们需要复用这个逻辑，但该方法是私有的，无法直接调用
     * - 通过反射调用后，可以获取标准的 ChatCompletionRequest 对象
     * - 然后手动向 ChatCompletionRequest.extraBody 注入 thinking 参数
     */
    private Method getCreateRequestMethod() {
        try {
            // 步骤1：获取 OpenAiChatModel 类的私有方法 "createRequest"
            // 参数签名：createRequest(Prompt prompt, boolean stream)
            Method method = OpenAiChatModel.class.getDeclaredMethod("createRequest", Prompt.class, boolean.class);

            // 步骤2：打破 Java 访问控制，允许调用私有方法
            method.setAccessible(true);

            return method;
        } catch (Exception e) {
            log.error("[ThinkingChatModel] 无法获取 createRequest 方法: {}", e.getMessage());
            throw new IllegalStateException("无法获取 createRequest 方法", e);
        }
    }
}
