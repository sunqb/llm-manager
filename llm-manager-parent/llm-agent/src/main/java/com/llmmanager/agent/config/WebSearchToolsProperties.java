package com.llmmanager.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 联网搜索工具配置属性
 */
@Data
@ConfigurationProperties(prefix = "llm.tools.web-search")
public class WebSearchToolsProperties {

    /**
     * 是否启用联网搜索工具
     * 默认：false（避免默认外网访问）
     */
    private Boolean enabled = false;

    /**
     * 搜索服务 Base URL
     * <p>
     * 推荐使用自建 SearxNG（支持 format=json）：
     * - 示例：http://localhost:8088
     * </p>
     */
    private String baseUrl;

    /**
     * 默认超时（秒）
     * 默认：20
     */
    private Integer timeoutSeconds = 20;

    /**
     * 默认返回条数
     * 默认：5
     */
    private Integer defaultLimit = 5;

    /**
     * 默认语言（SearxNG: language 参数）
     * 默认：zh-CN
     */
    private String language = "zh-CN";

    /**
     * SafeSearch（SearxNG: safesearch 参数：0/1/2）
     * 默认：1（中等）
     */
    private Integer safeSearch = 1;
}

