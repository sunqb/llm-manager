package com.llmmanager.agent.agent;

import com.llmmanager.agent.advisor.AdvisorManager;
import com.llmmanager.agent.advisor.ThinkingAdvisor;
import com.llmmanager.agent.config.ToolFunctionManager;
import com.llmmanager.agent.dto.ChatRequest;
import com.llmmanager.agent.mcp.McpClientManager;
import com.llmmanager.agent.message.MediaMessage;
import com.llmmanager.agent.model.ThinkingChatModel;
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
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import lombok.extern.slf4j.Slf4j;

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

    @Autowired(required = false)
    private McpClientManager mcpClientManager;

    @Autowired(required = false)
    private ThinkingAdvisor thinkingAdvisor;

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
    public String chat(ChatRequest request, String conversationCode) {
        ChatClient chatClient = createChatClient(request, conversationCode);
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

        // 执行（传递 Advisor 参数）
        ChatResponse response = promptBuilder
                .options(options)
                .advisors(advisor -> {
                    // Memory Advisor 参数（使用 conversationCode 作为 Spring AI 的 CONVERSATION_ID）
                    if (conversationCode != null) {
                        advisor.param(ChatMemory.CONVERSATION_ID, conversationCode);
                    }
                    // Thinking Advisor 参数
                    addThinkingAdvisorParams(advisor, request);
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
     * - 思考模式（通过 ThinkingAdvisor 处理，自动将 thinking 参数注入到顶层）
     */
    public Flux<ChatResponse> stream(ChatRequest request, String conversationCode) {
        // 打印完整的请求参数
        // logFullRequest(request, conversationCode);

        ChatClient chatClient = createChatClient(request, conversationCode);
        OpenAiChatOptions options = buildOptions(request);

        // 打印完整的 options
        // logFullOptions(options);

        var promptBuilder = chatClient.prompt();

        // 系统提示词
        if (StringUtils.hasText(request.getSystemPrompt())) {
            promptBuilder.system(request.getSystemPrompt());
        }

        // 用户消息（支持多模态）
        addUserMessage(promptBuilder, request);

        // 工具调用
        addTools(promptBuilder, request);

        // 执行流式请求（传递 Advisor 参数）
        return promptBuilder
                .options(options)
                .advisors(advisor -> {
                    // Memory Advisor 参数（使用 conversationCode 作为 Spring AI 的 CONVERSATION_ID）
                    if (conversationCode != null) {
                        advisor.param(ChatMemory.CONVERSATION_ID, conversationCode);
                    }
                    // Thinking Advisor 参数
                    addThinkingAdvisorParams(advisor, request);
                })
                .stream()
                .chatResponse();
    }

    /**
     * 打印完整的请求参数
     */
    private void logFullRequest(ChatRequest request, String conversationCode) {
        log.info("==================== LlmChatAgent 请求参数 ====================");
        log.info("baseUrl: {}", request.getBaseUrl());
        log.info("modelIdentifier: {}", request.getModelIdentifier());
        log.info("temperature: {}", request.getTemperature());
        log.info("thinkingMode: '{}'", request.getThinkingMode());
        log.info("reasoningFormat: {}", request.getReasoningFormat());
        log.info("conversationCode: {}", conversationCode);
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
     *
     * 注意：thinking 参数由 ThinkingAdvisor 处理，不再在这里设置 extraBody。
     * ThinkingAdvisor 会将 thinking 直接注入到 ChatOptions 的顶层。
     */
    private OpenAiChatOptions buildOptions(ChatRequest request) {
        log.debug("[LlmChatAgent] buildOptions - model: {}, temperature: {}",
                request.getModelIdentifier(), request.getTemperature());

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

        // thinking 参数由 ThinkingAdvisor 处理，不再设置 extraBody

        return builder.build();
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
     * 添加工具调用（支持本地工具和 MCP 工具）
     *
     * 注意：
     * - 本地工具（@Tool 注解）使用 tools() 方法
     * - MCP 工具（ToolCallback）使用 toolCallbacks() 方法
     */
    private void addTools(ChatClient.ChatClientRequestSpec promptBuilder, ChatRequest request) {
        int totalTools = 0;

        // 1. 添加本地工具（@Tool 注解的方法）- 使用 tools()
        if (Boolean.TRUE.equals(request.getEnableTools())) {
            Object[] toolObjects = toolFunctionManager.getToolObjects(request.getToolNames());
            if (toolObjects.length > 0) {
                log.info("[LlmChatAgent] 添加本地工具，数量: {}", toolObjects.length);
                promptBuilder.tools(toolObjects);
                totalTools += toolObjects.length;
            }
        }

        // 2. 添加 MCP 工具（ToolCallback）- 使用 toolCallbacks()
        if (Boolean.TRUE.equals(request.getEnableMcpTools()) && mcpClientManager != null) {
            ToolCallback[] mcpCallbacks = mcpClientManager.getAllToolCallbacks();
            if (mcpCallbacks.length > 0) {
                log.info("[LlmChatAgent] 添加 MCP 工具，数量: {}", mcpCallbacks.length);
                promptBuilder.toolCallbacks(mcpCallbacks);
                totalTools += mcpCallbacks.length;
            }
        }

        // 3. 日志记录
        if (totalTools > 0) {
            log.info("[LlmChatAgent] 启用工具调用，总工具数: {}", totalTools);
        } else if (Boolean.TRUE.equals(request.getEnableTools()) || Boolean.TRUE.equals(request.getEnableMcpTools())) {
            log.warn("[LlmChatAgent] 启用工具调用，但没有可用的工具");
        }
    }

    /**
     * 添加 ThinkingAdvisor 参数到上下文
     *
     * 通过 Advisor context 传递 thinking 参数，ThinkingAdvisor 会：
     * 1. 读取这些参数
     * 2. 构建 ThinkingChatOptions
     * 3. 替换原有的 ChatOptions
     */
    private void addThinkingAdvisorParams(ChatClient.AdvisorSpec advisor, ChatRequest request) {
        String thinkingMode = request.getThinkingMode();
        if (StringUtils.hasText(thinkingMode) && !"auto".equalsIgnoreCase(thinkingMode)) {
            advisor.param(ThinkingAdvisor.THINKING_MODE, thinkingMode);
            log.debug("[LlmChatAgent] 传递 thinking_mode 到 Advisor: {}", thinkingMode);

            // 传递 reasoningFormat（如果有）
            if (request.getReasoningFormat() != null) {
                advisor.param(ThinkingAdvisor.REASONING_FORMAT, request.getReasoningFormat().name());
                log.debug("[LlmChatAgent] 传递 reasoning_format 到 Advisor: {}", request.getReasoningFormat());
            }
        }
    }

    /**
     * 创建 ChatClient（内部使用）
     *
     * Advisor 管理策略：
     * - 全局 Advisor（如 LoggingAdvisor）：通过 AdvisorManager 注册（见 createChatClient(ChatModel)）
     * - 条件 Advisor（如 MemoryAdvisor、ThinkingAdvisor）：按需添加（本方法）
     *   - MemoryAdvisor：需要 conversationCode 时才添加
     *   - ThinkingAdvisor：需要 thinkingMode 时才添加
     *
     * 设计理由：
     * - 条件 Advisor 的触发条件是请求级别的，无法在全局注册时判断
     * - 按需添加避免不必要的性能开销（如无 conversationCode 时不查询数据库）
     * - 保持 AdvisorManager 简单，不耦合业务参数
     *
     * Advisor 执行顺序（按 order 从小到大）：
     * 1. MemoryAdvisor (order=0) - 处理历史消息
     * 2. ThinkingAdvisor (order=100) - 注入 thinking 参数到 ChatOptions
     */
    private ChatClient createChatClient(ChatRequest request, String conversationCode) {
        ChatModel chatModel = getOrCreateChatModel(request);
        ChatClient.Builder builder = ChatClient.builder(chatModel);

        // 收集需要的 Advisor
        List<Advisor> advisors = new ArrayList<>();

        // 1. MemoryAdvisor（需要 conversationCode）
        if (conversationCode != null && memoryAdvisor != null) {
            advisors.add(memoryAdvisor);
        }

        // 2. ThinkingAdvisor（需要 thinkingMode）
        if (thinkingAdvisor != null && StringUtils.hasText(request.getThinkingMode())
                && !"auto".equalsIgnoreCase(request.getThinkingMode())) {
            advisors.add(thinkingAdvisor);
            log.info("[LlmChatAgent] 启用 ThinkingAdvisor, thinkingMode: {}", request.getThinkingMode());
        }

        if (!advisors.isEmpty()) {
            builder.defaultAdvisors(advisors.toArray(new Advisor[0]));
        }

        return builder.build();
    }

    /**
     * 获取或创建 ChatModel（带缓存）
     *
     * 使用 ThinkingChatModel 包装 OpenAiChatModel，以支持 thinking 参数注入。
     * Spring AI 的 ModelOptionsUtils.merge() 会丢弃 extraBody，
     * ThinkingChatModel 通过反射在 createRequest 后手动注入 thinking 参数。
     */
    private ChatModel getOrCreateChatModel(ChatRequest request) {
        String cacheKey = buildCacheKey(request);

        return chatModelCache.computeIfAbsent(cacheKey, k -> {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(request.getBaseUrl())
                    .apiKey(request.getApiKey())
                    .build();

            OpenAiChatModel baseModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .build();

            // 使用 ThinkingChatModel 包装，支持 thinking 参数注入
            return new ThinkingChatModel(baseModel);
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

    public void clearConversationHistory(String conversationCode) {
        if (chatMemory != null && conversationCode != null) {
            chatMemory.clear(conversationCode);
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
