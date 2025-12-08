-- =============================================
-- LLM Manager 数据库表结构
-- 表命名规范：
--   p_ 开头：业务表（llm-service）
--   a_ 开头：Agent相关表（llm-agent）
-- =============================================

-- 用户表
CREATE TABLE IF NOT EXISTS p_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    username VARCHAR(255) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码',
    email VARCHAR(255) COMMENT '邮箱',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    is_delete TINYINT(3) UNSIGNED DEFAULT 0 COMMENT '是否删除，0：正常，1：删除',
    INDEX idx_username (username),
    INDEX idx_is_delete (is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 渠道表
CREATE TABLE IF NOT EXISTS p_channel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name VARCHAR(255) NOT NULL COMMENT '渠道名称',
    base_url VARCHAR(500) COMMENT 'API基础URL',
    api_key VARCHAR(500) COMMENT 'API密钥',
    type VARCHAR(50) COMMENT '渠道类型',
    additional_config TEXT COMMENT '额外配置（JSON格式）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    is_delete TINYINT(3) UNSIGNED DEFAULT 0 COMMENT '是否删除，0：正常，1：删除',
    INDEX idx_name (name),
    INDEX idx_is_delete (is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道表';

-- LLM模型表
CREATE TABLE IF NOT EXISTS p_llm_model (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name VARCHAR(255) NOT NULL COMMENT '模型名称',
    model_identifier VARCHAR(255) NOT NULL COMMENT '模型标识符',
    channel_id BIGINT COMMENT '所属渠道ID',
    description TEXT COMMENT '模型描述',
    temperature DOUBLE COMMENT '温度参数',
    max_tokens INT COMMENT '最大token数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    is_delete TINYINT(3) UNSIGNED DEFAULT 0 COMMENT '是否删除，0：正常，1：删除',
    INDEX idx_channel_id (channel_id),
    INDEX idx_model_identifier (model_identifier),
    INDEX idx_is_delete (is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM模型表';

-- Agent表
CREATE TABLE IF NOT EXISTS p_agents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name VARCHAR(255) NOT NULL COMMENT 'Agent名称',
    slug VARCHAR(255) NOT NULL UNIQUE COMMENT 'Agent唯一标识',
    description TEXT COMMENT 'Agent描述',
    system_prompt TEXT COMMENT '系统提示词',
    llm_model_id BIGINT COMMENT '关联的LLM模型ID',
    temperature_override DOUBLE COMMENT '温度覆盖值',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    is_delete TINYINT(3) UNSIGNED DEFAULT 0 COMMENT '是否删除，0：正常，1：删除',
    INDEX idx_slug (slug),
    INDEX idx_llm_model_id (llm_model_id),
    INDEX idx_is_delete (is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent表';

-- 提示词模板表
CREATE TABLE IF NOT EXISTS p_prompt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name VARCHAR(255) NOT NULL COMMENT '模板名称',
    content TEXT NOT NULL COMMENT '模板内容',
    description TEXT COMMENT '模板描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    is_delete TINYINT(3) UNSIGNED DEFAULT 0 COMMENT '是否删除，0：正常，1：删除',
    INDEX idx_name (name),
    INDEX idx_is_delete (is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提示词模板表';

-- API密钥表
CREATE TABLE IF NOT EXISTS p_api_key (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name VARCHAR(255) NOT NULL COMMENT '密钥名称',
    token VARCHAR(500) NOT NULL UNIQUE COMMENT 'API令牌',
    active TINYINT(1) DEFAULT 1 COMMENT '是否激活，0：禁用，1：启用',
    expires_at DATETIME COMMENT '过期时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    is_delete TINYINT(3) UNSIGNED DEFAULT 0 COMMENT '是否删除，0：正常，1：删除',
    INDEX idx_token (token),
    INDEX idx_is_delete (is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API密钥表';

-- =============================================
-- Agent 相关表（llm-agent 模块）
-- 命名规范：
--   *_id：主键（自增整数）
--   *_code：业务唯一标识（UUID）
-- =============================================

-- 会话表（独立的会话元数据）
CREATE TABLE IF NOT EXISTS a_conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    conversation_code VARCHAR(100) NOT NULL UNIQUE COMMENT '会话唯一标识（UUID）',
    title VARCHAR(500) COMMENT '会话标题（自动生成或用户设置）',
    agent_slug VARCHAR(255) COMMENT '关联的Agent标识',
    model_id BIGINT COMMENT '使用的模型ID',
    summary TEXT COMMENT '会话摘要',
    message_count INT DEFAULT 0 COMMENT '消息总数',
    total_tokens INT DEFAULT 0 COMMENT '总tokens消耗',
    last_message_time DATETIME COMMENT '最后消息时间',
    is_archived TINYINT DEFAULT 0 COMMENT '是否归档，0：否，1：是',
    is_pinned TINYINT DEFAULT 0 COMMENT '是否置顶，0：否，1：是',
    tags VARCHAR(500) COMMENT '标签（逗号分隔）',
    metadata TEXT COMMENT '额外元数据（JSON格式）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    is_delete TINYINT(3) UNSIGNED DEFAULT 0 COMMENT '是否删除，0：正常，1：删除',
    INDEX idx_conversation_code (conversation_code),
    INDEX idx_agent_slug (agent_slug),
    INDEX idx_model_id (model_id),
    INDEX idx_update_time (update_time),
    INDEX idx_last_message_time (last_message_time),
    INDEX idx_is_archived (is_archived),
    INDEX idx_is_pinned (is_pinned),
    INDEX idx_is_delete (is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

-- 聊天历史表（消息记录）
CREATE TABLE IF NOT EXISTS a_chat_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    message_code VARCHAR(32) NOT NULL UNIQUE COMMENT '消息唯一标识（32位UUID）',
    conversation_code VARCHAR(100) NOT NULL COMMENT '会话标识（关联 a_conversations.conversation_code）',
    turn_code VARCHAR(32) COMMENT '轮次标识（关联 a_conversation_turns.turn_code）',
    message_index INT NOT NULL DEFAULT 0 COMMENT '消息序号（同一会话内从0开始递增）',
    message_type VARCHAR(20) NOT NULL COMMENT '消息类型：SYSTEM/USER/ASSISTANT/TOOL',
    content TEXT NOT NULL COMMENT '消息内容',
    metadata TEXT COMMENT '元数据（JSON格式）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    is_delete TINYINT(3) UNSIGNED DEFAULT 0 COMMENT '是否删除，0：正常，1：删除',
    INDEX idx_message_code (message_code),
    INDEX idx_conversation_code (conversation_code),
    INDEX idx_turn_code (turn_code),
    INDEX idx_create_time (create_time),
    INDEX idx_is_delete (is_delete),
    UNIQUE INDEX uk_conv_msg_idx (conversation_code, message_index) COMMENT '会话标识+消息序号唯一约束'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天历史表';

-- 对话轮次表（一次问答的关联）
CREATE TABLE IF NOT EXISTS a_conversation_turns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    turn_code VARCHAR(32) NOT NULL UNIQUE COMMENT 'Turn唯一标识（32位UUID）',
    conversation_code VARCHAR(100) NOT NULL COMMENT '会话标识',
    turn_index INT NOT NULL DEFAULT 0 COMMENT '轮次序号（从0开始）',

    -- 关联的消息
    user_message_code VARCHAR(32) COMMENT '用户消息标识',
    assistant_message_code VARCHAR(32) COMMENT '助手消息标识',

    -- Token 统计
    prompt_tokens INT DEFAULT 0 COMMENT '输入token数',
    completion_tokens INT DEFAULT 0 COMMENT '输出token数',
    total_tokens INT DEFAULT 0 COMMENT '总token数',

    -- 性能指标
    latency_ms INT DEFAULT 0 COMMENT '响应耗时(毫秒)',
    first_token_ms INT COMMENT '首token耗时(毫秒)',

    -- 状态
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态：PENDING/PROCESSING/SUCCESS/FAILED/TIMEOUT',
    error_message TEXT COMMENT '错误信息',

    -- 模型信息
    model_id BIGINT COMMENT '使用的模型ID',
    model_identifier VARCHAR(255) COMMENT '模型标识符',

    -- 时间
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',

    -- 元数据
    metadata TEXT COMMENT '额外元数据（JSON格式）',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    is_delete TINYINT(3) UNSIGNED DEFAULT 0 COMMENT '是否删除，0：正常，1：删除',

    INDEX idx_turn_code (turn_code),
    INDEX idx_conversation_code (conversation_code),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time),
    INDEX idx_is_delete (is_delete),
    UNIQUE INDEX uk_conv_turn_idx (conversation_code, turn_index) COMMENT '会话标识+轮次序号唯一约束'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话轮次表';

-- 媒体文件表（支持多模态消息）
CREATE TABLE IF NOT EXISTS a_media_files (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    file_code VARCHAR(100) NOT NULL UNIQUE COMMENT '文件唯一标识（UUID）',
    conversation_code VARCHAR(100) COMMENT '关联的会话标识',
    message_code VARCHAR(100) COMMENT '关联的消息标识（a_chat_history.message_code）',
    media_type VARCHAR(20) NOT NULL COMMENT '媒体类型：IMAGE/DOCUMENT/AUDIO/VIDEO/OTHER',
    mime_type VARCHAR(100) NOT NULL COMMENT 'MIME类型（如 image/png, application/pdf）',
    file_name VARCHAR(255) COMMENT '原始文件名',
    file_size BIGINT COMMENT '文件大小（字节）',
    storage_path VARCHAR(500) COMMENT '存储路径（相对路径或URL）',
    file_url VARCHAR(500) COMMENT '访问URL',
    thumbnail_url VARCHAR(500) COMMENT '缩略图URL（仅图片）',
    width INT COMMENT '图片宽度（仅图片）',
    height INT COMMENT '图片高度（仅图片）',
    duration INT COMMENT '时长（秒，仅音视频）',
    metadata TEXT COMMENT '额外元数据（JSON格式）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    is_delete TINYINT(3) UNSIGNED DEFAULT 0 COMMENT '是否删除，0：正常，1：删除',
    INDEX idx_file_code (file_code),
    INDEX idx_conversation_code (conversation_code),
    INDEX idx_message_code (message_code),
    INDEX idx_media_type (media_type),
    INDEX idx_is_delete (is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体文件表';

-- MCP 服务器配置表
-- 用于存储 MCP (Model Context Protocol) 服务器连接配置
CREATE TABLE IF NOT EXISTS a_mcp_servers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    server_code VARCHAR(32) NOT NULL UNIQUE COMMENT '服务器唯一标识（32位UUID）',
    name VARCHAR(100) NOT NULL COMMENT '服务器名称',
    description VARCHAR(500) COMMENT '服务器描述',

    -- 传输类型
    transport_type VARCHAR(20) NOT NULL COMMENT '传输类型：STDIO / SSE / STREAMABLE_HTTP',

    -- STDIO 配置
    command VARCHAR(255) COMMENT 'STDIO: 执行命令（如 npx, node, python）',
    args JSON COMMENT 'STDIO: 命令参数列表（JSON 数组）',
    env JSON COMMENT 'STDIO: 环境变量（JSON 对象）',

    -- SSE / HTTP 配置
    url VARCHAR(500) COMMENT 'SSE/HTTP: 服务器 URL',
    sse_endpoint VARCHAR(100) DEFAULT '/sse' COMMENT 'SSE: SSE 端点路径',
    http_endpoint VARCHAR(100) DEFAULT '/mcp' COMMENT 'HTTP: Streamable HTTP 端点路径',

    -- 通用配置
    request_timeout INT DEFAULT 30 COMMENT '请求超时时间（秒）',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    sort_order INT DEFAULT 0 COMMENT '排序权重（越小越靠前）',

    -- 标准字段
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by VARCHAR(50) COMMENT '创建人',
    update_by VARCHAR(50) COMMENT '更新人',
    is_delete TINYINT(1) DEFAULT 0 COMMENT '是否删除（0:正常 1:删除）',

    INDEX idx_server_code (server_code),
    INDEX idx_transport_type (transport_type),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP 服务器配置表';

-- =============================================
-- 初始化数据请查看 initdata.sql
-- =============================================
