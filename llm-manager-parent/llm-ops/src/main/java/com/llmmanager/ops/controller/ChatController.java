package com.llmmanager.ops.controller;

import com.llmmanager.agent.config.ToolFunctionManager;
import com.llmmanager.agent.message.MediaMessage;
import com.llmmanager.agent.storage.core.service.MediaFileService;
import com.llmmanager.service.core.entity.Agent;
import com.llmmanager.service.core.service.AgentService;
import com.llmmanager.service.orchestration.LlmExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Resource
    private LlmExecutionService executionService;

    @Resource
    private AgentService agentService;

    @Resource
    private ToolFunctionManager toolFunctionManager;

    @Resource
    private MediaFileService mediaFileService;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @PostMapping("/{modelId}")
    public String chat(@PathVariable Long modelId, @RequestBody String message) {
        return executionService.chat(modelId, message);
    }

    /**
     * WebFlux 流式接口（推荐使用）
     *
     * @param conversationId 会话ID（前端传递，用于连续对话）
     */
    @PostMapping(value = "/{modelId}/stream-flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStreamFlux(
            @PathVariable Long modelId,
            @RequestBody String message,
            @RequestParam(required = false) String conversationId) {

        return executionService.streamChat(modelId, message, conversationId)
                .filter(content -> content != null && !content.isEmpty())
                .map(content -> {
                    String escapedContent = content.replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r");
                    String json = "{\"choices\":[{\"delta\":{\"content\":\"" + escapedContent + "\"}}]}";
                    return ServerSentEvent.<String>builder()
                            .data(json)
                            .build();
                })
                .concatWith(Flux.just(
                        ServerSentEvent.<String>builder()
                                .data("[DONE]")
                                .build()
                ));
    }

    /**
     * WebFlux 流式接口（支持 reasoning 内容）
     *
     * @param conversationId 会话ID（前端传递，用于连续对话）
     */
    @PostMapping(value = "/{modelId}/stream-with-reasoning", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStreamWithReasoning(
            @PathVariable Long modelId,
            @RequestBody String message,
            @RequestParam(required = false) String conversationId) {

        return executionService.streamChatResponse(modelId, message, conversationId)
                .mapNotNull(response -> {
                    if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                        return null;
                    }

                    var result = response.getResult();
                    if (result == null || result.getOutput() == null) {
                        return null;
                    }

                    var output = result.getOutput();
                    String content = output.getText();

                    // 尝试获取 reasoning 内容（如果模型支持）
                    // Spring AI 可能将其存储在 metadata 中
                    String reasoning = null;
                    if (output.getMetadata() != null) {
                        Object reasoningObj = output.getMetadata().get("reasoning_content");
                        if (reasoningObj != null) {
                            reasoning = reasoningObj.toString();
                        }
                    }

                    // 构建响应JSON
                    StringBuilder jsonBuilder = new StringBuilder("{\"choices\":[{\"delta\":{");

                    if (reasoning != null && !reasoning.isEmpty()) {
                        String escapedReasoning = reasoning.replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\n", "\\n")
                                .replace("\r", "\\r");
                        jsonBuilder.append("\"reasoning_content\":\"").append(escapedReasoning).append("\"");

                        if (content != null && !content.isEmpty()) {
                            jsonBuilder.append(",");
                        }
                    }

                    if (content != null && !content.isEmpty()) {
                        String escapedContent = content.replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\n", "\\n")
                                .replace("\r", "\\r");
                        jsonBuilder.append("\"content\":\"").append(escapedContent).append("\"");
                    }

                    jsonBuilder.append("}}]}");

                    return ServerSentEvent.<String>builder()
                            .data(jsonBuilder.toString())
                            .build();
                })
                .concatWith(Flux.just(
                        ServerSentEvent.<String>builder()
                                .data("[DONE]")
                                .build()
                ));
    }

    /**
     * SseEmitter 流式接口（兼容性）
     *
     * @param conversationId 会话ID（前端传递，用于连续对话）
     */
    @PostMapping(value = "/{modelId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @PathVariable Long modelId,
            @RequestBody String message,
            @RequestParam(required = false) String conversationId) {

        SseEmitter emitter = new SseEmitter(0L);  // 0 表示无超时
        emitter.onTimeout(emitter::complete);

        executorService.execute(() -> {
            try {
                executionService.streamChat(modelId, message, conversationId)
                        .filter(content -> content != null && !content.isEmpty())
                        .subscribe(
                                content -> {
                                    try {
                                        String escapedContent = content.replace("\\", "\\\\")
                                                .replace("\"", "\\\"")
                                                .replace("\n", "\\n")
                                                .replace("\r", "\\r");
                                        String json = "{\"choices\":[{\"delta\":{\"content\":\"" + escapedContent + "\"}}]}";

                                        emitter.send(SseEmitter.event().data(json));
                                    } catch (Exception e) {
                                        emitter.completeWithError(e);
                                    }
                                },
                                error -> {
                                    error.printStackTrace();
                                    emitter.completeWithError(error);
                                },
                                () -> {
                                    try {
                                        emitter.send(SseEmitter.event().data("[DONE]"));
                                        emitter.complete();
                                    } catch (Exception e) {
                                        emitter.completeWithError(e);
                                    }
                                }
                        );
            } catch (Exception e) {
                e.printStackTrace();
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @PostMapping("/{modelId}/template")
    public String chatWithTemplate(@PathVariable Long modelId,
                                   @RequestParam String templateContent,
                                   @RequestBody Map<String, Object> variables) {
        return executionService.chatWithTemplate(modelId, templateContent, variables);
    }

    /**
     * 获取所有可用的工具列表（供前端展示和选择）
     *
     * @return 工具列表 {name -> description}
     */
    @GetMapping("/tools")
    public Map<String, Object> getAvailableTools() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("tools", toolFunctionManager.getAllTools());
        return response;
    }

    /**
     * 带工具调用的对话接口（使用 Spring AI 原生 Function Calling）
     *
     * 工作流程（AI 自动决策）：
     * 1. 用户发送消息
     * 2. LLM 自动判断是否需要调用工具（无需手动正则匹配）
     * 3. 如果需要，LLM 自动调用已注册的工具函数
     * 4. LLM 基于工具返回结果生成最终回复
     *
     * 关键：将工具函数注册到 ChatClient，让 AI 自己决定何时调用
     *
     * @param modelId        模型ID
     * @param message        用户消息
     * @param conversationId 会话ID（可选）
     * @param toolNames      指定工具列表（可选，逗号分隔，null 表示使用所有工具）
     * @return LLM 回复
     */
    @PostMapping("/{modelId}/with-tools")
    public Map<String, Object> chatWithTools(
            @PathVariable Long modelId,
            @RequestBody String message,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) List<String> toolNames) {

        Map<String, Object> response = new HashMap<>();

        try {
            // 调用带工具支持的对话服务
            // LLM 会自动判断是否需要调用工具
            String llmResponse = executionService.chatWithTools(modelId, message, conversationId, toolNames);

            response.put("success", true);
            response.put("message", llmResponse);
            response.put("toolsUsed", toolNames != null ? toolNames : toolFunctionManager.getAllToolNames());

            return response;

        } catch (Exception e) {
            log.error("[ChatController] 工具调用对话失败", e);
            response.put("success", false);
            response.put("error", "对话失败: " + e.getMessage());
            return response;
        }
    }

    /**
     * 带工具调用的流式对话接口（使用 Spring AI 原生 Function Calling）
     *
     * @param modelId        模型ID
     * @param message        用户消息
     * @param conversationId 会话ID（可选）
     * @param toolNames      指定工具列表（可选，逗号分隔，null 表示使用所有工具）
     * @return 流式响应
     */
    @PostMapping(value = "/{modelId}/with-tools/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatWithToolsStream(
            @PathVariable Long modelId,
            @RequestBody String message,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) List<String> toolNames) {

        // 调用带工具支持的流式对话服务
        // LLM 会自动判断是否需要调用工具
        return executionService.streamChatWithTools(modelId, message, conversationId, toolNames)
                .filter(content -> content != null && !content.isEmpty())
                .map(content -> {
                    String escapedContent = content.replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r");
                    String json = "{\"choices\":[{\"delta\":{\"content\":\"" + escapedContent + "\"}}]}";
                    return ServerSentEvent.<String>builder()
                            .data(json)
                            .build();
                })
                .concatWith(Flux.just(ServerSentEvent.<String>builder().data("[DONE]").build()));
    }

    /**
     * 智能体流式对话接口（内部管理后台使用）
     *
     * 功能说明：
     * - 根据 slug 查询业务智能体配置（Agent）
     * - 使用智能体关联的模型、系统提示词等配置
     * - 支持会话历史记忆（conversationId）
     * - 返回流式响应
     *
     * @param slug           智能体标识（Agent.slug）
     * @param message        用户消息
     * @param conversationId 会话ID（可选，用于连续对话）
     * @return 流式响应
     */
    @PostMapping(value = "/agents/{slug}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatWithAgentStream(
            @PathVariable String slug,
            @RequestBody String message,
            @RequestParam(required = false) String conversationId) {

        log.info("[ChatController] 智能体流式对话请求，slug: {}, conversationId: {}", slug, conversationId);

        // 1. 根据 slug 查询智能体配置
        Agent agent = agentService.findBySlug(slug);
        if (agent == null) {
            log.error("[ChatController] 智能体不存在，slug: {}", slug);
            return Flux.just(ServerSentEvent.<String>builder()
                    .data("{\"error\":\"智能体不存在: " + slug + "\"}")
                    .build());
        }

        // 2. 调用流式对话服务（支持会话历史）
        return executionService.streamChatWithAgent(agent, message, conversationId)
                .filter(content -> content != null && !content.isEmpty())
                .map(content -> {
                    String escapedContent = content.replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r");
                    String json = "{\"choices\":[{\"delta\":{\"content\":\"" + escapedContent + "\"}}]}";
                    return ServerSentEvent.<String>builder()
                            .data(json)
                            .build();
                })
                .concatWith(Flux.just(
                        ServerSentEvent.<String>builder()
                                .data("[DONE]")
                                .build()
                ))
                .doOnError(error -> log.error("[ChatController] 智能体流式对话失败，slug: {}, error: {}",
                        slug, error.getMessage(), error));
    }

    // ==================== 多模态对话接口 ====================

    /**
     * 图片对话接口（通过 URL）
     *
     * 使用场景：传递图片 URL 让 LLM 分析图片内容
     * 支持模型：GPT-4V、Claude 3 等支持视觉的模型
     *
     * @param modelId        模型ID（需要支持视觉的模型，如 gpt-4o）
     * @param message        用户问题
     * @param imageUrls      图片 URL 列表（逗号分隔）
     * @param conversationId 会话ID（可选）
     * @return 流式响应
     */
    @PostMapping(value = "/{modelId}/with-image-url", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatWithImageUrl(
            @PathVariable Long modelId,
            @RequestParam String message,
            @RequestParam List<String> imageUrls,
            @RequestParam(required = false) String conversationId) {

        log.info("[ChatController] 图片对话请求（URL），模型: {}, 图片数: {}, 会话: {}",
                modelId, imageUrls.size(), conversationId);

        // 过滤有效的图片URL
        List<String> validImageUrls = imageUrls.stream()
                .filter(url -> url != null && !url.trim().isEmpty())
                .map(String::trim)
                .toList();

        // 构建媒体内容列表
        List<MediaMessage.MediaContent> mediaContents = new ArrayList<>();
        for (String imageUrl : validImageUrls) {
            mediaContents.add(MediaMessage.MediaContent.ofImageUrl(imageUrl));
        }

        return executionService.streamChatWithMedia(modelId, message, mediaContents, conversationId)
                .filter(content -> content != null && !content.isEmpty())
                .map(content -> {
                    String escapedContent = content.replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r");
                    String json = "{\"choices\":[{\"delta\":{\"content\":\"" + escapedContent + "\"}}]}";
                    return ServerSentEvent.<String>builder().data(json).build();
                })
                .concatWith(Flux.just(ServerSentEvent.<String>builder().data("[DONE]").build()))
                .doOnComplete(() -> {
                    // 流完成后保存图片URL到数据库
                    if (conversationId != null && !validImageUrls.isEmpty()) {
                        try {
                            mediaFileService.saveImageUrlsForLatestUserMessage(conversationId, validImageUrls);
                            log.info("[ChatController] 已保存图片URL到数据库，会话: {}, 图片数: {}",
                                    conversationId, validImageUrls.size());
                        } catch (Exception e) {
                            log.error("[ChatController] 保存图片URL失败: {}", e.getMessage(), e);
                        }
                    }
                })
                .doOnError(error -> log.error("[ChatController] 图片对话失败: {}", error.getMessage(), error));
    }

    /**
     * 图片对话接口（通过文件上传）
     *
     * 使用场景：上传本地图片文件让 LLM 分析
     * 支持模型：GPT-4V、Claude 3 等支持视觉的模型
     *
     * @param modelId        模型ID（需要支持视觉的模型，如 gpt-4o）
     * @param message        用户问题
     * @param images         图片文件列表
     * @param conversationId 会话ID（可选）
     * @return 流式响应
     */
    @PostMapping(value = "/{modelId}/with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatWithImage(
            @PathVariable Long modelId,
            @RequestParam String message,
            @RequestParam("images") List<MultipartFile> images,
            @RequestParam(required = false) String conversationId) {

        log.info("[ChatController] 图片对话请求（上传），模型: {}, 图片数: {}", modelId, images.size());

        // 构建媒体内容列表
        List<MediaMessage.MediaContent> mediaContents = new ArrayList<>();
        for (MultipartFile image : images) {
            if (image != null && !image.isEmpty()) {
                try {
                    byte[] imageData = image.getBytes();
                    String mimeType = image.getContentType();
                    if (mimeType == null) {
                        mimeType = "image/png";  // 默认 PNG
                    }
                    mediaContents.add(MediaMessage.MediaContent.ofImageData(imageData, mimeType));
                    log.debug("[ChatController] 处理图片: {}, 大小: {} bytes, MIME: {}",
                            image.getOriginalFilename(), imageData.length, mimeType);
                } catch (IOException e) {
                    log.error("[ChatController] 读取图片失败: {}", image.getOriginalFilename(), e);
                }
            }
        }

        if (mediaContents.isEmpty()) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .data("{\"error\":\"没有有效的图片\"}")
                    .build());
        }

        return executionService.streamChatWithMedia(modelId, message, mediaContents, conversationId)
                .filter(content -> content != null && !content.isEmpty())
                .map(content -> {
                    String escapedContent = content.replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r");
                    String json = "{\"choices\":[{\"delta\":{\"content\":\"" + escapedContent + "\"}}]}";
                    return ServerSentEvent.<String>builder().data(json).build();
                })
                .concatWith(Flux.just(ServerSentEvent.<String>builder().data("[DONE]").build()))
                .doOnError(error -> log.error("[ChatController] 图片对话失败: {}", error.getMessage(), error));
    }

    /**
     * 同步图片对话接口（通过 URL）
     *
     * @param modelId        模型ID
     * @param message        用户问题
     * @param imageUrls      图片 URL 列表
     * @param conversationId 会话ID（可选）
     * @return 回复内容
     */
    @PostMapping("/{modelId}/with-image-url/sync")
    public Map<String, Object> chatWithImageUrlSync(
            @PathVariable Long modelId,
            @RequestParam String message,
            @RequestParam List<String> imageUrls,
            @RequestParam(required = false) String conversationId) {

        log.info("[ChatController] 同步图片对话请求，模型: {}, 图片数: {}", modelId, imageUrls.size());

        // 过滤有效的图片URL
        List<String> validImageUrls = imageUrls.stream()
                .filter(url -> url != null && !url.trim().isEmpty())
                .map(String::trim)
                .toList();

        Map<String, Object> response = new HashMap<>();
        try {
            // 构建媒体内容列表
            List<MediaMessage.MediaContent> mediaContents = new ArrayList<>();
            for (String imageUrl : validImageUrls) {
                mediaContents.add(MediaMessage.MediaContent.ofImageUrl(imageUrl));
            }

            String result = executionService.chatWithMedia(modelId, message, mediaContents, conversationId);
            response.put("success", true);
            response.put("message", result);

            // 对话成功后保存图片URL到数据库
            if (conversationId != null && !validImageUrls.isEmpty()) {
                try {
                    mediaFileService.saveImageUrlsForLatestUserMessage(conversationId, validImageUrls);
                    log.info("[ChatController] 已保存图片URL到数据库，会话: {}, 图片数: {}",
                            conversationId, validImageUrls.size());
                } catch (Exception e) {
                    log.error("[ChatController] 保存图片URL失败: {}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("[ChatController] 同步图片对话失败", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return response;
    }

    // ==================== 文件对话接口 ====================

    /**
     * 文件对话接口（将文件内容作为上下文）
     *
     * 使用场景：上传文本文件（txt、md、json、代码文件等），将内容作为上下文让 LLM 分析
     * 注意：这不是多模态，而是将文件内容提取后作为文本上下文
     *
     * @param modelId        模型ID
     * @param message        用户问题
     * @param files          文件列表
     * @param conversationId 会话ID（可选）
     * @return 流式响应
     */
    @PostMapping(value = "/{modelId}/with-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatWithFile(
            @PathVariable Long modelId,
            @RequestParam String message,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(required = false) String conversationId) {

        log.info("[ChatController] 文件对话请求，模型: {}, 文件数: {}", modelId, files.size());

        // 构建包含文件内容的消息
        StringBuilder contextBuilder = new StringBuilder();

        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                try {
                    String fileName = file.getOriginalFilename();
                    String content = new String(file.getBytes(), StandardCharsets.UTF_8);

                    contextBuilder.append("\n--- 文件: ").append(fileName).append(" ---\n");
                    contextBuilder.append(content);
                    contextBuilder.append("\n--- 文件结束 ---\n\n");

                    log.debug("[ChatController] 读取文件: {}, 大小: {} bytes", fileName, content.length());
                } catch (IOException e) {
                    log.error("[ChatController] 读取文件失败: {}", file.getOriginalFilename(), e);
                }
            }
        }

        if (contextBuilder.isEmpty()) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .data("{\"error\":\"没有有效的文件内容\"}")
                    .build());
        }

        // 将文件内容作为上下文，与用户消息组合
        String fullMessage = "以下是相关文件内容，请根据这些内容回答问题：\n"
                + contextBuilder
                + "\n用户问题: " + message;

        return executionService.streamChat(modelId, fullMessage, conversationId)
                .filter(content -> content != null && !content.isEmpty())
                .map(content -> {
                    String escapedContent = content.replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r");
                    String json = "{\"choices\":[{\"delta\":{\"content\":\"" + escapedContent + "\"}}]}";
                    return ServerSentEvent.<String>builder().data(json).build();
                })
                .concatWith(Flux.just(ServerSentEvent.<String>builder().data("[DONE]").build()))
                .doOnError(error -> log.error("[ChatController] 文件对话失败: {}", error.getMessage(), error));
    }

    /**
     * 同步文件对话接口
     *
     * @param modelId        模型ID
     * @param message        用户问题
     * @param files          文件列表
     * @param conversationId 会话ID（可选）
     * @return 回复内容
     */
    @PostMapping(value = "/{modelId}/with-file/sync", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> chatWithFileSync(
            @PathVariable Long modelId,
            @RequestParam String message,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(required = false) String conversationId) {

        log.info("[ChatController] 同步文件对话请求，模型: {}, 文件数: {}", modelId, files.size());

        Map<String, Object> response = new HashMap<>();
        try {
            // 构建包含文件内容的消息
            StringBuilder contextBuilder = new StringBuilder();

            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    String fileName = file.getOriginalFilename();
                    String content = new String(file.getBytes(), StandardCharsets.UTF_8);

                    contextBuilder.append("\n--- 文件: ").append(fileName).append(" ---\n");
                    contextBuilder.append(content);
                    contextBuilder.append("\n--- 文件结束 ---\n\n");
                }
            }

            if (contextBuilder.isEmpty()) {
                response.put("success", false);
                response.put("error", "没有有效的文件内容");
                return response;
            }

            // 将文件内容作为上下文
            String fullMessage = "以下是相关文件内容，请根据这些内容回答问题：\n"
                    + contextBuilder
                    + "\n用户问题: " + message;

            // 使用普通流式接口获取结果
            StringBuilder resultBuilder = new StringBuilder();
            executionService.streamChat(modelId, fullMessage, conversationId)
                    .toStream()
                    .forEach(resultBuilder::append);

            response.put("success", true);
            response.put("message", resultBuilder.toString());
        } catch (Exception e) {
            log.error("[ChatController] 同步文件对话失败", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return response;
    }
}

