package com.llmmanager.ops.controller;

import com.llmmanager.agent.mcp.McpClientManager;
import com.llmmanager.agent.storage.core.entity.McpServer;
import com.llmmanager.agent.storage.core.service.McpServerService;
import com.llmmanager.common.exception.BusinessException;
import com.llmmanager.common.result.Result;
import com.llmmanager.common.result.ResultCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
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
    public Result<List<McpServer>> getAll() {
        return Result.success(mcpServerService.list());
    }

    /**
     * 获取已启用的 MCP 服务器
     */
    @GetMapping("/enabled")
    public Result<List<McpServer>> getEnabled() {
        return Result.success(mcpServerService.getEnabledServers());
    }

    /**
     * 根据 ID 获取 MCP 服务器
     */
    @GetMapping("/{id}")
    public Result<McpServer> getById(@PathVariable Long id) {
        McpServer server = mcpServerService.getById(id);
        if (server == null) {
            throw new BusinessException(ResultCode.MCP_SERVER_NOT_FOUND, "MCP 服务器不存在: " + id);
        }
        return Result.success(server);
    }

    /**
     * 根据 serverCode 获取 MCP 服务器
     */
    @GetMapping("/code/{serverCode}")
    public Result<McpServer> getByCode(@PathVariable String serverCode) {
        McpServer server = mcpServerService.getByServerCode(serverCode);
        if (server == null) {
            throw new BusinessException(ResultCode.MCP_SERVER_NOT_FOUND, "MCP 服务器不存在: " + serverCode);
        }
        return Result.success(server);
    }

    /**
     * 创建 MCP 服务器
     */
    @PostMapping
    public Result<McpServer> create(@RequestBody McpServer server) {
        mcpServerService.save(server);
        return Result.success(server);
    }

    /**
     * 更新 MCP 服务器
     */
    @PutMapping("/{id}")
    public Result<McpServer> update(@PathVariable Long id, @RequestBody McpServer updated) {
        McpServer existing = mcpServerService.getById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.MCP_SERVER_NOT_FOUND, "MCP 服务器不存在: " + id);
        }

        updated.setId(id);
        mcpServerService.updateById(updated);

        // 如果服务器已连接，重新连接以应用新配置
        if (mcpClientManager.isConnected(existing.getServerCode())) {
            mcpClientManager.reconnectClient(existing.getServerCode());
        }

        return Result.success(mcpServerService.getById(id));
    }

    /**
     * 删除 MCP 服务器
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        McpServer server = mcpServerService.getById(id);
        if (server == null) {
            throw new BusinessException(ResultCode.MCP_SERVER_NOT_FOUND, "MCP 服务器不存在: " + id);
        }

        // 先断开连接
        if (mcpClientManager.isConnected(server.getServerCode())) {
            mcpClientManager.disconnectClient(server.getServerCode());
        }
        mcpServerService.removeById(id);
        return Result.success();
    }

    /**
     * 连接 MCP 服务器
     */
    @PostMapping("/{id}/connect")
    public Result<Map<String, Object>> connect(@PathVariable Long id) {
        McpServer server = mcpServerService.getById(id);
        if (server == null) {
            throw new BusinessException(ResultCode.MCP_SERVER_NOT_FOUND, "MCP 服务器不存在: " + id);
        }

        try {
            mcpClientManager.initializeClient(server);
            Map<String, Object> data = new HashMap<>();
            data.put("connected", mcpClientManager.isConnected(server.getServerCode()));
            return Result.success(data, "连接成功");
        } catch (Exception e) {
            log.error("[MCP] 连接服务器失败: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.MCP_CONNECTION_FAILED, "连接失败: " + e.getMessage());
        }
    }

    /**
     * 断开 MCP 服务器连接
     */
    @PostMapping("/{id}/disconnect")
    public Result<Map<String, Object>> disconnect(@PathVariable Long id) {
        McpServer server = mcpServerService.getById(id);
        if (server == null) {
            throw new BusinessException(ResultCode.MCP_SERVER_NOT_FOUND, "MCP 服务器不存在: " + id);
        }

        mcpClientManager.disconnectClient(server.getServerCode());
        Map<String, Object> data = new HashMap<>();
        data.put("connected", false);
        return Result.success(data, "已断开连接");
    }

    /**
     * 重新连接 MCP 服务器
     */
    @PostMapping("/{id}/reconnect")
    public Result<Map<String, Object>> reconnect(@PathVariable Long id) {
        McpServer server = mcpServerService.getById(id);
        if (server == null) {
            throw new BusinessException(ResultCode.MCP_SERVER_NOT_FOUND, "MCP 服务器不存在: " + id);
        }

        try {
            mcpClientManager.reconnectClient(server.getServerCode());
            Map<String, Object> data = new HashMap<>();
            data.put("connected", mcpClientManager.isConnected(server.getServerCode()));
            return Result.success(data, "重新连接成功");
        } catch (Exception e) {
            log.error("[MCP] 重新连接服务器失败: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.MCP_CONNECTION_FAILED, "重新连接失败: " + e.getMessage());
        }
    }

    /**
     * 获取 MCP 服务器连接状态
     */
    @GetMapping("/{id}/status")
    public Result<Map<String, Object>> getStatus(@PathVariable Long id) {
        McpServer server = mcpServerService.getById(id);
        if (server == null) {
            throw new BusinessException(ResultCode.MCP_SERVER_NOT_FOUND, "MCP 服务器不存在: " + id);
        }

        Map<String, Object> result = new HashMap<>();
        boolean connected = mcpClientManager.isConnected(server.getServerCode());
        result.put("serverCode", server.getServerCode());
        result.put("name", server.getName());
        result.put("connected", connected);

        if (connected) {
            ToolCallback[] callbacks = mcpClientManager.getToolCallbacks(server.getServerCode());
            result.put("toolCount", callbacks.length);
            result.put("tools", Arrays.stream(callbacks)
                    .map(cb -> cb.getToolDefinition().name())
                    .collect(Collectors.toList()));
        }

        return Result.success(result);
    }

    /**
     * 获取所有已连接服务器的状态
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getAllStatus() {
        Map<String, Object> result = new HashMap<>();

        result.put("connectedCount", mcpClientManager.getConnectedServerCount());

        ToolCallback[] allCallbacks = mcpClientManager.getAllToolCallbacks();
        result.put("totalToolCount", allCallbacks.length);
        result.put("tools", Arrays.stream(allCallbacks)
                .map(cb -> cb.getToolDefinition().name())
                .collect(Collectors.toList()));

        return Result.success(result);
    }

    /**
     * 初始化所有已启用的 MCP 服务器
     */
    @PostMapping("/initialize-all")
    public Result<Map<String, Object>> initializeAll() {
        try {
            mcpClientManager.initializeAllClients();
            Map<String, Object> data = new HashMap<>();
            data.put("connectedCount", mcpClientManager.getConnectedServerCount());
            return Result.success(data, "初始化完成");
        } catch (Exception e) {
            log.error("[MCP] 初始化所有服务器失败: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.MCP_CONNECTION_FAILED, "初始化失败: " + e.getMessage());
        }
    }
}
