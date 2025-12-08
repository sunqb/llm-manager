-- MCP 服务器测试数据初始化脚本
-- 使用方法：在数据库客户端中执行此脚本

-- 先创建表（如果不存在）
CREATE TABLE IF NOT EXISTS a_mcp_servers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    server_code VARCHAR(32) NOT NULL UNIQUE COMMENT '服务器唯一标识（32位UUID）',
    name VARCHAR(100) NOT NULL COMMENT '服务器名称',
    description VARCHAR(500) COMMENT '服务器描述',
    transport_type VARCHAR(20) NOT NULL COMMENT '传输类型：STDIO / SSE / STREAMABLE_HTTP',
    command VARCHAR(255) COMMENT 'STDIO: 执行命令',
    args JSON COMMENT 'STDIO: 命令参数列表',
    env JSON COMMENT 'STDIO: 环境变量',
    url VARCHAR(500) COMMENT 'SSE/HTTP: 服务器 URL',
    sse_endpoint VARCHAR(100) DEFAULT '/sse' COMMENT 'SSE: SSE 端点路径',
    http_endpoint VARCHAR(100) DEFAULT '/mcp' COMMENT 'HTTP: Streamable HTTP 端点路径',
    request_timeout INT DEFAULT 30 COMMENT '请求超时时间（秒）',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    sort_order INT DEFAULT 0 COMMENT '排序权重',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by VARCHAR(50) COMMENT '创建人',
    update_by VARCHAR(50) COMMENT '更新人',
    is_delete TINYINT(1) DEFAULT 0 COMMENT '是否删除',
    INDEX idx_server_code (server_code),
    INDEX idx_transport_type (transport_type),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP 服务器配置表';

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

-- 查看插入结果
SELECT id, server_code, name, transport_type, url, enabled FROM a_mcp_servers WHERE is_delete = 0;

