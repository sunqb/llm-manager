package com.llmmanager.agent.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.llmmanager.agent.graph.workflow.DeepResearchWorkflow;
import com.llmmanager.agent.graph.workflow.DeepResearchWorkflow.ResearchProgress;
import com.llmmanager.agent.graph.workflow.DeepResearchWorkflow.ResearchResult;
import com.llmmanager.agent.review.snapshot.GraphStateSnapshot;
import com.llmmanager.agent.storage.core.entity.PendingReview;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Graph 工作流执行器（通用执行层）
 *
 * 提供两类能力：
 * 1. 通用执行方法（供所有 Graph 工作流复用）：
 *    - execute(CompiledGraph, initialState) - 同步执行任意 CompiledGraph
 *    - executeStream(CompiledGraph, initialState) - 流式执行任意 CompiledGraph
 *
 * 2. DeepResearch 专用方法（保持向后兼容）：
 *    - deepResearch(chatClient, cacheKey, question)
 *    - deepResearchStream(chatClient, cacheKey, question)
 *
 * 职责：业务编排层 - 执行工作流
 * 区别于 storage.core.service.GraphWorkflowService（数据访问层 - CRUD）
 */
@Slf4j
@Service
public class GraphWorkflowExecutor {

    private final Map<String, DeepResearchWorkflow> deepResearchCache = new ConcurrentHashMap<>();
    private final Map<String, CompiledGraph> compiledGraphCache = new ConcurrentHashMap<>();

    // ==================== 通用执行方法（供所有 Graph 工作流复用） ====================

    /**
     * 同步执行 CompiledGraph（公共方法）
     *
     * @param compiledGraph 已编译的工作流图
     * @param initialState  初始状态
     * @return 执行结果（包含最终状态）
     */
    public Map<String, Object> execute(CompiledGraph compiledGraph, Map<String, Object> initialState) {
        log.info("[GraphWorkflowExecutor] 开始同步执行工作流");

        RunnableConfig config = RunnableConfig.builder()
                .threadId(UUID.randomUUID().toString())
                .build();

        Map<String, Object> result = new HashMap<>();
        try {
            Optional<OverAllState> stateResult = compiledGraph.invoke(initialState, config);

            if (stateResult.isPresent()) {
                Map<String, Object> finalState = stateResult.get().data();
                log.info("[GraphWorkflowExecutor] 工作流执行成功，最终状态键: {}", finalState.keySet());
                result.put("success", true);
                result.put("data", finalState);
            } else {
                log.warn("[GraphWorkflowExecutor] 工作流执行返回空结果");
                result.put("success", false);
                result.put("error", "工作流执行返回空结果");
            }
        } catch (Exception e) {
            log.error("[GraphWorkflowExecutor] 工作流执行失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 流式执行 CompiledGraph（公共方法）
     *
     * @param compiledGraph 已编译的工作流图
     * @param initialState  初始状态
     * @return 节点输出流
     */
    public Flux<NodeOutput> executeStream(CompiledGraph compiledGraph, Map<String, Object> initialState) {
        log.info("[GraphWorkflowExecutor] 开始流式执行工作流");

        RunnableConfig config = RunnableConfig.builder()
                .threadId(UUID.randomUUID().toString())
                .build();

        return compiledGraph.stream(initialState, config);
    }

    /**
     * 带缓存的同步执行（公共方法）
     *
     * @param compiledGraph 已编译的工作流图
     * @param cacheKey      缓存键
     * @param initialState  初始状态
     * @return 执行结果
     */
    public Map<String, Object> executeWithCache(CompiledGraph compiledGraph, String cacheKey,
                                                  Map<String, Object> initialState) {
        // 缓存 CompiledGraph
        compiledGraphCache.putIfAbsent(cacheKey, compiledGraph);
        return execute(compiledGraph, initialState);
    }

    /**
     * 从缓存获取并执行（公共方法）
     *
     * @param cacheKey     缓存键
     * @param initialState 初始状态
     * @return 执行结果，如果缓存不存在返回错误
     */
    public Map<String, Object> executeFromCache(String cacheKey, Map<String, Object> initialState) {
        CompiledGraph cached = compiledGraphCache.get(cacheKey);
        if (cached == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "缓存中不存在工作流: " + cacheKey);
            return error;
        }
        return execute(cached, initialState);
    }

    // ==================== DeepResearch 专用方法（保持向后兼容） ====================

    /**
     * 执行深度研究（同步）
     *
     * @param chatClient ChatClient 实例
     * @param cacheKey   缓存键（用于复用工作流实例）
     * @param question   研究问题
     * @return 研究结果
     */
    public ResearchResult deepResearch(ChatClient chatClient, String cacheKey, String question) {
        log.info("[GraphWorkflowExecutor] 开始深度研究: {}", question);
        DeepResearchWorkflow workflow = getOrCreateDeepResearchWorkflow(chatClient, cacheKey);
        return workflow.research(question);
    }

    /**
     * 执行深度研究（流式）
     *
     * @param chatClient ChatClient 实例
     * @param cacheKey   缓存键（用于复用工作流实例）
     * @param question   研究问题
     * @return 研究进度流
     */
    public Flux<ResearchProgress> deepResearchStream(ChatClient chatClient, String cacheKey, String question) {
        log.info("[GraphWorkflowExecutor] 开始流式深度研究: {}", question);
        DeepResearchWorkflow workflow = getOrCreateDeepResearchWorkflow(chatClient, cacheKey);
        return workflow.researchStream(question);
    }

    /**
     * 获取或创建 DeepResearch 工作流实例
     */
    private DeepResearchWorkflow getOrCreateDeepResearchWorkflow(ChatClient chatClient, String cacheKey) {
        return deepResearchCache.computeIfAbsent(cacheKey, k -> new DeepResearchWorkflow(chatClient, 3));
    }

    // ==================== 缓存管理 ====================

    /**
     * 清除指定渠道的缓存
     */
    public void clearCacheForChannel(Long channelId) {
        String prefix = channelId + "_";
        deepResearchCache.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
        compiledGraphCache.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
        log.info("[GraphWorkflowExecutor] 已清除渠道 {} 的缓存", channelId);
    }

    /**
     * 清除所有缓存
     */
    public void clearAllCache() {
        deepResearchCache.clear();
        compiledGraphCache.clear();
        log.info("[GraphWorkflowExecutor] 已清除所有缓存");
    }

    // ==================== 人工审核恢复执行 ====================

    /**
     * 从审核记录恢复工作流执行
     *
     * 设计说明：
     * 由于 Spring AI Alibaba 的 StateGraph 不支持从中间节点恢复执行，
     * 采用以下策略：
     * 1. 从快照恢复状态值
     * 2. 将审核结果写入状态（如 review_result=APPROVED）
     * 3. 重新执行工作流（从 START 开始）
     * 4. 工作流配置应使用条件边跳过已完成的节点
     *
     * 工作流配置建议：
     * - 在审核节点后使用 CONDITIONAL 边
     * - condition_field 设置为审核结果的 output_key
     * - 提供 APPROVED/REJECTED 的路由规则
     *
     * @param review   审核记录（包含快照和审核结果）
     * @param snapshot 状态快照
     * @return 执行结果
     */
    public Map<String, Object> resumeFromReview(PendingReview review, GraphStateSnapshot snapshot) {
        log.info("[GraphWorkflowExecutor] 开始恢复执行，reviewCode: {}, 节点: {}",
                review.getReviewCode(), snapshot.getCurrentNodeId());

        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 验证审核状态
            if (!review.isApproved()) {
                log.warn("[GraphWorkflowExecutor] 审核未通过，无法恢复执行: {}", review.getReviewCode());
                result.put("success", false);
                result.put("error", "审核未通过，无法恢复执行");
                return result;
            }

            // 2. 从快照恢复状态
            Map<String, Object> restoredState = new HashMap<>();
            if (snapshot.getStateValues() != null) {
                restoredState.putAll(snapshot.getStateValues());
            }

            // 3. 写入审核结果到状态
            String reviewResult = review.getReviewResult() != null && review.getReviewResult() ? "APPROVED" : "REJECTED";
            restoredState.put("review_result", reviewResult);
            restoredState.put("review_comment", review.getReviewComment());
            restoredState.put("review_code", review.getReviewCode());

            // 4. 查找缓存的 CompiledGraph
            String cacheKey = buildCacheKey(snapshot);
            CompiledGraph compiledGraph = compiledGraphCache.get(cacheKey);

            if (compiledGraph == null) {
                log.warn("[GraphWorkflowExecutor] 缓存中不存在工作流，需要重新构建: {}", cacheKey);
                result.put("success", false);
                result.put("error", "缓存中不存在工作流，需要重新构建: " + cacheKey);
                result.put("needsRebuild", true);
                result.put("restoredState", restoredState);
                return result;
            }

            // 5. 重新执行工作流
            log.info("[GraphWorkflowExecutor] 使用恢复的状态重新执行工作流，状态键: {}", restoredState.keySet());
            result = execute(compiledGraph, restoredState);

            if (Boolean.TRUE.equals(result.get("success"))) {
                log.info("[GraphWorkflowExecutor] 工作流恢复执行成功，reviewCode: {}", review.getReviewCode());
            }

            return result;

        } catch (Exception e) {
            log.error("[GraphWorkflowExecutor] 恢复执行失败，reviewCode: {}", review.getReviewCode(), e);
            result.put("success", false);
            result.put("error", "恢复执行失败: " + e.getMessage());
            return result;
        }
    }

    /**
     * 恢复执行（使用快照重建状态，并将审核结果写入指定的 output_key）
     *
     * @param review    审核记录
     * @param snapshot  状态快照
     * @param outputKey 审核结果写入的状态 key
     * @return 执行结果
     */
    public Map<String, Object> resumeFromReview(PendingReview review, GraphStateSnapshot snapshot, String outputKey) {
        log.info("[GraphWorkflowExecutor] 开始恢复执行（指定 outputKey: {}），reviewCode: {}",
                outputKey, review.getReviewCode());

        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 验证审核状态
            if (!review.isApproved()) {
                log.warn("[GraphWorkflowExecutor] 审核未通过，无法恢复执行: {}", review.getReviewCode());
                result.put("success", false);
                result.put("error", "审核未通过，无法恢复执行");
                return result;
            }

            // 2. 从快照恢复状态
            Map<String, Object> restoredState = new HashMap<>();
            if (snapshot.getStateValues() != null) {
                restoredState.putAll(snapshot.getStateValues());
            }

            // 3. 写入审核结果到指定的 outputKey
            String reviewResult = review.getReviewResult() != null && review.getReviewResult() ? "APPROVED" : "REJECTED";
            restoredState.put(outputKey, reviewResult);
            restoredState.put("next_node", reviewResult);  // 用于条件边路由
            restoredState.put("review_comment", review.getReviewComment());
            restoredState.put("review_code", review.getReviewCode());

            // 4. 查找缓存的 CompiledGraph
            String cacheKey = buildCacheKey(snapshot);
            CompiledGraph compiledGraph = compiledGraphCache.get(cacheKey);

            if (compiledGraph == null) {
                log.warn("[GraphWorkflowExecutor] 缓存中不存在工作流，需要重新构建: {}", cacheKey);
                result.put("success", false);
                result.put("error", "缓存中不存在工作流，需要重新构建: " + cacheKey);
                result.put("needsRebuild", true);
                result.put("restoredState", restoredState);
                return result;
            }

            // 5. 重新执行工作流
            log.info("[GraphWorkflowExecutor] 使用恢复的状态重新执行工作流，状态键: {}", restoredState.keySet());
            result = execute(compiledGraph, restoredState);

            if (Boolean.TRUE.equals(result.get("success"))) {
                log.info("[GraphWorkflowExecutor] 工作流恢复执行成功，reviewCode: {}", review.getReviewCode());
            }

            return result;

        } catch (Exception e) {
            log.error("[GraphWorkflowExecutor] 恢复执行失败，reviewCode: {}", review.getReviewCode(), e);
            result.put("success", false);
            result.put("error", "恢复执行失败: " + e.getMessage());
            return result;
        }
    }

    /**
     * 从快照构建缓存键
     */
    private String buildCacheKey(GraphStateSnapshot snapshot) {
        // 使用 graphTaskId 或工作流类型作为缓存键
        if (snapshot.getGraphTaskId() != null) {
            return "graph_task_" + snapshot.getGraphTaskId();
        }
        if (snapshot.getWorkflowType() != null) {
            return "workflow_" + snapshot.getWorkflowType();
        }
        return "default";
    }

    /**
     * 缓存 CompiledGraph（供恢复执行时使用）
     *
     * @param cacheKey      缓存键
     * @param compiledGraph 编译后的工作流图
     */
    public void cacheCompiledGraph(String cacheKey, CompiledGraph compiledGraph) {
        compiledGraphCache.put(cacheKey, compiledGraph);
        log.debug("[GraphWorkflowExecutor] 已缓存工作流: {}", cacheKey);
    }

    /**
     * 根据 graphTaskId 缓存 CompiledGraph
     *
     * @param graphTaskId   Graph 任务 ID
     * @param compiledGraph 编译后的工作流图
     */
    public void cacheCompiledGraphByTaskId(Long graphTaskId, CompiledGraph compiledGraph) {
        String cacheKey = "graph_task_" + graphTaskId;
        cacheCompiledGraph(cacheKey, compiledGraph);
    }
}
