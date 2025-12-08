package com.llmmanager.agent.storage.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.llmmanager.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP 服务器配置实体
 *
 * 支持三种传输方式：
 * - STDIO：本地进程通信（如 npx 启动的 MCP 服务器）
 * - SSE：Server-Sent Events（HTTP 长连接）
 * - STREAMABLE_HTTP：HTTP 流式传输
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "a_mcp_servers", autoResultMap = true)
public class McpServer extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 服务器唯一标识（32位UUID）
     */
    private String serverCode;

    /**
     * 服务器名称（用于显示）
     */
    private String name;

    /**
     * 服务器描述
     */
    private String description;

    /**
     * 传输类型：STDIO / SSE / STREAMABLE_HTTP
     */
    private String transportType;

    // ==================== STDIO 配置 ====================

    /**
     * STDIO: 执行命令（如 npx, node, python）
     */
    private String command;

    /**
     * STDIO: 命令参数列表（JSON 数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> args;

    /**
     * STDIO: 环境变量（JSON 对象）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> env;

    // ==================== SSE / HTTP 配置 ====================

    /**
     * SSE/HTTP: 服务器 URL（如 http://localhost:8080）
     */
    private String url;

    /**
     * SSE: SSE 端点路径（默认 /sse）
     */
    private String sseEndpoint;

    /**
     * HTTP: Streamable HTTP 端点路径（默认 /mcp）
     */
    private String httpEndpoint;

    // ==================== 通用配置 ====================

    /**
     * 请求超时时间（秒）
     */
    private Integer requestTimeout;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 排序权重（越小越靠前）
     */
    private Integer sortOrder;

    /**
     * 生成服务器唯一标识（32位无连字符的UUID）
     */
    public static String generateServerCode() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 创建 STDIO 类型服务器
     */
    public static McpServer createStdio(String name, String command, List<String> args) {
        McpServer server = new McpServer();
        server.setServerCode(generateServerCode());
        server.setName(name);
        server.setTransportType("STDIO");
        server.setCommand(command);
        server.setArgs(args);
        server.setEnabled(true);
        server.setSortOrder(0);
        return server;
    }

    /**
     * 创建 SSE 类型服务器
     */
    public static McpServer createSse(String name, String url) {
        McpServer server = new McpServer();
        server.setServerCode(generateServerCode());
        server.setName(name);
        server.setTransportType("SSE");
        server.setUrl(url);
        server.setSseEndpoint("/sse");
        server.setEnabled(true);
        server.setSortOrder(0);
        return server;
    }

    /**
     * 创建 Streamable HTTP 类型服务器
     */
    public static McpServer createStreamableHttp(String name, String url) {
        McpServer server = new McpServer();
        server.setServerCode(generateServerCode());
        server.setName(name);
        server.setTransportType("STREAMABLE_HTTP");
        server.setUrl(url);
        server.setHttpEndpoint("/mcp");
        server.setEnabled(true);
        server.setSortOrder(0);
        return server;
    }

    /**
     * 是否为 STDIO 类型
     */
    public boolean isStdio() {
        return "STDIO".equals(transportType);
    }

    /**
     * 是否为 SSE 类型
     */
    public boolean isSse() {
        return "SSE".equals(transportType);
    }

    /**
     * 是否为 Streamable HTTP 类型
     */
    public boolean isStreamableHttp() {
        return "STREAMABLE_HTTP".equals(transportType);
    }
}

