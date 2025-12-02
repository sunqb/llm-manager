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
-- =============================================

-- 聊天历史表
CREATE TABLE IF NOT EXISTS a_chat_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    conversation_id VARCHAR(255) NOT NULL COMMENT '会话ID',
    message_index INT NOT NULL DEFAULT 0 COMMENT '消息序号（同一会话内从0开始递增）',
    message_type VARCHAR(20) NOT NULL COMMENT '消息类型：SYSTEM/USER/ASSISTANT/TOOL',
    content TEXT NOT NULL COMMENT '消息内容',
    metadata TEXT COMMENT '元数据（JSON格式）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    is_delete TINYINT(3) UNSIGNED DEFAULT 0 COMMENT '是否删除，0：正常，1：删除',
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_create_time (create_time),
    INDEX idx_is_delete (is_delete),
    UNIQUE INDEX uk_conv_msg_idx (conversation_id, message_index) COMMENT '会话ID+消息序号唯一约束'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天历史表';
