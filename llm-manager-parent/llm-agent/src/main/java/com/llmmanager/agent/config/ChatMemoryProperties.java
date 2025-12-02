package com.llmmanager.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 聊天记忆配置属性（简化版）
 */
@Data
@ConfigurationProperties(prefix = "llm.memory")
public class ChatMemoryProperties {

    /**
     * 是否启用历史记忆
     * 默认：true
     */
    private Boolean enabled = true;

    /**
     * 最大保留消息数量
     * 默认：10
     */
    private Integer maxMessages = 10;

    /**
     * 是否启用历史消息清理
     * 默认：false
     */
    private Boolean enableCleanup = false;

    /**
     * 历史消息保留天数（enableCleanup=true 时生效）
     * 默认：7天
     */
    private Integer retentionDays = 7;
}
