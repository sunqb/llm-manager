package com.llmmanager.agent.model;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 支持 Thinking 参数的 OpenAiApi
 *
 * 解决 Spring AI 的 bug：extraBody 参数在 merge 过程中丢失。
 * 方案：从 metadata 读取参数，展开到 extraBody 中。
 *
 * 参考：https://github.com/spring-projects/spring-ai/issues/4879
 */
@Slf4j
public class ThinkingAwareOpenAiApi extends OpenAiApi {

    public ThinkingAwareOpenAiApi(String baseUrl, String apiKey) {
        super(
                baseUrl,
                new SimpleApiKey(apiKey),
                new LinkedMultiValueMap<>(),
                "/v1/chat/completions",
                "/v1/embeddings",
                RestClient.builder(),
                WebClient.builder(),
                RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER
        );
    }

    @Override
    public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest chatRequest) {
        expandMetadataToExtraBody(chatRequest);
        return super.chatCompletionEntity(chatRequest);
    }

    @Override
    public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest chatRequest,
                                                                MultiValueMap<String, String> additionalHttpHeader) {
        expandMetadataToExtraBody(chatRequest);
        return super.chatCompletionEntity(chatRequest, additionalHttpHeader);
    }

    @Override
    public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest chatRequest) {
        expandMetadataToExtraBody(chatRequest);
        return super.chatCompletionStream(chatRequest);
    }

    @Override
    public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest chatRequest,
                                                           MultiValueMap<String, String> additionalHttpHeader) {
        expandMetadataToExtraBody(chatRequest);
        return super.chatCompletionStream(chatRequest, additionalHttpHeader);
    }

    /**
     * 将 metadata 中的参数展开到 extraBody
     *
     * 解决 Spring AI 的 bug：OpenAiChatOptions.extraBody 在 merge 过程中丢失。
     * 通过 metadata 传递参数，然后在这里展开到 extraBody。
     */
    private void expandMetadataToExtraBody(ChatCompletionRequest chatRequest) {
        Map<String, Object> extraBody = chatRequest.extraBody();
        Map<String, String> metadata = chatRequest.metadata();

        if (CollectionUtils.isEmpty(metadata)) {
            return;
        }

        metadata.forEach((key, value) -> {
            if (key.equalsIgnoreCase("thinking_mode")) {
                // 豆包/火山格式: {"thinking": {"type": "enabled/disabled"}}
                Map<String, Object> thinking = new java.util.HashMap<>();
                thinking.put("type", value);
                extraBody.put("thinking", thinking);
                log.debug("[ThinkingAwareOpenAiApi] 设置 thinking: {}", thinking);
            } else if (key.equalsIgnoreCase("reasoning_effort")) {
                // OpenAI 格式: {"reasoning_effort": "low/medium/high"}
                extraBody.put("reasoning_effort", value);
                log.debug("[ThinkingAwareOpenAiApi] 设置 reasoning_effort: {}", value);
            } else if (key.equalsIgnoreCase("enable_thinking")) {
                // 兼容其他格式
                boolean enabled = "1".equals(value) || "true".equalsIgnoreCase(value) || "enabled".equalsIgnoreCase(value);
                extraBody.put("enable_thinking", enabled);
                log.debug("[ThinkingAwareOpenAiApi] 设置 enable_thinking: {}", enabled);
            }
            // 忽略其他 metadata 参数，不展开到 extraBody
        });
    }
}
