package com.llmmanager.agent.storage.core.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.llmmanager.agent.storage.core.entity.McpServer;
import com.llmmanager.agent.storage.core.mapper.McpServerMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MCP 服务器服务
 */
@Service
public class McpServerService extends ServiceImpl<McpServerMapper, McpServer> {

    @Resource
    private McpServerMapper mcpServerMapper;

    /**
     * 获取所有启用的服务器
     */
    public List<McpServer> getEnabledServers() {
        return mcpServerMapper.selectEnabledServers();
    }

    /**
     * 根据服务器标识获取
     */
    public McpServer getByServerCode(String serverCode) {
        return mcpServerMapper.selectByServerCode(serverCode);
    }

    /**
     * 获取指定传输类型的启用服务器
     */
    public List<McpServer> getEnabledByTransportType(String transportType) {
        return mcpServerMapper.selectEnabledByTransportType(transportType);
    }

    /**
     * 获取所有 STDIO 类型的启用服务器
     */
    public List<McpServer> getEnabledStdioServers() {
        return getEnabledByTransportType("STDIO");
    }

    /**
     * 获取所有 SSE 类型的启用服务器
     */
    public List<McpServer> getEnabledSseServers() {
        return getEnabledByTransportType("SSE");
    }

    /**
     * 获取所有 Streamable HTTP 类型的启用服务器
     */
    public List<McpServer> getEnabledStreamableHttpServers() {
        return getEnabledByTransportType("STREAMABLE_HTTP");
    }
}

