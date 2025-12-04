package com.llmmanager.agent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

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
            log.debug("[ThinkingAdvisor] 未设置 thinkingMode 或为 auto，跳过处理");
            return request;
        }

        // 获取原始 Prompt 和 ChatOptions
        Prompt originalPrompt = request.prompt();
        ChatOptions originalOptions = originalPrompt != null ? originalPrompt.getOptions() : null;

        // 解析格式
        ReasoningFormat format = parseFormat(formatStr, originalOptions);
        log.info("[ThinkingAdvisor] 处理 thinking 参数 - mode: {}, format: {}", thinkingMode, format);

        // 构建带 extraBody 的 OpenAiChatOptions
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

        log.info("[ThinkingAdvisor] 已注入 thinking 参数到 extraBody");
        // logOpenAiOptions(newOptions);

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

        // 复制原始 Options 的参数
        if (originalOptions != null) {
            builder.model(originalOptions.getModel())
                    .temperature(originalOptions.getTemperature())
                    .topP(originalOptions.getTopP())
                    .maxTokens(originalOptions.getMaxTokens())
                    .frequencyPenalty(originalOptions.getFrequencyPenalty())
                    .presencePenalty(originalOptions.getPresencePenalty())
                    .stop(originalOptions.getStopSequences());
        }

        // 构建 extraBody（会被打平到 JSON 根层级）
        Map<String, Object> extraBody = new HashMap<>();

        // 如果原有 extraBody，先复制（排除 thinking 和 reasoning_effort）
        if (originalOptions instanceof OpenAiChatOptions openAiOptions) {
            if (openAiOptions.getExtraBody() != null) {
                for (var entry : openAiOptions.getExtraBody().entrySet()) {
                    String key = entry.getKey();
                    if (!"thinking".equals(key) && !"reasoning_effort".equals(key)) {
                        extraBody.put(key, entry.getValue());
                    }
                }
            }
        }

        // 根据格式添加 thinking 参数到 extraBody
        switch (format) {
            case DOUBAO -> {
                // 豆包格式: extraBody 中放 {"thinking": {"type": "enabled/disabled"}}
                // 最终会打平到 JSON 根层级
                Map<String, Object> thinking = new HashMap<>();
                thinking.put("type", thinkingMode);
                extraBody.put("thinking", thinking);
                log.debug("[ThinkingAdvisor] 设置豆包格式 thinking: {}", thinkingMode);
            }
            case OPENAI -> {
                // OpenAI 格式: extraBody 中放 {"reasoning_effort": "low/medium/high"}
                extraBody.put("reasoning_effort", thinkingMode);
                log.debug("[ThinkingAdvisor] 设置 OpenAI 格式 reasoning_effort: {}", thinkingMode);
            }
            case DEEPSEEK -> {
                // DeepSeek 不需要额外参数，R1 模型自动启用深度思考
                log.debug("[ThinkingAdvisor] DeepSeek 格式，不设置额外参数");
            }
            default -> {
                // 默认使用豆包格式
                Map<String, Object> thinking = new HashMap<>();
                thinking.put("type", thinkingMode);
                extraBody.put("thinking", thinking);
                log.debug("[ThinkingAdvisor] 默认使用豆包格式 thinking: {}", thinkingMode);
            }
        }

        // 设置 extraBody
        if (!extraBody.isEmpty()) {
            builder.extraBody(extraBody);
        }

        return builder.build();
    }

    /**
     * 打印 OpenAiChatOptions（调试用）
     */
    private void logOpenAiOptions(OpenAiChatOptions options) {
        if (log.isInfoEnabled()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
                String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(options);
                log.info("[ThinkingAdvisor] OpenAiChatOptions JSON:\n{}", json);

                // 单独打印 extraBody 内容
                if (options.getExtraBody() != null && !options.getExtraBody().isEmpty()) {
                    log.info("[ThinkingAdvisor] extraBody 内容（将打平到 JSON 根层级）: {}", options.getExtraBody());
                }
            } catch (Exception e) {
                log.warn("[ThinkingAdvisor] 序列化 OpenAiChatOptions 失败: {}", e.getMessage());
            }
        }
    }
}
