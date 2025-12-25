package com.llmmanager.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用响应码枚举
 * <p>
 * 编码规范：
 * - 200: 成功
 * - 300: 警告（业务警告，非错误）
 * - 400: 客户端错误（参数错误、资源不存在等）
 * - 401: 未授权
 * - 403: 禁止访问
 * - 500: 系统内部错误
 * - 1xxx: 通用业务错误
 * - 2xxx: 用户模块错误
 * - 3xxx: Channel/Model 模块错误
 * - 4xxx: Agent 模块错误
 * - 5xxx: RAG/知识库模块错误
 * - 6xxx: MCP 模块错误
 * - 7xxx: 工作流模块错误
 */
@Getter
@AllArgsConstructor
public enum ResultCode implements ErrorCode {

    // ==================== 基础响应码 ====================
    SUCCESS(200, "成功"),
    WARN(300, "警告"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权，请先登录"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    SYSTEM_ERROR(500, "系统内部错误"),

    // ==================== 通用业务错误 1xxx ====================
    PARAM_ERROR(1001, "参数校验失败"),
    PARAM_MISSING(1002, "缺少必要参数"),
    DATA_NOT_FOUND(1003, "数据不存在"),
    DATA_ALREADY_EXISTS(1004, "数据已存在"),
    DATA_SAVE_FAILED(1005, "数据保存失败"),
    DATA_UPDATE_FAILED(1006, "数据更新失败"),
    DATA_DELETE_FAILED(1007, "数据删除失败"),
    OPERATION_FAILED(1008, "操作失败"),
    CONCURRENT_UPDATE(1009, "数据已被其他人修改，请刷新后重试"),

    // ==================== 用户模块 2xxx ====================
    USER_NOT_FOUND(2001, "用户不存在"),
    USER_PASSWORD_ERROR(2002, "密码错误"),
    USER_DISABLED(2003, "用户已被禁用"),
    USER_ALREADY_EXISTS(2004, "用户已存在"),
    TOKEN_INVALID(2005, "Token 无效"),
    TOKEN_EXPIRED(2006, "Token 已过期"),
    LOGIN_FAILED(2007, "登录失败"),

    // ==================== Channel/Model 模块 3xxx ====================
    CHANNEL_NOT_FOUND(3001, "渠道不存在"),
    CHANNEL_DISABLED(3002, "渠道已被禁用"),
    CHANNEL_CONFIG_ERROR(3003, "渠道配置错误"),
    MODEL_NOT_FOUND(3004, "模型不存在"),
    MODEL_DISABLED(3005, "模型已被禁用"),
    MODEL_CALL_FAILED(3006, "模型调用失败"),
    MODEL_TIMEOUT(3007, "模型调用超时"),
    API_KEY_INVALID(3008, "API Key 无效"),
    API_KEY_EXPIRED(3009, "API Key 已过期"),
    CHAT_FAILED(3010, "对话失败"),

    // ==================== Agent 模块 4xxx ====================
    AGENT_NOT_FOUND(4001, "Agent 不存在"),
    AGENT_DISABLED(4002, "Agent 已被禁用"),
    AGENT_CONFIG_ERROR(4003, "Agent 配置错误"),
    AGENT_EXECUTION_FAILED(4004, "Agent 执行失败"),
    TOOL_NOT_FOUND(4005, "工具不存在"),
    TOOL_EXECUTION_FAILED(4006, "工具执行失败"),
    REACT_AGENT_NOT_FOUND(4007, "ReactAgent 不存在"),
    REACT_AGENT_BUILD_FAILED(4008, "ReactAgent 构建失败"),

    // ==================== RAG/知识库模块 5xxx ====================
    KNOWLEDGE_BASE_NOT_FOUND(5001, "知识库不存在"),
    KNOWLEDGE_BASE_DISABLED(5002, "知识库已被禁用"),
    DOCUMENT_NOT_FOUND(5003, "文档不存在"),
    DOCUMENT_PROCESS_FAILED(5004, "文档处理失败"),
    VECTOR_STORE_ERROR(5005, "向量存储错误"),
    EMBEDDING_FAILED(5006, "文本向量化失败"),
    SEARCH_FAILED(5007, "检索失败"),

    // ==================== MCP 模块 6xxx ====================
    MCP_SERVER_NOT_FOUND(6001, "MCP 服务器不存在"),
    MCP_SERVER_DISABLED(6002, "MCP 服务器已被禁用"),
    MCP_CONNECTION_FAILED(6003, "MCP 连接失败"),
    MCP_TOOL_NOT_FOUND(6004, "MCP 工具不存在"),
    MCP_TOOL_CALL_FAILED(6005, "MCP 工具调用失败"),

    // ==================== 工作流模块 7xxx ====================
    WORKFLOW_NOT_FOUND(7001, "工作流不存在"),
    WORKFLOW_DISABLED(7002, "工作流已被禁用"),
    WORKFLOW_CONFIG_ERROR(7003, "工作流配置错误"),
    WORKFLOW_EXECUTION_FAILED(7004, "工作流执行失败"),
    WORKFLOW_NODE_ERROR(7005, "工作流节点错误"),
    GRAPH_BUILD_FAILED(7006, "Graph 构建失败");

    private final Integer code;
    private final String msg;
}
