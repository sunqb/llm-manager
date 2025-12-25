package com.llmmanager.openapi.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容的 Chat Completion 请求
 *
 * 参考：https://platform.openai.com/docs/api-reference/chat/create
 *
 * @author LLM Manager
 */
@Data
public class ChatCompletionRequest {

    /**
     * 模型标识
     *
     * 格式：
     * - react-agent/{slug}  - ReactAgent（支持 SINGLE/SEQUENTIAL/SUPERVISOR）
     * - agent/{slug}        - 普通 Agent
     * - {modelId}           - 直接使用模型 ID
     */
    private String model;

    /**
     * 消息列表
     */
    private List<ChatMessage> messages;

    /**
     * 是否流式输出
     */
    private Boolean stream = false;

    /**
     * 温度参数 (0-2)
     */
    private Double temperature;

    /**
     * Top P 参数
     */
    @JsonProperty("top_p")
    private Double topP;

    /**
     * 最大 Token 数
     */
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    /**
     * 停止词
     */
    private List<String> stop;

    /**
     * 用户标识
     */
    private String user;

    /**
     * 扩展参数
     */
    private Map<String, Object> extra;

    /**
     * 聊天消息
     */
    @Data
    public static class ChatMessage {
        /**
         * 角色：system, user, assistant
         */
        private String role;

        /**
         * 消息内容
         */
        private String content;

        /**
         * 消息名称（可选）
         */
        private String name;
    }

    /**
     * 获取用户消息内容
     */
    public String getUserMessage() {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        // 返回最后一条 user 消息
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if ("user".equalsIgnoreCase(msg.getRole())) {
                return msg.getContent();
            }
        }
        return null;
    }

    /**
     * 获取系统消息内容
     */
    public String getSystemMessage() {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (ChatMessage msg : messages) {
            if ("system".equalsIgnoreCase(msg.getRole())) {
                return msg.getContent();
            }
        }
        return null;
    }
}
