package com.llmmanager.openapi.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmmanager.agent.storage.core.entity.ReactAgent;
import com.llmmanager.common.exception.BusinessException;
import com.llmmanager.common.result.Result;
import com.llmmanager.common.result.ResultCode;
import com.llmmanager.service.orchestration.DynamicReactAgentExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 外部 ReactAgent API Controller
 *
 * 提供给外部应用调用的 ReactAgent API，通过 ApiKeyAuthFilter 进行认证
 *
 * 接口设计：
 * - 同步接口：POST /api/external/react-agent/{slug}
 * - 流式接口：POST /api/external/react-agent/{slug}/stream
 * - 列表接口：GET /api/external/react-agent/list
 *
 * 使用示例（curl）：
 * <pre>
 * # 同步调用
 * curl -X POST https://your-domain/api/external/react-agent/universal-assistant \
 *   -H "Authorization: Bearer sk-xxxx" \
 *   -H "Content-Type: application/json" \
 *   -d '{"message": "帮我查询北京天气"}'
 *
 * # 流式调用
 * curl -N -X POST https://your-domain/api/external/react-agent/universal-assistant/stream \
 *   -H "Authorization: Bearer sk-xxxx" \
 *   -H "Content-Type: application/json" \
 *   -d '{"message": "帮我查询北京天气"}'
 * </pre>
 *
 * @author LLM Manager
 */
@Slf4j
@RestController
@RequestMapping("/api/external/react-agent")
public class ExternalReactAgentController {

    @Resource
    private DynamicReactAgentExecutionService dynamicReactAgentService;

    @Resource
    private ObjectMapper objectMapper;

    // ==================== 同步接口 ====================

    /**
     * 同步执行 ReactAgent
     *
     * @param slug    Agent 唯一标识
     * @param payload 请求体
     * @return 执行结果
     */
    @PostMapping("/{slug}")
    public Result<Map<String, Object>> execute(
            @PathVariable String slug,
            @RequestBody Map<String, Object> payload) {

        String message = getStringParam(payload, "message");

        // 参数校验
        if (!StringUtils.hasText(message)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Message content is required");
        }

        // 检查 Agent 是否存在
        ReactAgent agent = dynamicReactAgentService.getAgentBySlug(slug);
        if (agent == null) {
            log.warn("[ExternalReactAgent] Agent 不存在，slug: {}", slug);
            throw new BusinessException(ResultCode.REACT_AGENT_NOT_FOUND, "ReactAgent not found: " + slug);
        }

        log.info("[ExternalReactAgent] 同步执行，slug: {}, message: {}", slug, truncateMessage(message));

        try {
            Map<String, Object> result = dynamicReactAgentService.execute(slug, message);
            return Result.success(result);
        } catch (Exception e) {
            log.error("[ExternalReactAgent] 执行失败，slug: {}", slug, e);
            throw new BusinessException(ResultCode.AGENT_EXECUTION_FAILED, "ReactAgent execution failed: " + e.getMessage());
        }
    }

    // ==================== 流式接口 ====================

    /**
     * 流式执行 ReactAgent（SSE）
     *
     * 由于 ReactAgent 是多轮推理模式，流式输出为"进度流"：
     * - event: start    - 开始执行
     * - event: progress - 执行进度（thinking/action/result）
     * - event: complete - 执行完成，包含最终结果
     * - event: error    - 执行错误
     *
     * @param slug    Agent 唯一标识
     * @param payload 请求体
     * @return SSE 事件流
     */
    @PostMapping(value = "/{slug}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> executeStream(
            @PathVariable String slug,
            @RequestBody Map<String, Object> payload) {

        String message = getStringParam(payload, "message");

        // 参数校验
        if (!StringUtils.hasText(message)) {
            return errorEvent("Message content is required");
        }

        // 检查 Agent 是否存在
        ReactAgent agent = dynamicReactAgentService.getAgentBySlug(slug);
        if (agent == null) {
            log.warn("[ExternalReactAgent] Agent 不存在，slug: {}", slug);
            return errorEvent("ReactAgent not found: " + slug);
        }

        log.info("[ExternalReactAgent] 流式执行，slug: {}, message: {}", slug, truncateMessage(message));

        // 构建进度流
        return buildProgressStream(slug, agent, message);
    }

    /**
     * 构建进度流
     *
     * ReactAgent 执行过程中输出进度事件
     */
    private Flux<ServerSentEvent<String>> buildProgressStream(String slug, ReactAgent agent, String message) {
        // 开始事件
        Flux<ServerSentEvent<String>> startEvent = Flux.just(
                buildSseEvent("start", Map.of(
                        "slug", slug,
                        "agentName", agent.getName(),
                        "agentType", agent.getAgentType(),
                        "status", "processing"
                ))
        );

        // 思考中进度事件
        Flux<ServerSentEvent<String>> thinkingEvent = Flux.just(
                buildSseEvent("progress", Map.of(
                        "step", "thinking",
                        "message", "Agent 正在分析问题..."
                ))
        ).delayElements(Duration.ofMillis(100));

        // 异步执行并返回结果
        Flux<ServerSentEvent<String>> resultEvents = Mono.fromCallable(() -> {
                    // 在独立线程中执行 Agent
                    return dynamicReactAgentService.execute(slug, message);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(result -> {
                    // 执行完成事件
                    Map<String, Object> completeData = new HashMap<>(result);
                    completeData.put("status", "completed");

                    return Flux.just(
                            buildSseEvent("complete", completeData),
                            ServerSentEvent.<String>builder().data("[DONE]").build()
                    );
                })
                .onErrorResume(error -> {
                    log.error("[ExternalReactAgent] 流式执行失败，slug: {}", slug, error);
                    return Flux.just(
                            buildSseEvent("error", Map.of(
                                    "error", error.getMessage(),
                                    "status", "failed"
                            )),
                            ServerSentEvent.<String>builder().data("[DONE]").build()
                    );
                });

        return Flux.concat(startEvent, thinkingEvent, resultEvents);
    }

    // ==================== 查询接口 ====================

    /**
     * 获取可用的 ReactAgent 列表
     *
     * @return Agent 列表（简化信息）
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> listAgents() {
        List<ReactAgent> agents = dynamicReactAgentService.getActiveAgents();

        List<Map<String, Object>> simplifiedList = agents.stream()
                .map(this::simplifyAgentInfo)
                .collect(Collectors.toList());

        return Result.success(simplifiedList);
    }

    /**
     * 获取单个 ReactAgent 详情
     *
     * @param slug Agent 唯一标识
     * @return Agent 详情
     */
    @GetMapping("/{slug}")
    public Result<Map<String, Object>> getAgent(@PathVariable String slug) {
        ReactAgent agent = dynamicReactAgentService.getAgentBySlug(slug);
        if (agent == null) {
            throw new BusinessException(ResultCode.REACT_AGENT_NOT_FOUND, "ReactAgent not found: " + slug);
        }
        return Result.success(simplifyAgentInfo(agent));
    }

    // ==================== 辅助方法 ====================

    /**
     * 简化 Agent 信息（对外暴露）
     */
    private Map<String, Object> simplifyAgentInfo(ReactAgent agent) {
        Map<String, Object> info = new HashMap<>();
        info.put("slug", agent.getSlug());
        info.put("name", agent.getName());
        info.put("description", agent.getDescription());
        info.put("agentType", agent.getAgentType());
        info.put("isActive", agent.getIsActive());
        return info;
    }

    /**
     * 构建 SSE 事件
     */
    private ServerSentEvent<String> buildSseEvent(String eventType, Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return ServerSentEvent.<String>builder()
                    .event(eventType)
                    .data(json)
                    .build();
        } catch (JsonProcessingException e) {
            log.error("[ExternalReactAgent] JSON 序列化失败", e);
            return ServerSentEvent.<String>builder()
                    .event(eventType)
                    .data("{}")
                    .build();
        }
    }

    /**
     * 构建错误事件流
     */
    private Flux<ServerSentEvent<String>> errorEvent(String message) {
        return Flux.just(
                buildSseEvent("error", Map.of("error", message)),
                ServerSentEvent.<String>builder().data("[DONE]").build()
        );
    }

    /**
     * 从 payload 获取字符串参数
     */
    private String getStringParam(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 截断消息用于日志
     */
    private String truncateMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 100 ? message.substring(0, 100) + "..." : message;
    }
}
