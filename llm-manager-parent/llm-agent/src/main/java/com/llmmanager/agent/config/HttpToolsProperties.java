package com.llmmanager.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP 工具配置属性
 */
@Data
@ConfigurationProperties(prefix = "llm.tools.http")
public class HttpToolsProperties {

    /**
     * 是否启用 HTTP 工具
     * 默认：false（避免默认开放外网请求）
     */
    private Boolean enabled = false;

    /**
     * 允许访问的域名白名单
     * <p>
     * 支持：
     * - 精确匹配：example.com
     * - 通配子域：*.example.com
     * </p>
     * 为空表示不允许访问任何外部域名（需要显式配置）。
     */
    private List<String> allowHosts = new ArrayList<>();

    /**
     * 是否允许访问私网/本机地址（SSRF 风险）
     * 默认：false
     */
    private Boolean allowPrivateNetwork = false;

    /**
     * 默认请求超时（秒）
     * 默认：20
     */
    private Integer defaultTimeoutSeconds = 20;

    /**
     * 最大响应字节数（超过则截断）
     * 默认：1MB
     */
    private Integer maxResponseBytes = 1024 * 1024;

    /**
     * User-Agent
     */
    private String userAgent = "LLM-Manager/1.0";
}

