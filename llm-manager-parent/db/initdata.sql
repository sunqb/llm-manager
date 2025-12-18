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

-- =============================================
-- ReactAgent 初始化数据
-- =============================================

-- 1. SINGLE 类型：全能助手
INSERT INTO p_react_agent (name, slug, description, agent_type, model_id, agent_config, is_active)
VALUES (
    '全能助手',
    'all-in-one',
    '一个全能助手，可以查询天气、股票、新闻、翻译、日期计算、企业知识库等',
    'SINGLE',
    1,
    '{
      "instruction": "你是一个智能助手，拥有以下能力：\n1. 查询天气信息\n2. 数学计算\n3. 股票行情查询和分析\n4. 文本翻译和语言检测\n5. 新闻资讯获取\n6. 日期时间查询和计算\n7. 企业知识库查询（请假、报销、入职等规定）\n请根据用户的问题，自主决定调用哪些工具来完成任务。",
      "tools": ["weather", "calculator", "stock", "translation", "news", "datetime", "knowledge"]
    }',
    1
) ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 2. SINGLE 类型：天气专家
INSERT INTO p_react_agent (name, slug, description, agent_type, model_id, agent_config, is_active)
VALUES (
    '天气专家',
    'weather-expert',
    '专业的天气查询助手',
    'SINGLE',
    1,
    '{
      "instruction": "你是天气专家。使用天气工具查询用户询问的城市天气，提供详细的天气信息和出行建议。",
      "tools": ["weather"]
    }',
    1
) ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 3. SINGLE 类型：股票专家
INSERT INTO p_react_agent (name, slug, description, agent_type, model_id, agent_config, is_active)
VALUES (
    '股票专家',
    'stock-expert',
    '专业的股票分析助手',
    'SINGLE',
    1,
    '{
      "instruction": "你是股票分析专家。使用股票工具查询行情并提供专业分析，注意提醒用户投资有风险。",
      "tools": ["stock", "calculator"]
    }',
    1
) ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 4. SINGLE 类型：HR专家
INSERT INTO p_react_agent (name, slug, description, agent_type, model_id, agent_config, is_active)
VALUES (
    'HR专家',
    'hr-expert',
    '企业人事规章制度咨询助手',
    'SINGLE',
    1,
    '{
      "instruction": "你是HR专家。使用知识库工具查询公司规章制度，解答员工关于请假、报销、入职、考勤、福利等问题。",
      "tools": ["knowledge"]
    }',
    1
) ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 5. SEQUENTIAL 类型：研究报告流水线
INSERT INTO p_react_agent (name, slug, description, agent_type, model_id, agent_config, is_active)
VALUES (
    '研究报告流水线',
    'research-pipeline',
    '顺序执行的研究报告生成流水线：研究 → 分析 → 总结',
    'SEQUENTIAL',
    1,
    '{
      "agents": [
        {
          "name": "researcher",
          "instruction": "你是研究员，负责收集和整理信息。请根据用户的主题，使用新闻和知识库工具收集相关信息。",
          "tools": ["news", "knowledge"]
        },
        {
          "name": "analyst",
          "instruction": "你是数据分析师。请分析研究员收集的信息，提取关键数据和趋势，如有需要可进行计算。",
          "tools": ["calculator", "stock"]
        },
        {
          "name": "summarizer",
          "instruction": "你是总结专家。请将之前的研究和分析整合成一份简洁明了的执行摘要，突出关键结论和建议。",
          "tools": []
        }
      ]
    }',
    1
) ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 6. SUPERVISOR 类型：企业智能助手团队
INSERT INTO p_react_agent (name, slug, description, agent_type, model_id, agent_config, is_active)
VALUES (
    '企业智能助手团队',
    'enterprise-team',
    'Supervisor 模式的企业助手团队，自主调度多个专家完成任务',
    'SUPERVISOR',
    1,
    '{
      "supervisorInstruction": "你是企业智能助手的调度员。根据用户需求，调用合适的专家来完成任务。可用专家：天气专家、股票专家、新闻专家、HR专家、翻译专家。请分析用户需求，决定调用哪些专家，以及调用顺序。",
      "workers": [
        {"ref": "weather-expert"},
        {"ref": "stock-expert"},
        {"ref": "hr-expert"},
        {
          "name": "news-expert",
          "instruction": "你是新闻编辑。使用新闻工具获取最新资讯并整理汇报。",
          "tools": ["news"]
        },
        {
          "name": "translator-expert",
          "instruction": "你是翻译专家。使用翻译工具完成用户的翻译需求。",
          "tools": ["translation"]
        }
      ]
    }',
    1
) ON DUPLICATE KEY UPDATE name = VALUES(name);

