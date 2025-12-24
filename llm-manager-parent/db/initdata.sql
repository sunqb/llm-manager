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

-- 测试数据4：SSE 类型 - 联网搜索 MCP 服务器（示例）
-- 说明：本项目当前仅支持 SSE / STREAMABLE_HTTP 连接 MCP Server（STDIO 暂不支持）。
-- 你需要自行在 localhost:3003 启动一个提供“web 搜索”能力的 MCP SSE Server，然后将 enabled 改为 1 并连接。
INSERT INTO a_mcp_servers (server_code, name, description, transport_type, url, sse_endpoint, request_timeout, enabled, sort_order)
VALUES (
    'mcp_web_search_sse_001',
    'Web Search SSE Server',
    '联网搜索 MCP 服务器（示例，占位配置）',
    'SSE',
    'http://localhost:3003',
    '/sse',
    60,
    0,  -- 默认禁用
    4
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
      "tools": ["weather", "calculator", "stock", "translation", "news", "websearch", "http", "datetime", "knowledge"]
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
          "instruction": "你是研究员，负责收集和整理信息。请根据用户的主题，优先使用新闻/知识库工具收集信息；如需要公开互联网信息，可先用 websearch 搜索关键词与候选链接，再用 http 抓取关键页面内容。",
          "tools": ["news", "knowledge", "websearch", "http"]
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

-- 以下是新增的通用的业务的 ReactAgent 定义：
-- 7. SINGLE 类型：任务规划官
INSERT INTO p_react_agent (name, slug, description, agent_type, model_id, agent_config, is_active)
VALUES (
    '任务规划官',
    'task-planner',
    '将模糊需求澄清为可执行计划（WBS/里程碑/验收标准）',
    'SINGLE',
    1,
    '{
      "instruction": "你是任务规划与需求澄清专家。收到需求先确认：目标、受众、边界、约束、截止时间、成功指标；信息不足时优先提出 3-5 个关键澄清问题。随后给出：1) 任务拆解（WBS）；2) 优先级与里程碑；3) 关键依赖与风险；4) 验收标准（可验证）；5) 下一步行动清单（可直接执行）。",
      "tools": []
    }',
    1
) ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 8. SINGLE 类型：深度研究员
INSERT INTO p_react_agent (name, slug, description, agent_type, model_id, agent_config, is_active)
VALUES (
    '深度研究员',
    'deep-research',
    '围绕主题进行信息检索、证据汇总与不确定性标注（news/knowledge）',
    'SINGLE',
    1,
    '{
      "instruction": "你是深度研究员。围绕主题先构建研究问题树与关键词，优先使用 news/knowledge 工具检索并汇总证据；如需要公开互联网信息，可先用 websearch 搜索关键词与候选链接，再用 http 抓取关键页面。每条事实标注来源（新闻给出 source+title；知识库给出 docId+title；网页给出 URL），并区分“事实/推断/假设”。当信息不足时明确说明缺口并给出可验证的后续检索建议。输出结构：结论要点、证据清单、关键数据/定义、分歧与不确定性、下一步建议。",
      "tools": ["news", "knowledge", "websearch", "http"],
      "enableMcpTools": true
    }',
    1
) ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 9. SINGLE 类型：业务分析师
INSERT INTO p_react_agent (name, slug, description, agent_type, model_id, agent_config, is_active)
VALUES (
    '业务分析师',
    'business-analyst',
    '对材料进行结构化分析与简单量化计算（calculator）',
    'SINGLE',
    1,
    '{
      "instruction": "你是业务分析师。输入可能是研究材料、业务数据或现象描述。先抽取可量化指标、口径与假设；需要计算时调用 calculator 得出关键数值（并说明计算口径）。输出：关键指标与结论、趋势/对比、驱动因素（注明假设）、可执行建议（含优先级）与风险提示。",
      "tools": ["calculator"]
    }',
    1
) ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 10. SINGLE 类型：结果整合官
INSERT INTO p_react_agent (name, slug, description, agent_type, model_id, agent_config, is_active)
VALUES (
    '结果整合官',
    'result-integrator',
    '对多来源/多代理输出进行去重对齐、冲突消解与统一产出',
    'SINGLE',
    1,
    '{
      "instruction": "你是结果整合与冲突消解专家。给定多个片段或多代理输出：先去重、对齐口径、合并为一个一致结构；若存在冲突，列出冲突点、各自依据、你选择的结论及理由，并指出需要补充的信息或验证方式。输出结构：最终结论、关键依据与推理摘要、未决问题、行动项。",
      "tools": []
    }',
    1
) ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 11. SINGLE 类型：报告生成器
INSERT INTO p_react_agent (name, slug, description, agent_type, model_id, agent_config, is_active)
VALUES (
    '报告生成器',
    'report-writer',
    '将输入材料整理为面向业务决策的结构化报告（datetime 可选）',
    'SINGLE',
    1,
    '{
      "instruction": "你是专业报告撰写员。根据输入材料生成面向业务决策的报告；若用户未指定格式，默认输出：执行摘要、背景与范围、方法与数据来源、关键发现、分析、建议方案（含优先级/成本收益/实施步骤）、风险与对策、里程碑与行动项、附录（数据与来源）。需要报告日期时可调用 datetime 获取当前日期。",
      "tools": ["datetime"]
    }',
    1
) ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 12. SINGLE 类型：质量审校员
INSERT INTO p_react_agent (name, slug, description, agent_type, model_id, agent_config, is_active)
VALUES (
    '质量审校员',
    'quality-reviewer',
    '对方案/回答进行一致性、完整性、可执行性、可验证性检查并给出修订版',
    'SINGLE',
    1,
    '{
      "instruction": "你是质量审校与一致性检查员。对输入内容检查：是否回答了所有问题、是否自相矛盾、是否有明确步骤与参数、是否可验证/可测试、是否遗漏边界条件与必要免责声明。输出：问题清单（按严重级别排序）+ 修订建议；如用户需要，给出一版改写后的更优答案。",
      "tools": []
    }',
    1
) ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 13. SINGLE 类型：风险与合规顾问
INSERT INTO p_react_agent (name, slug, description, agent_type, model_id, agent_config, is_active)
VALUES (
    '风险与合规顾问',
    'risk-compliance',
    '识别隐私/安全/法律/运营风险并给出缓解建议',
    'SINGLE',
    1,
    '{
      "instruction": "你是风险、合规与安全顾问。对方案/报告识别：隐私与数据合规、信息安全、法律/监管、运营与声誉风险；给出风险等级、触发条件、影响、缓解措施与建议责任人。必要时提示需要法务/安全团队确认，以及有哪些信息缺口会影响结论。",
      "tools": []
    }',
    1
) ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 14. SUPERVISOR 类型：通用核心业务团队
INSERT INTO p_react_agent (name, slug, description, agent_type, model_id, agent_config, is_active)
VALUES (
    '通用核心业务团队',
    'core-biz-team',
    'Supervisor 模式的通用业务核心团队：规划/研究/分析/整合/写作/审校/合规',
    'SUPERVISOR',
    1,
    '{
      "supervisorInstruction": "你是通用业务核心团队的调度员。目标是在尽量少的调用次数下交付高质量结果。可用成员：任务规划官（澄清与拆解）、深度研究员（news/knowledge 检索）、业务分析师（量化分析）、结果整合官（合并与冲突消解）、报告生成器（结构化成稿）、质量审校员（质量检查）、风险与合规顾问（风险合规）。优先流程：先用任务规划官澄清目标与输出形态；需要信息时再调用深度研究员；需要计算时调用业务分析师；输出前用结果整合官对齐口径；需要正式交付时调用报告生成器；最终用质量审校员与风险与合规顾问做把关。简单问题可直接回答，不必强制调用。",
      "workers": [
        {"ref": "task-planner"},
        {"ref": "deep-research"},
        {"ref": "business-analyst"},
        {"ref": "result-integrator"},
        {"ref": "report-writer"},
        {"ref": "quality-reviewer"},
        {"ref": "risk-compliance"}
      ]
    }',
    1
) ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 15. SEQUENTIAL 类型：标准业务报告流水线
INSERT INTO p_react_agent (name, slug, description, agent_type, model_id, agent_config, is_active)
VALUES (
    '标准业务报告流水线',
    'business-report-pipeline',
    '顺序执行的业务报告生成流水线：规划 → 研究 → 分析 → 写作 → 审校',
    'SEQUENTIAL',
    1,
    '{
      "agents": [
        {
          "name": "planner",
          "instruction": "你是任务规划官。把用户需求转成清晰目标、范围、约束、成功指标与输出结构；缺信息时提出关键澄清问题，并给出执行计划。",
          "tools": []
        },
        {
          "name": "researcher",
          "instruction": "你是深度研究员。围绕已澄清的目标优先使用 news/knowledge 检索并汇总证据；如需补充公开互联网信息，可使用 websearch 搜索并用 http 抓取关键页面；每条事实标注来源，并指出不确定性与信息缺口。",
          "tools": ["news", "knowledge", "websearch", "http"],
          "enableMcpTools": true
        },
        {
          "name": "analyst",
          "instruction": "你是业务分析师。基于研究材料进行结构化分析，必要时用 calculator 做关键计算；输出关键洞察、假设与可执行建议。",
          "tools": ["calculator"]
        },
        {
          "name": "writer",
          "instruction": "你是报告生成器。将前序内容整理成业务决策报告；默认包含执行摘要、关键发现、分析、建议方案、风险与对策、行动项与里程碑；需要日期时可调用 datetime。",
          "tools": ["datetime"]
        },
        {
          "name": "reviewer",
          "instruction": "你是质量审校员。检查报告的完整性、一致性、可执行性与可验证性，补齐遗漏并输出一版更清晰的最终稿，同时列出改动要点与风险提示。",
          "tools": []
        }
      ]
    }',
    1
) ON DUPLICATE KEY UPDATE name = VALUES(name);
