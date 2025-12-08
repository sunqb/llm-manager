package com.llmmanager.ops.controller;

import com.llmmanager.agent.mcp.McpClientManager;
import com.llmmanager.agent.storage.core.entity.McpServer;
import com.llmmanager.agent.storage.core.service.McpServerService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP 服务器管理 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/mcp-servers")
public class McpServerController {

    @Resource
    private McpServerService mcpServerService;

    @Resource
    private McpClientManager mcpClientManager;

    /**
     * 获取所有 MCP 服务器
     */
    @GetMapping
    public List<McpServer> getAll() {
        return mcpServerService.list();
    }

    /**
     * 获取已启用的 MCP 服务器
     */
    @GetMapping("/enabled")
    public List<McpServer> getEnabled() {
        return mcpServerService.getEnabledServers();
    }

    /**
     * 根据 ID 获取 MCP 服务器
     */
    @GetMapping("/{id}")
    public McpServer getById(@PathVariable Long id) {
        McpServer server = mcpServerService.getById(id);
        if (server == null) {
            throw new RuntimeException("MCP 服务器不存在: " + id);
        }
        return server;
    }

    /**
     * 根据 serverCode 获取 MCP 服务器
     */
    @GetMapping("/code/{serverCode}")
    public McpServer getByCode(@PathVariable String serverCode) {
        McpServer server = mcpServerService.getByServerCode(serverCode);
        if (server == null) {
            throw new RuntimeException("MCP 服务器不存在: " + serverCode);
        }
        return server;
    }

    /**
     * 创建 MCP 服务器
     */
    @PostMapping
    public McpServer create(@RequestBody McpServer server) {
        mcpServerService.save(server);
        return server;
    }

    /**
     * 更新 MCP 服务器
     */
    @PutMapping("/{id}")
    public McpServer update(@PathVariable Long id, @RequestBody McpServer updated) {
        McpServer existing = getById(id);
        updated.setId(id);
        mcpServerService.updateById(updated);
        
        // 如果服务器已连接，重新连接以应用新配置
        if (mcpClientManager.isConnected(existing.getServerCode())) {
            mcpClientManager.reconnectClient(existing.getServerCode());
        }
        
        return mcpServerService.getById(id);
    }

    /**
     * 删除 MCP 服务器
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        McpServer server = getById(id);
        // 先断开连接
        if (mcpClientManager.isConnected(server.getServerCode())) {
            mcpClientManager.disconnectClient(server.getServerCode());
        }
        mcpServerService.removeById(id);
    }

    /**
     * 连接 MCP 服务器
     */
    @PostMapping("/{id}/connect")
    public Map<String, Object> connect(@PathVariable Long id) {
        McpServer server = getById(id);
        Map<String, Object> result = new HashMap<>();
        
        try {
            mcpClientManager.initializeClient(server);
            result.put("success", true);
            result.put("message", "连接成功");
            result.put("connected", mcpClientManager.isConnected(server.getServerCode()));
        } catch (Exception e) {
            log.error("[MCP] 连接服务器失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "连接失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 断开 MCP 服务器连接
     */
    @PostMapping("/{id}/disconnect")
    public Map<String, Object> disconnect(@PathVariable Long id) {
        McpServer server = getById(id);
        Map<String, Object> result = new HashMap<>();
        
        mcpClientManager.disconnectClient(server.getServerCode());
        result.put("success", true);
        result.put("message", "已断开连接");
        result.put("connected", false);
        
        return result;
    }

    /**
     * 重新连接 MCP 服务器
     */
    @PostMapping("/{id}/reconnect")
    public Map<String, Object> reconnect(@PathVariable Long id) {
        McpServer server = getById(id);
        Map<String, Object> result = new HashMap<>();
        
        try {
            mcpClientManager.reconnectClient(server.getServerCode());
            result.put("success", true);
            result.put("message", "重新连接成功");
            result.put("connected", mcpClientManager.isConnected(server.getServerCode()));
        } catch (Exception e) {
            log.error("[MCP] 重新连接服务器失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "重新连接失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 获取 MCP 服务器连接状态
     */
    @GetMapping("/{id}/status")
    public Map<String, Object> getStatus(@PathVariable Long id) {
        McpServer server = getById(id);
        Map<String, Object> result = new HashMap<>();
        
        boolean connected = mcpClientManager.isConnected(server.getServerCode());
        result.put("serverCode", server.getServerCode());
        result.put("name", server.getName());
        result.put("connected", connected);
        
        if (connected) {
            // 获取该服务器提供的工具
            ToolCallback[] callbacks = mcpClientManager.getToolCallbacks(server.getServerCode());
            result.put("toolCount", callbacks.length);
            result.put("tools", java.util.Arrays.stream(callbacks)
                .map(cb -> cb.getToolDefinition().name())
                .collect(Collectors.toList()));
        }
        
        return result;
    }

    /**
     * 获取所有已连接服务器的状态
     */
    @GetMapping("/status")
    public Map<String, Object> getAllStatus() {
        Map<String, Object> result = new HashMap<>();
        
        result.put("connectedCount", mcpClientManager.getConnectedServerCount());
        
        // 获取所有 MCP 工具
        ToolCallback[] allCallbacks = mcpClientManager.getAllToolCallbacks();
        result.put("totalToolCount", allCallbacks.length);
        result.put("tools", java.util.Arrays.stream(allCallbacks)
            .map(cb -> cb.getToolDefinition().name())
            .collect(Collectors.toList()));
        
        return result;
    }

    /**
     * 初始化所有已启用的 MCP 服务器
     */
    @PostMapping("/initialize-all")
    public Map<String, Object> initializeAll() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            mcpClientManager.initializeAllClients();
            result.put("success", true);
            result.put("message", "初始化完成");
            result.put("connectedCount", mcpClientManager.getConnectedServerCount());
        } catch (Exception e) {
            log.error("[MCP] 初始化所有服务器失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "初始化失败: " + e.getMessage());
        }
        
        return result;
    }
}

