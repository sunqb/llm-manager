package com.llmmanager.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP 客户端配置属性
 */
@Data
@ConfigurationProperties(prefix = "llm.mcp")
public class McpClientProperties {

    /**
     * 是否启用 MCP 功能
     */
    private boolean enabled = true;

    /**
     * 默认请求超时时间（秒）
     */
    private int requestTimeout = 30;

    /**
     * 是否在启动时自动初始化客户端
     */
    private boolean autoInitialize = true;

    /**
     * 客户端类型：SYNC 或 ASYNC
     */
    private String clientType = "SYNC";
}

