package com.llmmanager.agent.config;

import com.llmmanager.agent.advisor.AdvisorManager;
import com.llmmanager.agent.storage.memory.MybatisChatMemoryRepository;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 聊天记忆配置类（简化版 - 统一 Spring AI）
 */
@Configuration
@EnableConfigurationProperties({ChatMemoryProperties.class, McpClientProperties.class})
public class ChatMemoryConfig {

    /**
     * Spring AI ChatMemory
     */
    @Bean
    @ConditionalOnProperty(name = "llm.memory.enabled", havingValue = "true", matchIfMissing = true)
    public ChatMemory chatMemory(MybatisChatMemoryRepository repository, ChatMemoryProperties properties) {
        int maxMessages = properties.getMaxMessages();

        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(maxMessages)
                .build();
    }

    /**
     * MessageChatMemoryAdvisor
     * 注意：不自动注册到 AdvisorManager，避免所有请求都查询数据库
     * 只有在需要历史对话时，由 LlmChatAgent 按需使用
     */
    @Bean
    @ConditionalOnProperty(name = "llm.memory.enabled", havingValue = "true", matchIfMissing = true)
    public MessageChatMemoryAdvisor memoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }
}
