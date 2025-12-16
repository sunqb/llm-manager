package com.llmmanager.agent.rag.config;

import com.llmmanager.agent.rag.VectorStoreManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * RAG 配置类
 *
 * 配置 Embedding 模型和 VectorStore。
 * 仅在 llm.rag.enabled=true 时启用。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(RagProperties.class)
@ConditionalOnProperty(name = "llm.rag.enabled", havingValue = "true", matchIfMissing = false)
public class RagConfig {

    @Value("${spring.ai.openai.api-key:}")
    private String defaultApiKey;

    @Value("${spring.ai.openai.base-url:https://api.openai.com}")
    private String defaultBaseUrl;

    /**
     * 创建 Embedding 模型
     *
     * 优先使用 llm.rag.embedding 配置，否则使用 spring.ai.openai 配置。
     * 支持任何兼容 OpenAI API 的 Embedding 服务（OpenAI、Ollama、Azure OpenAI 等）。
     */
    @Bean
    @ConditionalOnMissingBean(EmbeddingModel.class)
    public EmbeddingModel embeddingModel(RagProperties ragProperties) {
        RagProperties.EmbeddingConfig embeddingConfig = ragProperties.getEmbedding();

        // 优先使用 RAG 配置，否则使用默认配置
        String apiKey = StringUtils.hasText(embeddingConfig.getApiKey())
                ? embeddingConfig.getApiKey() : defaultApiKey;
        String baseUrl = StringUtils.hasText(embeddingConfig.getBaseUrl())
                ? embeddingConfig.getBaseUrl() : defaultBaseUrl;
        String model = embeddingConfig.getModel();

        log.info("[RagConfig] 创建 Embedding 模型: baseUrl={}, model={}, dimensions={}",
                baseUrl, model, embeddingConfig.getDimensions());

        // 创建 OpenAI API（兼容其他服务）
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        // 创建 Embedding 模型
        return new OpenAiEmbeddingModel(openAiApi);
    }

    /**
     * 创建 VectorStore 管理器
     *
     * VectorStoreManager 已经是 @Component，这里只是确保配置生效
     */
    @Bean
    @ConditionalOnMissingBean(VectorStoreManager.class)
    public VectorStoreManager vectorStoreManager() {
        return new VectorStoreManager();
    }
}
