package com.llmmanager.agent.model;

import com.llmmanager.agent.message.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 聊天响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /**
     * 响应消息
     */
    private Message message;

    /**
     * 模型标识符
     */
    private String model;

    /**
     * 生成的 token 数
     */
    private Integer tokensUsed;

    /**
     * 完成原因
     * stop: 正常完成
     * length: 达到最大长度
     * content_filter: 内容过滤
     */
    private String finishReason;

    /**
     * 响应时间
     */
    private LocalDateTime timestamp;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    /**
     * 创建简单响应
     */
    public static ChatResponse of(Message message) {
        return ChatResponse.builder()
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
