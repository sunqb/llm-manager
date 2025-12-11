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

-- =============================================
-- Graph 节点类型初始化数据
-- =============================================

-- 1. LLM 调用节点
INSERT INTO p_graph_node_types (type_code, type_name, description, config_schema, executor_bean_name, is_system)
VALUES (
    'LLM_NODE',
    'LLM 调用节点',
    '调用语言模型进行文本生成',
    '{
  "type": "object",
  "properties": {
    "prompt_template": {"type": "string", "description": "提示词模板，支持 {{key}} 占位符"},
    "model_id": {"type": "integer", "description": "模型ID（可选，默认使用工作流配置的模型）"},
    "temperature": {"type": "number", "description": "温度参数（0-1）"},
    "max_tokens": {"type": "integer", "description": "最大生成 token 数"},
    "output_key": {"type": "string", "description": "输出结果存储到状态的 key"}
  },
  "required": ["prompt_template", "output_key"]
}',
    'LlmNodeExecutor',
    1
) ON DUPLICATE KEY UPDATE type_name = VALUES(type_name);

-- 2. 工具调用节点
INSERT INTO p_graph_node_types (type_code, type_name, description, config_schema, executor_bean_name, is_system)
VALUES (
    'TOOL_NODE',
    '工具调用节点',
    '调用系统工具（如搜索、计算器等）',
    '{
  "type": "object",
  "properties": {
    "tool_name": {"type": "string", "description": "工具名称"},
    "tool_params": {"type": "object", "description": "工具参数（JSON 对象）"},
    "output_key": {"type": "string", "description": "工具结果存储到状态的 key"}
  },
  "required": ["tool_name", "output_key"]
}',
    'ToolNodeExecutor',
    1
) ON DUPLICATE KEY UPDATE type_name = VALUES(type_name);

-- 3. 条件路由节点
INSERT INTO p_graph_node_types (type_code, type_name, description, config_schema, executor_bean_name, is_system)
VALUES (
    'CONDITION_NODE',
    '条件路由节点',
    '根据状态值决定下一步路由',
    '{
  "type": "object",
  "properties": {
    "condition_field": {"type": "string", "description": "条件判断的状态字段名"},
    "routes": {"type": "object", "description": "路由映射（值 -> 节点ID）"},
    "default_route": {"type": "string", "description": "默认路由（可选）"}
  },
  "required": ["condition_field", "routes"]
}',
    'ConditionNodeExecutor',
    1
) ON DUPLICATE KEY UPDATE type_name = VALUES(type_name);

-- 4. 数据转换节点
INSERT INTO p_graph_node_types (type_code, type_name, description, config_schema, executor_bean_name, is_system)
VALUES (
    'TRANSFORM_NODE',
    '数据转换节点',
    '转换或处理状态数据',
    '{
  "type": "object",
  "properties": {
    "transform_type": {"type": "string", "enum": ["MERGE", "EXTRACT", "FORMAT"], "description": "转换类型"},
    "input_keys": {"type": "array", "items": {"type": "string"}, "description": "输入字段列表"},
    "output_key": {"type": "string", "description": "输出结果存储到状态的 key"},
    "transform_script": {"type": "string", "description": "转换脚本（可选，支持 SpEL 表达式）"}
  },
  "required": ["transform_type", "input_keys", "output_key"]
}',
    'TransformNodeExecutor',
    1
) ON DUPLICATE KEY UPDATE type_name = VALUES(type_name);

-- 5. HTTP 请求节点
INSERT INTO p_graph_node_types (type_code, type_name, description, config_schema, executor_bean_name, is_system)
VALUES (
    'HTTP_REQUEST_NODE',
    'HTTP 请求节点',
    '发起 HTTP 请求获取外部数据',
    '{
  "type": "object",
  "properties": {
    "url": {"type": "string", "description": "请求 URL（支持模板）"},
    "method": {"type": "string", "enum": ["GET", "POST", "PUT", "DELETE"], "description": "HTTP 方法"},
    "headers": {"type": "object", "description": "请求头（JSON 对象）"},
    "body": {"type": "string", "description": "请求体（JSON 字符串）"},
    "output_key": {"type": "string", "description": "响应结果存储到状态的 key"}
  },
  "required": ["url", "method", "output_key"]
}',
    'HttpRequestNodeExecutor',
    1
) ON DUPLICATE KEY UPDATE type_name = VALUES(type_name);

