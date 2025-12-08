package com.llmmanager.agent.mcp;

import com.llmmanager.agent.config.McpClientProperties;
import com.llmmanager.agent.storage.core.entity.McpServer;
import com.llmmanager.agent.storage.core.service.McpServerService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 客户端管理器
 * 
 * 负责管理 MCP 服务器连接和工具发现
 */
@Slf4j
@Component
public class McpClientManager {

    @Resource
    private McpServerService mcpServerService;

    @Resource
    private McpClientProperties mcpClientProperties;

    /**
     * 已连接的 MCP 客户端缓存
     * Key: serverCode
     */
    private final Map<String, McpSyncClient> clientCache = new ConcurrentHashMap<>();

    /**
     * 工具回调提供者缓存
     */
    private final Map<String, SyncMcpToolCallbackProvider> toolCallbackProviderCache = new ConcurrentHashMap<>();

    /**
     * 启动时自动初始化
     */
    @PostConstruct
    public void init() {
        if (!mcpClientProperties.isEnabled()) {
            log.info("[MCP] MCP 功能已禁用");
            return;
        }

        if (mcpClientProperties.isAutoInitialize()) {
            log.info("[MCP] 开始自动初始化 MCP 客户端...");
            initializeAllClients();
        }
    }

    /**
     * 初始化所有启用的 MCP 服务器客户端
     */
    public void initializeAllClients() {
        List<McpServer> servers = mcpServerService.getEnabledServers();
        log.info("[MCP] 发现 {} 个启用的 MCP 服务器", servers.size());

        for (McpServer server : servers) {
            try {
                initializeClient(server);
            } catch (Exception e) {
                log.error("[MCP] 初始化服务器 {} 失败: {}", server.getName(), e.getMessage(), e);
            }
        }
    }

    /**
     * 初始化单个 MCP 客户端
     */
    public McpSyncClient initializeClient(McpServer server) {
        String serverCode = server.getServerCode();

        // 检查是否已存在
        if (clientCache.containsKey(serverCode)) {
            log.debug("[MCP] 服务器 {} 已初始化，跳过", server.getName());
            return clientCache.get(serverCode);
        }

        log.info("[MCP] 正在初始化服务器: {} ({})", server.getName(), server.getTransportType());

        McpSyncClient client = createClient(server);
        if (client != null) {
            clientCache.put(serverCode, client);
            log.info("[MCP] 服务器 {} 初始化成功", server.getName());

            // 列出可用工具
            listServerTools(server.getName(), client);
        }

        return client;
    }

    /**
     * 创建 MCP 客户端
     */
    private McpSyncClient createClient(McpServer server) {
        int timeout = server.getRequestTimeout() != null 
            ? server.getRequestTimeout() 
            : mcpClientProperties.getRequestTimeout();

        try {
            if (server.isStdio()) {
                return createStdioClient(server, timeout);
            } else if (server.isSse()) {
                return createSseClient(server, timeout);
            } else if (server.isStreamableHttp()) {
                return createStreamableHttpClient(server, timeout);
            } else {
                log.warn("[MCP] 不支持的传输类型: {}", server.getTransportType());
                return null;
            }
        } catch (Exception e) {
            log.error("[MCP] 创建客户端失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 创建 STDIO 客户端
     * 注意：STDIO 传输需要额外的依赖配置，当前版本暂不支持
     * 推荐使用 SSE 或 Streamable HTTP 传输
     */
    private McpSyncClient createStdioClient(McpServer server, int timeout) {
        log.warn("[MCP] STDIO 传输类型当前版本暂不支持，请使用 SSE 或 STREAMABLE_HTTP 传输。服务器: {}", server.getName());
        log.info("[MCP] 提示：可以将 MCP 服务器配置为 SSE 模式，例如使用 npx @anthropic-ai/mcp-server-sse 包装 STDIO 服务器");
        return null;
    }

    /**
     * 创建 SSE 客户端
     */
    private McpSyncClient createSseClient(McpServer server, int timeout) {
        if (!StringUtils.hasText(server.getUrl())) {
            log.error("[MCP] SSE 服务器 {} 缺少 url 配置", server.getName());
            return null;
        }

        String sseEndpoint = StringUtils.hasText(server.getSseEndpoint())
            ? server.getSseEndpoint()
            : "/sse";

        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(server.getUrl())
            .sseEndpoint(sseEndpoint)
            .build();

        McpSyncClient client = McpClient.sync(transport)
            .requestTimeout(Duration.ofSeconds(timeout))
            .build();

        // 初始化客户端
        client.initialize();
        return client;
    }

    /**
     * 创建 Streamable HTTP 客户端
     */
    private McpSyncClient createStreamableHttpClient(McpServer server, int timeout) {
        if (!StringUtils.hasText(server.getUrl())) {
            log.error("[MCP] Streamable HTTP 服务器 {} 缺少 url 配置", server.getName());
            return null;
        }

        // Streamable HTTP 使用相同的 SSE 传输，但端点不同
        String httpEndpoint = StringUtils.hasText(server.getHttpEndpoint())
            ? server.getHttpEndpoint()
            : "/mcp";

        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(server.getUrl())
            .sseEndpoint(httpEndpoint)
            .build();

        McpSyncClient client = McpClient.sync(transport)
            .requestTimeout(Duration.ofSeconds(timeout))
            .build();

        // 初始化客户端
        client.initialize();
        return client;
    }

    /**
     * 列出服务器可用工具
     */
    private void listServerTools(String serverName, McpSyncClient client) {
        try {
            var toolsResult = client.listTools();
            List<McpSchema.Tool> tools = toolsResult.tools();
            log.info("[MCP] 服务器 {} 提供 {} 个工具:", serverName, tools.size());
            for (McpSchema.Tool tool : tools) {
                log.info("[MCP]   - {} : {}", tool.name(), tool.description());
            }
        } catch (Exception e) {
            log.warn("[MCP] 获取服务器 {} 工具列表失败: {}", serverName, e.getMessage());
        }
    }

    /**
     * 获取所有 MCP 工具回调
     */
    public ToolCallback[] getAllToolCallbacks() {
        List<ToolCallback> allCallbacks = new ArrayList<>();

        for (Map.Entry<String, McpSyncClient> entry : clientCache.entrySet()) {
            try {
                SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(entry.getValue());
                ToolCallback[] callbacks = provider.getToolCallbacks();
                for (ToolCallback callback : callbacks) {
                    allCallbacks.add(callback);
                }
            } catch (Exception e) {
                log.warn("[MCP] 获取服务器 {} 工具回调失败: {}", entry.getKey(), e.getMessage());
            }
        }

        return allCallbacks.toArray(new ToolCallback[0]);
    }

    /**
     * 获取指定服务器的工具回调
     */
    public ToolCallback[] getToolCallbacks(String serverCode) {
        McpSyncClient client = clientCache.get(serverCode);
        if (client == null) {
            log.warn("[MCP] 服务器 {} 未初始化", serverCode);
            return new ToolCallback[0];
        }

        try {
            SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(client);
            return provider.getToolCallbacks();
        } catch (Exception e) {
            log.error("[MCP] 获取服务器 {} 工具回调失败: {}", serverCode, e.getMessage());
            return new ToolCallback[0];
        }
    }

    /**
     * 获取所有已连接的客户端
     */
    public List<McpSyncClient> getAllClients() {
        return new ArrayList<>(clientCache.values());
    }

    /**
     * 获取指定服务器的客户端
     */
    public McpSyncClient getClient(String serverCode) {
        return clientCache.get(serverCode);
    }

    /**
     * 断开指定服务器连接
     */
    public void disconnectClient(String serverCode) {
        McpSyncClient client = clientCache.remove(serverCode);
        if (client != null) {
            try {
                client.close();
                log.info("[MCP] 服务器 {} 已断开连接", serverCode);
            } catch (Exception e) {
                log.warn("[MCP] 断开服务器 {} 连接时出错: {}", serverCode, e.getMessage());
            }
        }
        toolCallbackProviderCache.remove(serverCode);
    }

    /**
     * 重新连接指定服务器
     */
    public void reconnectClient(String serverCode) {
        disconnectClient(serverCode);
        McpServer server = mcpServerService.getByServerCode(serverCode);
        if (server != null && Boolean.TRUE.equals(server.getEnabled())) {
            initializeClient(server);
        }
    }

    /**
     * 关闭所有连接
     */
    @PreDestroy
    public void shutdown() {
        log.info("[MCP] 正在关闭所有 MCP 连接...");
        for (Map.Entry<String, McpSyncClient> entry : clientCache.entrySet()) {
            try {
                entry.getValue().close();
            } catch (Exception e) {
                log.warn("[MCP] 关闭服务器 {} 连接时出错: {}", entry.getKey(), e.getMessage());
            }
        }
        clientCache.clear();
        toolCallbackProviderCache.clear();
        log.info("[MCP] 所有 MCP 连接已关闭");
    }

    /**
     * 检查是否为 Windows 系统
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * 获取已连接的服务器数量
     */
    public int getConnectedServerCount() {
        return clientCache.size();
    }

    /**
     * 检查服务器是否已连接
     */
    public boolean isConnected(String serverCode) {
        return clientCache.containsKey(serverCode);
    }
}

