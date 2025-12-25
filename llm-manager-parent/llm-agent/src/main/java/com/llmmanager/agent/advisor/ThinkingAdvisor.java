package com.llmmanager.agent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Thinking 参数处理 Advisor
 *
 * 核心功能：
 * - 在请求前读取上下文中的 thinking 参数
 * - 将 thinking 参数注入到 OpenAiChatOptions.extraBody 中 【实际做做了这件事】
 * - Spring AI 1.1+ 会将 extraBody 打平到 JSON 根层级
 *
 * 解决方案说明：
 * OpenAiChatModel.createRequest() 会将 prompt.getOptions() 强转为 OpenAiChatOptions，
 * 导致自定义 ChatOptions 子类的字段丢失。解决方案是使用 OpenAiChatOptions.extraBody，
 * 因为：
 * 1. extraBody 在 ModelOptionsUtils.merge() 时会被完整拷贝
 * 2. ChatCompletionRequest.extraBody 使用 @JsonAnyGetter 打平到 JSON 根层级
 *
 * 使用方式：
 * chatClient.prompt()
 *     .advisors(advisor -> advisor.param(ThinkingAdvisor.THINKING_MODE, "enabled"))
 *     .advisors(advisor -> advisor.param(ThinkingAdvisor.REASONING_FORMAT, "DOUBAO"))
 *     .user("你好")
 *     .call()
 */
@Slf4j
@Component
public class ThinkingAdvisor implements BaseAdvisor {

    /**
     * 上下文参数：思考模式值
     * - DOUBAO 格式: enabled, disabled
     * - OPENAI 格式: low, medium, high
     */
    public static final String THINKING_MODE = "thinking_mode";

    /**
     * 上下文参数：Reasoning 格式
     * - DOUBAO: 豆包/火山引擎格式 {"thinking": {"type": "enabled"}}
     * - OPENAI: OpenAI o1/o3 格式 {"reasoning_effort": "medium"}
     * - AUTO: 自动根据模型名推断
     */
    public static final String REASONING_FORMAT = "reasoning_format";

    /**
     * Reasoning 格式枚举
     */
    public enum ReasoningFormat {
        DOUBAO, OPENAI, DEEPSEEK, AUTO
    }

    @Override
    public String getName() {
        return "ThinkingAdvisor";
    }

    @Override
    public int getOrder() {
        // 在 Memory Advisor 之后执行，但在其他 Advisor 之前
        return 100;
    }

    /**
     * 请求前处理：注入 thinking 参数（实际上是转换我们的业务定义的thinking（我们叫thinkingMode和format）到标准的格式的thinking，例如doubao的格式）到 OpenAiChatOptions.extraBody
     */
    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        // 从上下文获取 thinking 参数
        String thinkingMode = getContextParam(request, THINKING_MODE, String.class);
        String formatStr = getContextParam(request, REASONING_FORMAT, String.class);

        // 如果没有设置 thinkingMode 或者是 auto，不处理
        if (thinkingMode == null || thinkingMode.isEmpty() || "auto".equalsIgnoreCase(thinkingMode)) {
            return request;
        }

        // 获取原始 Prompt 和 ChatOptions
        Prompt originalPrompt = request.prompt();
        ChatOptions originalOptions = originalPrompt != null ? originalPrompt.getOptions() : null;

        // 解析格式
        ReasoningFormat format = parseFormat(formatStr, originalOptions);
        log.debug("[ThinkingAdvisor] 处理 thinking 参数 - mode: {}, format: {}", thinkingMode, format);

        // 构建带 metadata 的 OpenAiChatOptions
        OpenAiChatOptions newOptions = buildOpenAiOptionsWithExtraBody(originalOptions, thinkingMode, format);

        // 使用 Prompt.mutate() 创建新的 Prompt，替换 ChatOptions
        Prompt modifiedPrompt = originalPrompt.mutate()
                .chatOptions(newOptions)
                .build();

        // 创建新的 ChatClientRequest
        ChatClientRequest modifiedRequest = ChatClientRequest.builder()
                .prompt(modifiedPrompt)
                .context(request.context())
                .build();

        return modifiedRequest;
    }

    /**
     * 响应后处理：直接返回（不做处理）
     *
     * 注意：BaseAdvisor 的默认 adviseStream 实现会自动复用这个方法，
     * 所以不需要单独实现 beforeStream 和 afterStream。
     */
    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    // ==================== 内部方法 ====================

    /**
     * 从上下文获取参数
     */
    @SuppressWarnings("unchecked")
    private <T> T getContextParam(ChatClientRequest request, String key, Class<T> type) {
        if (request.context() == null) {
            return null;
        }
        Object value = request.context().get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * 解析 Reasoning 格式
     */
    private ReasoningFormat parseFormat(String formatStr, ChatOptions options) {
        if (formatStr != null && !formatStr.isEmpty()) {
            try {
                return ReasoningFormat.valueOf(formatStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("[ThinkingAdvisor] 无效的 reasoningFormat: {}, 使用 AUTO", formatStr);
            }
        }

        // AUTO 模式：根据模型名推断
        String model = options != null ? options.getModel() : null;
        return inferFormatFromModel(model);
    }

    /**
     * 根据模型名推断格式
     */
    private ReasoningFormat inferFormatFromModel(String model) {
        if (model == null || model.isEmpty()) {
            return ReasoningFormat.DOUBAO; // 默认使用豆包格式
        }

        String modelLower = model.toLowerCase();

        // OpenAI o1/o3 系列
        if (modelLower.contains("o1") || modelLower.contains("o3") || modelLower.contains("o4")) {
            return ReasoningFormat.OPENAI;
        }

        // DeepSeek R1 系列
        if (modelLower.contains("deepseek") && modelLower.contains("r1")) {
            return ReasoningFormat.DEEPSEEK;
        }

        // 豆包/Doubao 系列
        if (modelLower.contains("doubao") || modelLower.contains("seed")) {
            return ReasoningFormat.DOUBAO;
        }

        // 默认使用豆包格式（兼容更多国内模型）
        return ReasoningFormat.DOUBAO;
    }

    /**
     * 构建带 extraBody 的 OpenAiChatOptions
     *
     * 关键：将 thinking 参数放入 extraBody，Spring AI 会将其打平到 JSON 根层级
     */
    private OpenAiChatOptions buildOpenAiOptionsWithExtraBody(ChatOptions originalOptions, String thinkingMode, ReasoningFormat format) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();

        // 复制原始 Options 的所有参数
        if (originalOptions != null) {
            builder.model(originalOptions.getModel())
                    .temperature(originalOptions.getTemperature())
                    .topP(originalOptions.getTopP())
                    .maxTokens(originalOptions.getMaxTokens())
                    .frequencyPenalty(originalOptions.getFrequencyPenalty())
                    .presencePenalty(originalOptions.getPresencePenalty())
                    .stop(originalOptions.getStopSequences());

            // 复制 OpenAI 特有的参数（包括工具配置）
            if (originalOptions instanceof OpenAiChatOptions openAiOptions) {
                // OpenAI 低级工具配置（FunctionTool）
                if (openAiOptions.getTools() != null) {
                    builder.tools(openAiOptions.getTools());
                }
                if (openAiOptions.getToolChoice() != null) {
                    builder.toolChoice(openAiOptions.getToolChoice());
                }
                if (openAiOptions.getParallelToolCalls() != null) {
                    builder.parallelToolCalls(openAiOptions.getParallelToolCalls());
                }
                // 其他 OpenAI 参数
                if (openAiOptions.getUser() != null) {
                    builder.user(openAiOptions.getUser());
                }
                if (openAiOptions.getSeed() != null) {
                    builder.seed(openAiOptions.getSeed());
                }
                if (openAiOptions.getLogprobs() != null) {
                    builder.logprobs(openAiOptions.getLogprobs());
                }
                if (openAiOptions.getTopLogprobs() != null) {
                    builder.topLogprobs(openAiOptions.getTopLogprobs());
                }
                if (openAiOptions.getResponseFormat() != null) {
                    builder.responseFormat(openAiOptions.getResponseFormat());
                }
            }

            // 复制 Spring AI ToolCallingChatOptions 参数（关键！工具回调在这里）
            if (originalOptions instanceof ToolCallingChatOptions toolOptions) {
                // Spring AI 高级工具配置（ToolCallback）
                if (!CollectionUtils.isEmpty(toolOptions.getToolCallbacks())) {
                    builder.toolCallbacks(toolOptions.getToolCallbacks());
                    log.debug("[ThinkingAdvisor] 复制 toolCallbacks, 数量: {}", toolOptions.getToolCallbacks().size());
                }
                if (!CollectionUtils.isEmpty(toolOptions.getToolNames())) {
                    builder.toolNames(toolOptions.getToolNames());
                    log.debug("[ThinkingAdvisor] 复制 toolNames: {}", toolOptions.getToolNames());
                }
                if (!CollectionUtils.isEmpty(toolOptions.getToolContext())) {
                    builder.toolContext(toolOptions.getToolContext());
                }
                if (toolOptions.getInternalToolExecutionEnabled() != null) {
                    builder.internalToolExecutionEnabled(toolOptions.getInternalToolExecutionEnabled());
                }
            }
        }

        // 构建 metadata（用于传递 thinking 参数，解决 extraBody 在 merge 时丢失的问题）
        // ThinkingAwareOpenAiApi 会从 metadata 读取并展开到 extraBody
        Map<String, String> metadata = new HashMap<>();

        // 如果原有 metadata，先复制
        if (originalOptions instanceof OpenAiChatOptions openAiOptions) {
            if (openAiOptions.getMetadata() != null) {
                metadata.putAll(openAiOptions.getMetadata());
            }
            // 保留原有 extraBody（排除 thinking 相关参数）
            if (openAiOptions.getExtraBody() != null && !openAiOptions.getExtraBody().isEmpty()) {
                Map<String, Object> extraBody = new HashMap<>();
                for (var entry : openAiOptions.getExtraBody().entrySet()) {
                    String key = entry.getKey();
                    if (!"thinking".equals(key) && !"reasoning_effort".equals(key) && !"enable_thinking".equals(key)) {
                        extraBody.put(key, entry.getValue());
                    }
                }
                if (!extraBody.isEmpty()) {
                    builder.extraBody(extraBody);
                }
            }
        }

        // 根据格式添加 thinking 参数到 metadata
        // ThinkingAwareOpenAiApi 会在 HTTP 层面将 metadata 展开到 extraBody
        switch (format) {
            case DOUBAO -> {
                // 豆包格式: metadata 中放 thinking_mode=enabled/disabled
                // ThinkingAwareOpenAiApi 会转换为 {"thinking": {"type": "enabled/disabled"}}
                metadata.put("thinking_mode", thinkingMode);
                log.debug("[ThinkingAdvisor] 设置豆包格式 thinking_mode 到 metadata: {}", thinkingMode);
            }
            case OPENAI -> {
                // OpenAI 格式: metadata 中放 reasoning_effort=low/medium/high
                // ThinkingAwareOpenAiApi 会转换为 {"reasoning_effort": "low/medium/high"}
                metadata.put("reasoning_effort", thinkingMode);
                log.debug("[ThinkingAdvisor] 设置 OpenAI 格式 reasoning_effort 到 metadata: {}", thinkingMode);
            }
            case DEEPSEEK -> {
                // DeepSeek 不需要额外参数，R1 模型自动启用深度思考
                log.debug("[ThinkingAdvisor] DeepSeek 格式，不设置额外参数");
            }
            default -> {
                // 默认使用豆包格式
                metadata.put("thinking_mode", thinkingMode);
                log.debug("[ThinkingAdvisor] 默认使用豆包格式 thinking_mode 到 metadata: {}", thinkingMode);
            }
        }

        // 设置 metadata
        if (!metadata.isEmpty()) {
            builder.metadata(metadata);
            log.debug("[ThinkingAdvisor] 设置 metadata: {}", metadata);
        }

        return builder.build();
    }
}
