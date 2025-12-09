package com.llmmanager.agent.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.time.Duration;
import java.util.List;

/**
 * MCP SSE 连接测试
 * 
 * 用户提供的 MCP 服务器：
 * - type: sse
 * - url: https://mcp.api-inference.modelscope.net/487c560600b547/sse
 * 
 * SSE 流返回的消息端点格式：
 * event: endpoint
 * data: /messages/?session_id=xxx
 */
public class McpSseConnectionTest {

    public static void main(String[] args) {
        // 问题分析：
        // 服务器返回的消息端点 /messages/?session_id=xxx 是相对于服务器根的
        // 所以 baseUrl 必须是服务器根 URL，sseEndpoint 包含完整路径
        
        // 正确配置：
        // - baseUrl: 服务器根 URL
        // - sseEndpoint: 完整的 SSE 路径（包含 /487c560600b547/sse）
        String baseUrl = "https://mcp.api-inference.modelscope.net";
        String sseEndpoint = "/487c560600b547/sse";
        
        System.out.println("=== MCP SSE 连接测试 ===");
        System.out.println("Base URL: " + baseUrl);
        System.out.println("SSE Endpoint: " + sseEndpoint);
        System.out.println();

        try {
            // 创建 SSE 传输
            System.out.println("1. 创建 SSE Transport...");
            HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(baseUrl)
                .sseEndpoint(sseEndpoint)
                .build();

            // 创建同步客户端
            System.out.println("2. 创建 MCP Client...");
            McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(30))
                .build();

            // 初始化客户端
            System.out.println("3. 初始化客户端...");
            client.initialize();
            System.out.println("   [OK] 客户端初始化成功！");

            // 列出可用工具
            System.out.println("4. 获取可用工具列表...");
            var toolsResult = client.listTools();
            List<McpSchema.Tool> tools = toolsResult.tools();

            System.out.println("   [OK] 发现 " + tools.size() + " 个工具:");
            for (McpSchema.Tool tool : tools) {
                System.out.println("   - " + tool.name() + ": " + tool.description());
            }

            // 如果有工具，可以尝试调用一个
            if (!tools.isEmpty()) {
                System.out.println();
                System.out.println("5. 测试工具调用...");
                McpSchema.Tool firstTool = tools.get(0);
                System.out.println("   选择工具: " + firstTool.name());
                
                // 这里不实际调用，只显示工具的 schema
                System.out.println("   工具 Schema: " + firstTool.inputSchema());
            }

            // 关闭连接
            System.out.println();
            System.out.println("6. 关闭连接...");
            client.close();
            System.out.println("   [OK] 连接已关闭");

            System.out.println();
            System.out.println("=== 测试完成：SUCCESS ===");

        } catch (Exception e) {
            System.err.println();
            System.err.println("=== 测试失败：ERROR ===");
            System.err.println("错误类型: " + e.getClass().getSimpleName());
            System.err.println("错误信息: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
