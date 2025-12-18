package com.llmmanager.agent.reactagent.configurable.config;

import com.llmmanager.agent.reactagent.configurable.WorkflowPattern;
import lombok.Builder;
import lombok.Data;
import org.springframework.ai.chat.model.ChatModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 工作流配置
 * 
 * 定义多 Agent 协作的配置，包括：
 * - 协作模式（Sequential/Parallel/Routing/Loop）
 * - 参与的 Agent 列表
 * - 模式特定的配置参数
 * 
 * 使用示例：
 * <pre>{@code
 * // 顺序执行配置
 * AgentWorkflowConfig config = AgentWorkflowConfig.builder()
 *     .pattern(WorkflowPattern.SEQUENTIAL)
 *     .agents(List.of(
 *         AgentConfig.of("researcher", researchAgent),
 *         AgentConfig.of("analyzer", analyzeAgent),
 *         AgentConfig.of("writer", writeAgent)
 *     ))
 *     .build();
 * 
 * // 并行执行配置
 * AgentWorkflowConfig config = AgentWorkflowConfig.builder()
 *     .pattern(WorkflowPattern.PARALLEL)
 *     .agents(List.of(
 *         AgentConfig.of("tech-analyst", techAgent),
 *         AgentConfig.of("market-analyst", marketAgent),
 *         AgentConfig.of("risk-analyst", riskAgent)
 *     ))
 *     .parallelMergePrompt("请综合以上分析结果，给出最终建议")
 *     .build();
 * 
 * // LLM 路由配置
 * AgentWorkflowConfig config = AgentWorkflowConfig.builder()
 *     .pattern(WorkflowPattern.ROUTING)
 *     .routingChatModel(chatModel)
 *     .agents(List.of(
 *         AgentConfig.of("tech-support", techAgent, "处理技术问题"),
 *         AgentConfig.of("sales", salesAgent, "处理销售咨询"),
 *         AgentConfig.of("general", generalAgent, "处理其他问题")
 *     ))
 *     .build();
 * }</pre>
 * 
 * @author LLM Manager
 */
@Data
@Builder
public class AgentWorkflowConfig {

    /**
     * 工作流模式
     */
    private WorkflowPattern pattern;

    /**
     * 参与的 Agent 列表
     */
    @Builder.Default
    private List<AgentConfig> agents = new ArrayList<>();

    // ========== 顺序执行模式配置 ==========

    /**
     * 是否将前一个 Agent 的输出作为后一个的输入
     * 默认 true
     */
    @Builder.Default
    private boolean chainOutput = true;

    // ========== 并行执行模式配置 ==========

    /**
     * 并行执行后的结果合并提示词
     * 用于指导 LLM 如何合并多个 Agent 的结果
     */
    private String parallelMergePrompt;

    /**
     * 用于合并结果的 ChatModel
     */
    private ChatModel mergeChatModel;

    // ========== LLM 路由模式配置 ==========

    /**
     * 用于路由决策的 ChatModel
     */
    private ChatModel routingChatModel;

    /**
     * 路由决策的系统提示词
     */
    private String routingSystemPrompt;

    /**
     * 是否允许路由到多个 Agent
     * 默认 false（只路由到一个）
     */
    @Builder.Default
    private boolean multiRouting = false;

    // ========== 循环执行模式配置 ==========

    /**
     * 最大循环次数
     */
    @Builder.Default
    private int maxLoopIterations = 10;

    /**
     * 循环终止条件判断提示词
     */
    private String loopTerminationPrompt;

    /**
     * 用于判断循环终止的 ChatModel
     */
    private ChatModel loopChatModel;

    // ========== 通用配置 ==========

    /**
     * 工作流名称
     */
    private String workflowName;

    /**
     * 工作流描述
     */
    private String workflowDescription;

    /**
     * 全局超时时间（毫秒）
     */
    @Builder.Default
    private long globalTimeoutMs = 300000;  // 5分钟

    /**
     * 是否启用详细日志
     */
    @Builder.Default
    private boolean verboseLogging = false;
}

