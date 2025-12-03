package com.llmmanager.agent.storage.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.llmmanager.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 对话轮次实体
 *
 * 表示一次完整的问答（Turn），包含：
 * - 用户消息（USER）
 * - 助手回复（ASSISTANT）
 * - 可能的工具调用消息（TOOL）
 *
 * 命名规范：
 * - id：主键（自增整数）
 * - turnCode：轮次唯一标识（32位UUID）
 * - conversationCode：关联的会话标识
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "a_conversation_turns", autoResultMap = true)
public class ConversationTurn extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Turn唯一标识（32位UUID）
     */
    private String turnCode;

    /**
     * 会话标识
     */
    private String conversationCode;

    /**
     * 轮次序号（从0开始）
     */
    private Integer turnIndex;

    // ==================== 关联的消息 ====================

    /**
     * 用户消息标识
     */
    private String userMessageCode;

    /**
     * 助手消息标识
     */
    private String assistantMessageCode;

    // ==================== Token 统计 ====================

    /**
     * 输入token数
     */
    private Integer promptTokens;

    /**
     * 输出token数
     */
    private Integer completionTokens;

    /**
     * 总token数
     */
    private Integer totalTokens;

    // ==================== 性能指标 ====================

    /**
     * 响应耗时(毫秒)
     */
    private Integer latencyMs;

    /**
     * 首token耗时(毫秒)
     */
    private Integer firstTokenMs;

    // ==================== 状态 ====================

    /**
     * 状态：PENDING/PROCESSING/SUCCESS/FAILED/TIMEOUT
     */
    private String status;

    /**
     * 错误信息
     */
    private String errorMessage;

    // ==================== 模型信息 ====================

    /**
     * 使用的模型ID
     */
    private Long modelId;

    /**
     * 模型标识符
     */
    private String modelIdentifier;

    // ==================== 时间 ====================

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    // ==================== 元数据 ====================

    /**
     * 额外元数据（JSON格式）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    // ==================== 状态常量 ====================

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_TIMEOUT = "TIMEOUT";

    // ==================== 静态工厂方法 ====================

    /**
     * 生成Turn唯一标识（32位无连字符的UUID）
     */
    public static String generateTurnCode() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 创建新的Turn（开始状态）
     */
    public static ConversationTurn create(String conversationCode, int turnIndex) {
        ConversationTurn turn = new ConversationTurn();
        turn.setTurnCode(generateTurnCode());
        turn.setConversationCode(conversationCode);
        turn.setTurnIndex(turnIndex);
        turn.setStatus(STATUS_PENDING);
        turn.setStartTime(LocalDateTime.now());
        turn.setPromptTokens(0);
        turn.setCompletionTokens(0);
        turn.setTotalTokens(0);
        turn.setLatencyMs(0);
        return turn;
    }

    /**
     * 创建新的Turn（带模型信息）
     */
    public static ConversationTurn create(String conversationCode, int turnIndex, Long modelId, String modelIdentifier) {
        ConversationTurn turn = create(conversationCode, turnIndex);
        turn.setModelId(modelId);
        turn.setModelIdentifier(modelIdentifier);
        return turn;
    }

    // ==================== 业务方法 ====================

    /**
     * 标记为处理中
     */
    public void markProcessing() {
        this.status = STATUS_PROCESSING;
    }

    /**
     * 标记为成功
     */
    public void markSuccess(int promptTokens, int completionTokens, long latencyMs) {
        this.status = STATUS_SUCCESS;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = promptTokens + completionTokens;
        this.latencyMs = (int) latencyMs;
        this.endTime = LocalDateTime.now();
    }

    /**
     * 标记为失败
     */
    public void markFailed(String errorMessage) {
        this.status = STATUS_FAILED;
        this.errorMessage = errorMessage;
        this.endTime = LocalDateTime.now();
    }

    /**
     * 标记为超时
     */
    public void markTimeout() {
        this.status = STATUS_TIMEOUT;
        this.errorMessage = "Request timeout";
        this.endTime = LocalDateTime.now();
    }

    /**
     * 设置用户消息
     */
    public void setUserMessage(String userMessageCode) {
        this.userMessageCode = userMessageCode;
    }

    /**
     * 设置助手消息
     */
    public void setAssistantMessage(String assistantMessageCode) {
        this.assistantMessageCode = assistantMessageCode;
    }

    /**
     * 记录首token耗时
     */
    public void recordFirstToken(long firstTokenMs) {
        this.firstTokenMs = (int) firstTokenMs;
    }

    /**
     * 是否成功
     */
    public boolean isSuccess() {
        return STATUS_SUCCESS.equals(this.status);
    }

    /**
     * 是否失败
     */
    public boolean isFailed() {
        return STATUS_FAILED.equals(this.status) || STATUS_TIMEOUT.equals(this.status);
    }
}
