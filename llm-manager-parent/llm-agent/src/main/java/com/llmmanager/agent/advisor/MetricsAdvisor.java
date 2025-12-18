package com.llmmanager.agent.advisor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 指标收集 Advisor（无侵入方式）
 *
 * 通过 Spring AI Advisor 机制，在请求前后自动收集指标：
 * - llm.chat.duration (Timer) - 对话耗时
 * - llm.tokens.prompt (Counter) - 输入 Token 数
 * - llm.tokens.completion (Counter) - 输出 Token 数
 * - llm.chat.total (Counter) - 对话总次数
 *
 * 配置开关：llm.observability.metrics-enabled=true
 *
 * 使用方式：
 * 1. 自动注册：通过 ObservabilityConfig 自动注册到 AdvisorManager
 * 2. 手动注册：advisorManager.registerAdvisor(metricsAdvisor)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "llm.observability.metrics-enabled", havingValue = "true", matchIfMissing = false)
public class MetricsAdvisor implements BaseAdvisor {

    private static final String REQUEST_START_TIME = "metrics_request_start_time";
    private static final String MODEL_TAG = "metrics_model";
    private static final String CHANNEL_TAG = "metrics_channel";

    @Resource
    private MeterRegistry meterRegistry;

    @Override
    public String getName() {
        return "MetricsAdvisor";
    }

    @Override
    public int getOrder() {
        return -100; // 最早执行，确保记录完整耗时
    }

    /**
     * 请求前处理：记录开始时间
     */
    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        // 记录开始时间（使用 nanoTime 以获得更精确的耗时）
        request.context().put(REQUEST_START_TIME, System.nanoTime());

        // 提取模型和渠道信息
        String model = extractModel(request);
        String channel = extractChannel(request);
        request.context().put(MODEL_TAG, model);
        request.context().put(CHANNEL_TAG, channel);

        log.debug("[MetricsAdvisor] 开始记录请求指标, model={}, channel={}", model, channel);

        return request;
    }

    /**
     * 响应后处理：记录指标
     */
    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        // 计算耗时
        Long startTime = (Long) response.context().get(REQUEST_START_TIME);
        long duration = startTime != null ? System.nanoTime() - startTime : 0;

        String model = (String) response.context().getOrDefault(MODEL_TAG, "unknown");
        String channel = (String) response.context().getOrDefault(CHANNEL_TAG, "unknown");
        String status = determineStatus(response);

        // 记录耗时指标
        Timer.builder("llm.chat.duration")
                .tag("model", model)
                .tag("channel", channel)
                .tag("status", status)
                .description("LLM 对话耗时")
                .register(meterRegistry)
                .record(duration, TimeUnit.NANOSECONDS);

        // 记录调用次数
        Counter.builder("llm.chat.total")
                .tag("model", model)
                .tag("channel", channel)
                .tag("status", status)
                .description("LLM 对话总次数")
                .register(meterRegistry)
                .increment();

        // 记录 Token 使用量
        if (response.chatResponse() != null &&
            response.chatResponse().getMetadata() != null &&
            response.chatResponse().getMetadata().getUsage() != null) {

            var usage = response.chatResponse().getMetadata().getUsage();

            if (usage.getPromptTokens() != null) {
                Counter.builder("llm.tokens.prompt")
                        .tag("model", model)
                        .tag("channel", channel)
                        .description("输入 Token 数")
                        .register(meterRegistry)
                        .increment(usage.getPromptTokens());
            }

            if (usage.getCompletionTokens() != null) {
                Counter.builder("llm.tokens.completion")
                        .tag("model", model)
                        .tag("channel", channel)
                        .description("输出 Token 数")
                        .register(meterRegistry)
                        .increment(usage.getCompletionTokens());
            }

            log.debug("[MetricsAdvisor] 记录完成, model={}, channel={}, status={}, duration={}ms, " +
                            "promptTokens={}, completionTokens={}",
                    model, channel, status, TimeUnit.NANOSECONDS.toMillis(duration),
                    usage.getPromptTokens(), usage.getCompletionTokens());
        } else {
            log.debug("[MetricsAdvisor] 记录完成, model={}, channel={}, status={}, duration={}ms",
                    model, channel, status, TimeUnit.NANOSECONDS.toMillis(duration));
        }

        return response;
    }

    /**
     * 从请求中提取模型名称
     */
    private String extractModel(ChatClientRequest request) {
        if (request.prompt() != null && request.prompt().getOptions() != null) {
            Object model = request.prompt().getOptions().getModel();
            if (model != null) {
                return model.toString();
            }
        }
        return "unknown";
    }

    /**
     * 从请求中提取渠道标识
     */
    private String extractChannel(ChatClientRequest request) {
        if (request.context() != null) {
            Object channelId = request.context().get("channelId");
            if (channelId != null) {
                return channelId.toString();
            }
        }
        return "unknown";
    }

    /**
     * 判断响应状态
     */
    private String determineStatus(ChatClientResponse response) {
        if (response.chatResponse() != null && response.chatResponse().getResult() != null) {
            return "success";
        }
        return "error";
    }
}
