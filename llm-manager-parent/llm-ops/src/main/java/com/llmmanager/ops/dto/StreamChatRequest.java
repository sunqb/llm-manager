package com.llmmanager.ops.dto;

import lombok.Data;

import java.util.List;

/**
 * 统一流式对话请求
 *
 * 支持多种对话场景：
 * - 基础对话：仅需 message + (modelId 或 agentSlug)
 * - 工具对话：enableTools=true + toolNames
 * - 多模态对话：mediaUrls（图片URL列表）
 * - 思考模式：thinkingMode 控制深度思考
 *
 * 注意：reasoning 内容会自动返回（如果模型支持，如 DeepSeek R1）
 */
@Data
public class StreamChatRequest {

    /**
     * 用户消息（必填）
     */
    private String message;

    /**
     * 模型ID（与 agentSlug 二选一）
     */
    private Long modelId;

    /**
     * 智能体标识（与 modelId 二选一）
     */
    private String agentSlug;

    /**
     * 会话ID（可选，用于连续对话）
     */
    private String conversationId;

    /**
     * 是否启用工具调用（默认 false）
     */
    private Boolean enableTools = false;

    /**
     * 工具名称列表（可选，为空则使用所有工具）
     */
    private List<String> toolNames;

    /**
     * 媒体 URL 列表（图片等，可选）
     */
    private List<String> mediaUrls;

    /**
     * 思考模式（深度思考控制）
     *
     * 可选值取决于 reasoningFormat：
     * - DOUBAO 格式: enabled, disabled
     * - OPENAI 格式: low, medium, high
     * - auto: 不传递参数，让模型自行判断（默认）
     */
    private String thinkingMode;

    /**
     * Reasoning 参数格式（不同厂商使用不同的 API 格式）
     *
     * - DOUBAO: {"thinking": {"type": "enabled/disabled"}} - 豆包/火山引擎
     * - OPENAI: {"reasoning_effort": "low/medium/high"} - OpenAI o1/o3 系列
     * - DEEPSEEK: 无需额外参数，模型自动思考
     * - AUTO: 根据模型名称自动推断格式（默认）
     */
    private String reasoningFormat;
}
