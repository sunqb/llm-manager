package com.llmmanager.agent.agent;

import com.llmmanager.agent.advisor.AdvisorManager;
import com.llmmanager.agent.config.ToolFunctionManager;
import com.llmmanager.agent.dto.ChatRequest;
import com.llmmanager.agent.message.MediaMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;
import lombok.extern.slf4j.Slf4j;

import com.llmmanager.agent.interceptor.ExtraBodyFlattenInterceptor;

import javax.annotation.Resource;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM对话代理 - 统一版（基于 Spring AI）
 *
 * 核心设计：
 * - 统一使用 Spring AI ChatClient
 * - Agent 层只关心与 LLM 交互，不关心格式处理
 * - 流式接口统一返回 Flux<ChatResponse>，由 Service 层处理格式转换
 * - 支持：同步对话、流式对话、历史对话、工具调用、多模态
 */
@Slf4j
@Component
public class LlmChatAgent {

    @Resource
    private ChatMemory chatMemory;

    @Resource
    private AdvisorManager advisorManager;

    @Autowired(required = false)
    private MessageChatMemoryAdvisor memoryAdvisor;

    @Resource
    private ToolFunctionManager toolFunctionManager;

    // 缓存 ChatModel 实例
    private static final Map<String, ChatModel> chatModelCache = new ConcurrentHashMap<>();

    // ==================== 同步对话 ====================

    /**
     * 执行同步对话
     */
    public String chat(ChatRequest request) {
        return chat(request, null);
    }

    /**
     * 执行同步对话（支持历史）
     */
    public String chat(ChatRequest request, String conversationId) {
        ChatClient chatClient = createChatClient(request, conversationId);
        OpenAiChatOptions options = buildOptions(request);

        var promptBuilder = chatClient.prompt();

        // 系统提示词
        if (StringUtils.hasText(request.getSystemPrompt())) {
            promptBuilder.system(request.getSystemPrompt());
        }

        // 用户消息（支持多模态）
        addUserMessage(promptBuilder, request);

        // 工具调用
        addTools(promptBuilder, request);

        // 执行
        ChatResponse response = promptBuilder
                .options(options)
                .advisors(advisor -> {
                    if (conversationId != null) {
                        advisor.param(ChatMemory.CONVERSATION_ID, conversationId);
                    }
                })
                .call()
                .chatResponse();

        return response.getResult().getOutput().getText();
    }

    // ==================== 流式对话（统一返回 ChatResponse）====================

    /**
     * 执行流式对话
     *
     * Agent 层统一返回 Flux<ChatResponse>，包含完整的 LLM 响应信息：
     * - content: 回答内容
     * - metadata: 包含 reasoning_content 等扩展字段
     *
     * Service 层负责将 ChatResponse 转换为业务 DTO
     */
    public Flux<ChatResponse> stream(ChatRequest request) {
        return stream(request, null);
    }

    /**
     * 执行流式对话（支持历史）
     *
     * 支持所有对话场景：
     * - 基础对话
     * - 工具调用（request.enableTools = true）
     * - 多模态（request.mediaContents）
     * - 思考模式（自动支持，通过 metadata 返回 reasoning_content）
     */
    public Flux<ChatResponse> stream(ChatRequest request, String conversationId) {
        // 打印完整的请求参数
        logFullRequest(request, conversationId);

        ChatClient chatClient = createChatClient(request, conversationId);
        OpenAiChatOptions options = buildOptions(request);

        // 打印完整的 options（包括 extraBody）
        logFullOptions(options);

        var promptBuilder = chatClient.prompt();

        // 系统提示词
        if (StringUtils.hasText(request.getSystemPrompt())) {
            promptBuilder.system(request.getSystemPrompt());
        }

        // 用户消息（支持多模态）
        addUserMessage(promptBuilder, request);

        // 工具调用
        addTools(promptBuilder, request);

        // 执行流式请求
        return promptBuilder
                .options(options)
                .advisors(advisor -> {
                    if (conversationId != null) {
                        advisor.param(ChatMemory.CONVERSATION_ID, conversationId);
                    }
                })
                .stream()
                .chatResponse();
    }

    /**
     * 打印完整的请求参数
     */
    private void logFullRequest(ChatRequest request, String conversationId) {
        log.info("==================== LlmChatAgent 请求参数 ====================");
        log.info("baseUrl: {}", request.getBaseUrl());
        log.info("modelIdentifier: {}", request.getModelIdentifier());
        log.info("temperature: {}", request.getTemperature());
        log.info("thinkingMode: '{}'", request.getThinkingMode());
        log.info("reasoningFormat: {}", request.getReasoningFormat());
        log.info("conversationId: {}", conversationId);
        log.info("userMessage: {}", request.getUserMessage() != null ?
                (request.getUserMessage().length() > 100 ? request.getUserMessage().substring(0, 100) + "..." : request.getUserMessage()) : null);
        log.info("systemPrompt: {}", request.getSystemPrompt() != null ?
                (request.getSystemPrompt().length() > 100 ? request.getSystemPrompt().substring(0, 100) + "..." : request.getSystemPrompt()) : null);
        log.info("enableTools: {}", request.getEnableTools());
        log.info("hasMedia: {}", request.hasMedia());
        log.info("==============================================================");
    }

    /**
     * 打印完整的 OpenAiChatOptions（包括 extraBody）
     */
    private void logFullOptions(OpenAiChatOptions options) {
        log.info("==================== OpenAiChatOptions ====================");
        log.info("model: {}", options.getModel());
        log.info("temperature: {}", options.getTemperature());
        log.info("topP: {}", options.getTopP());
        log.info("maxTokens: {}", options.getMaxTokens());
        log.info("extraBody: {}", options.getExtraBody());

        // 打印完整的 JSON 序列化结果
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
            String optionsJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(options);
            log.info("OpenAiChatOptions 完整 JSON:\n{}", optionsJson);
        } catch (Exception e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
        }
        log.info("============================================================");
    }

    // ==================== 内部工具方法 ====================

    /**
     * 构建 OpenAI 选项
     */
    private OpenAiChatOptions buildOptions(ChatRequest request) {
        log.info("[LlmChatAgent] buildOptions - thinkingMode: '{}', reasoningFormat: {}",
                request.getThinkingMode(), request.getReasoningFormat());

        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(request.getModelIdentifier());

        if (request.getTemperature() != null) {
            builder.temperature(request.getTemperature());
        }
        if (request.getTopP() != null) {
            builder.topP(request.getTopP());
        }
        if (request.getMaxTokens() != null) {
            builder.maxTokens(request.getMaxTokens());
        }
        if (request.getFrequencyPenalty() != null) {
            builder.frequencyPenalty(request.getFrequencyPenalty());
        }
        if (request.getPresencePenalty() != null) {
            builder.presencePenalty(request.getPresencePenalty());
        }

        // 思考模式：根据不同厂商格式构建 extraBody
        Map<String, Object> extraBody = buildReasoningExtraBody(request);
        if (extraBody != null && !extraBody.isEmpty()) {
            builder.extraBody(extraBody);
            log.info("[LlmChatAgent] 已设置 reasoning extraBody: {}", extraBody);
        }

        OpenAiChatOptions options = builder.build();

        // 调试日志：打印完整的 options JSON
        if (log.isDebugEnabled() && options.getExtraBody() != null && !options.getExtraBody().isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String optionsJson = mapper.writeValueAsString(options);
                log.debug("[LlmChatAgent] OpenAiChatOptions JSON: {}",
                        optionsJson.length() > 1000 ? optionsJson.substring(0, 1000) + "..." : optionsJson);
            } catch (Exception e) {
                log.warn("[LlmChatAgent] JSON 序列化预览失败: {}", e.getMessage());
            }
        }

        return options;
    }

    /**
     * 根据厂商格式构建 reasoning extraBody
     *
     * @param request 聊天请求
     * @return extraBody Map，如果不需要则返回 null
     */
    private Map<String, Object> buildReasoningExtraBody(ChatRequest request) {
        String thinkingMode = request.getThinkingMode();

        // 如果没有设置 thinkingMode 或者是 auto，不传递参数
        if (!StringUtils.hasText(thinkingMode) || "auto".equalsIgnoreCase(thinkingMode)) {
            return null;
        }

        // 确定实际使用的格式
        ChatRequest.ReasoningFormat format = resolveReasoningFormat(request);
        log.info("[LlmChatAgent] Reasoning 格式: {}, 值: {}", format, thinkingMode);

        return switch (format) {
            case DOUBAO -> {
                // 豆包格式: {"thinking": {"type": "enabled/disabled"}}
                Map<String, Object> thinkingConfig = Map.of("type", thinkingMode);
                yield Map.of("thinking", thinkingConfig);
            }
            case OPENAI -> {
                // OpenAI 格式: {"reasoning_effort": "low/medium/high"}
                yield Map.of("reasoning_effort", thinkingMode);
            }
            case DEEPSEEK -> {
                // DeepSeek 不需要额外参数
                yield null;
            }
            case AUTO -> {
                // AUTO 模式下根据模型名推断（这里不应该到达，因为已在 resolveReasoningFormat 处理）
                log.warn("[LlmChatAgent] AUTO 格式未能解析，使用豆包格式作为默认");
                Map<String, Object> defaultConfig = Map.of("type", thinkingMode);
                yield Map.of("thinking", defaultConfig);
            }
        };
    }

    /**
     * 解析实际使用的 Reasoning 格式
     * 如果是 AUTO，则根据模型名称自动推断
     */
    private ChatRequest.ReasoningFormat resolveReasoningFormat(ChatRequest request) {
        ChatRequest.ReasoningFormat format = request.getReasoningFormat();

        // 如果不是 AUTO，直接返回
        if (format != null && format != ChatRequest.ReasoningFormat.AUTO) {
            return format;
        }

        // AUTO 模式：根据模型名称推断
        String model = request.getModelIdentifier();
        if (model == null) {
            return ChatRequest.ReasoningFormat.DOUBAO; // 默认使用豆包格式
        }

        String modelLower = model.toLowerCase();

        // OpenAI o1/o3 系列
        if (modelLower.contains("o1") || modelLower.contains("o3") || modelLower.contains("o4") || modelLower.contains("gpt")) {
            return ChatRequest.ReasoningFormat.OPENAI;
        }

        // DeepSeek R1 系列
        if (modelLower.contains("deepseek") && modelLower.contains("r1")) {
            return ChatRequest.ReasoningFormat.DEEPSEEK;
        }

        // 豆包/Doubao 系列
        if (modelLower.contains("doubao") || modelLower.contains("seed")) {
            return ChatRequest.ReasoningFormat.DOUBAO;
        }

        // 默认使用豆包格式（兼容更多国内模型）
        return ChatRequest.ReasoningFormat.DOUBAO;
    }

    /**
     * 添加用户消息（支持多模态）
     */
    private void addUserMessage(ChatClient.ChatClientRequestSpec promptBuilder, ChatRequest request) {
        if (request.hasMedia()) {
            // 多模态消息
            promptBuilder.user(userSpec -> {
                userSpec.text(request.getUserMessage());
                Media[] mediaArray = convertToSpringAiMedia(request.getMediaContents());
                if (mediaArray.length > 0) {
                    userSpec.media(mediaArray);
                    log.info("[LlmChatAgent] 多模态请求，媒体数量: {}", mediaArray.length);
                }
            });
        } else {
            // 纯文本消息
            promptBuilder.user(request.getUserMessage());
        }
    }

    /**
     * 添加工具调用
     */
    private void addTools(ChatClient.ChatClientRequestSpec promptBuilder, ChatRequest request) {
        if (Boolean.TRUE.equals(request.getEnableTools())) {
            Object[] toolObjects = toolFunctionManager.getToolObjects(request.getToolNames());
            if (toolObjects.length > 0) {
                log.info("[LlmChatAgent] 启用工具调用，工具数: {}", toolObjects.length);
                promptBuilder.tools(toolObjects);
            } else {
                log.warn("[LlmChatAgent] 启用工具调用，但没有可用的工具");
            }
        }
    }

    /**
     * 创建 ChatClient（内部使用）
     */
    private ChatClient createChatClient(ChatRequest request, String conversationId) {
        ChatModel chatModel = getOrCreateChatModel(request);
        ChatClient.Builder builder = ChatClient.builder(chatModel);

        // 按需添加 MemoryAdvisor
        if (conversationId != null && memoryAdvisor != null) {
            builder.defaultAdvisors(memoryAdvisor);
        }

        return builder.build();
    }

    /**
     * 获取或创建 ChatModel（带缓存）
     */
    private ChatModel getOrCreateChatModel(ChatRequest request) {
        String cacheKey = buildCacheKey(request);

        return chatModelCache.computeIfAbsent(cacheKey, k -> {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(request.getBaseUrl())
                    .apiKey(request.getApiKey())
                    .build();

            return OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .build();
        });
    }

    private String buildCacheKey(ChatRequest request) {
        return request.getChannelId() + "_" + request.getApiKey() + "_" + request.getBaseUrl();
    }

    // ==================== Spring AI 原生 API ====================

    /**
     * 创建带 Advisor 的 ChatClient
     */
    public ChatClient createChatClient(ChatModel chatModel) {
        ChatClient.Builder builder = ChatClient.builder(chatModel);
        List<Advisor> advisors = advisorManager.getAllAdvisors();
        if (!advisors.isEmpty()) {
            builder.defaultAdvisors(advisors.toArray(new Advisor[0]));
        }
        return builder.build();
    }

    /**
     * 创建纯净的 ChatClient
     */
    public ChatClient createPureChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    /**
     * 创建带自定义 Advisor 的 ChatClient
     */
    public ChatClient createChatClientWithAdvisors(ChatModel chatModel, Advisor... advisors) {
        ChatClient.Builder builder = ChatClient.builder(chatModel);
        List<Advisor> allAdvisors = new ArrayList<>(advisorManager.getAllAdvisors());
        if (advisors != null && advisors.length > 0) {
            allAdvisors.addAll(Arrays.asList(advisors));
        }
        if (!allAdvisors.isEmpty()) {
            builder.defaultAdvisors(allAdvisors.toArray(new Advisor[0]));
        }
        return builder.build();
    }

    // ==================== 缓存管理 ====================

    public void clearCacheForChannel(Long channelId) {
        chatModelCache.entrySet().removeIf(entry -> entry.getKey().startsWith(channelId + "_"));
    }

    public void clearAllCache() {
        chatModelCache.clear();
    }

    public void clearConversationHistory(String conversationId) {
        if (chatMemory != null && conversationId != null) {
            chatMemory.clear(conversationId);
        }
    }

    // ==================== 多模态支持 ====================

    private Media[] convertToSpringAiMedia(List<MediaMessage.MediaContent> mediaContents) {
        if (mediaContents == null || mediaContents.isEmpty()) {
            return new Media[0];
        }

        List<Media> mediaList = new ArrayList<>();
        for (MediaMessage.MediaContent content : mediaContents) {
            Media media = convertSingleMedia(content);
            if (media != null) {
                mediaList.add(media);
            }
        }
        return mediaList.toArray(new Media[0]);
    }

    private Media convertSingleMedia(MediaMessage.MediaContent content) {
        try {
            MimeType mimeType = parseMimeType(content);

            if (content.isUrlMode()) {
                return new Media(mimeType, URI.create(content.getMediaUrl()));
            } else if (content.isDataMode()) {
                return Media.builder()
                        .mimeType(mimeType)
                        .data(content.getMediaData())
                        .build();
            } else {
                log.warn("[LlmChatAgent] 媒体内容无效");
                return null;
            }
        } catch (Exception e) {
            log.error("[LlmChatAgent] 转换媒体内容失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private MimeType parseMimeType(MediaMessage.MediaContent content) {
        if (content.getMimeType() != null && !content.getMimeType().isEmpty()) {
            return MimeType.valueOf(content.getMimeType());
        }

        return switch (content.getMediaType()) {
            case IMAGE -> MimeTypeUtils.IMAGE_PNG;
            case DOCUMENT -> MimeType.valueOf("application/pdf");
            case AUDIO -> MimeType.valueOf("audio/mpeg");
            case VIDEO -> MimeType.valueOf("video/mp4");
            default -> MimeTypeUtils.APPLICATION_OCTET_STREAM;
        };
    }
}
