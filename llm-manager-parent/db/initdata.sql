-- =============================================
-- LLM Manager 初始化数据脚本
-- 说明：此文件仅包含 DML（INSERT/UPDATE）语句
-- DDL 语句请查看 schema.sql
-- =============================================

-- =============================================
-- MCP 服务器测试数据
-- =============================================

-- 测试数据1：SSE 类型 - 本地 MCP 服务器
INSERT INTO a_mcp_servers (server_code, name, description, transport_type, url, sse_endpoint, request_timeout, enabled, sort_order)
VALUES (
    'mcp_sse_local_001',
    'Local SSE Server',
    '本地 SSE MCP 服务器（测试用）',
    'SSE',
    'http://localhost:3001',
    '/sse',
    30,
    0,  -- 默认禁用，需要时手动启用
    1
) ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 测试数据2：SSE 类型 - 远程 MCP 服务器
INSERT INTO a_mcp_servers (server_code, name, description, transport_type, url, sse_endpoint, request_timeout, enabled, sort_order)
VALUES (
    'mcp_sse_remote_001',
    'Remote SSE Server',
    '远程 SSE MCP 服务器（测试用）',
    'SSE',
    'http://mcp.example.com:8080',
    '/sse',
    60,
    0,  -- 默认禁用
    2
) ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 测试数据3：Streamable HTTP 类型
INSERT INTO a_mcp_servers (server_code, name, description, transport_type, url, http_endpoint, request_timeout, enabled, sort_order)
VALUES (
    'mcp_http_local_001',
    'Local HTTP Server',
    '本地 Streamable HTTP MCP 服务器（测试用）',
    'STREAMABLE_HTTP',
    'http://localhost:3002',
    '/mcp',
    30,
    0,  -- 默认禁用
    3
) ON DUPLICATE KEY UPDATE name = VALUES(name);

