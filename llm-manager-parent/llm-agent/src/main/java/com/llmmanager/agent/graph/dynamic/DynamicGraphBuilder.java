package com.llmmanager.agent.graph.dynamic;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.llmmanager.agent.graph.dynamic.dto.*;
import com.llmmanager.agent.graph.dynamic.executor.NodeExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;

/**
 * 动态 Graph 构建引擎（核心）
 *
 * 功能：根据 JSON 配置动态构建 Spring AI Alibaba 的 StateGraph
 *
 * 设计理念：
 * - 符合 Spring AI Alibaba 官方推荐：声明式 API、配置驱动、状态驱动路由
 * - 支持简单边和条件边
 * - 自动注入所有 NodeExecutor（策略模式）
 */
@Slf4j
@Component
public class DynamicGraphBuilder {

    /**
     * 所有节点执行器（Spring 自动注入）
     * Key: Bean 名称（如 "LlmNodeExecutor"）
     * Value: NodeExecutor 实例
     */
    private final Map<String, NodeExecutor> nodeExecutors;

    @Autowired
    public DynamicGraphBuilder(Map<String, NodeExecutor> nodeExecutors) {
        this.nodeExecutors = nodeExecutors;
        log.info("[DynamicGraphBuilder] 初始化完成，已注册 {} 个节点执行器: {}",
                nodeExecutors.size(), nodeExecutors.keySet());
    }

    /**
     * 根据配置构建 CompiledGraph
     *
     * @param config     工作流配置
     * @param chatClient ChatClient 实例
     * @return 编译后的 CompiledGraph
     * @throws GraphStateException 如果构建失败
     */
    public CompiledGraph build(GraphWorkflowConfig config, ChatClient chatClient) throws GraphStateException {
        log.info("[DynamicGraphBuilder] 开始构建工作流: {}", config.getName());

        // 1. 验证配置
        config.validate();

        // 2. 创建 KeyStrategyFactory
        KeyStrategyFactory keyStrategyFactory = createKeyStrategyFactory(config.getStateConfig());

        // 3. 创建 StateGraph
        StateGraph stateGraph = new StateGraph(config.getName(), keyStrategyFactory);

        // 4. 添加节点
        for (NodeConfig nodeConfig : config.getNodes()) {
            addNode(stateGraph, nodeConfig, chatClient);
        }

        // 5. 添加边
        for (EdgeConfig edgeConfig : config.getEdges()) {
            addEdge(stateGraph, edgeConfig);
        }

        // 6. 编译
        CompiledGraph compiledGraph = stateGraph.compile();
        log.info("[DynamicGraphBuilder] 工作流构建成功: {}", config.getName());
        return compiledGraph;
    }

    /**
     * 创建 KeyStrategyFactory
     *
     * 新版本 Spring AI Alibaba 使用 KeyStrategyFactory 替代 OverAllStateFactory
     */
    private KeyStrategyFactory createKeyStrategyFactory(GraphWorkflowConfig.StateConfig stateConfig) {
        return () -> {
            java.util.Map<String, com.alibaba.cloud.ai.graph.KeyStrategy> strategies = new java.util.HashMap<>();

            // 注册所有状态键及其更新策略
            for (StateKeyConfig keyConfig : stateConfig.getKeys()) {
                if (keyConfig.isAppend()) {
                    strategies.put(keyConfig.getKey(), new AppendStrategy());
                    log.debug("[DynamicGraphBuilder] 注册状态键: {} (APPEND)", keyConfig.getKey());
                } else {
                    strategies.put(keyConfig.getKey(), new ReplaceStrategy());
                    log.debug("[DynamicGraphBuilder] 注册状态键: {} (REPLACE)", keyConfig.getKey());
                }
            }

            // 必须注册 next_node 键（用于条件路由）
            if (stateConfig.getKeys().stream().noneMatch(k -> "next_node".equals(k.getKey()))) {
                strategies.put("next_node", new ReplaceStrategy());
                log.debug("[DynamicGraphBuilder] 自动注册状态键: next_node (REPLACE)");
            }

            return strategies;
        };
    }

    /**
     * 添加节点
     */
    private void addNode(StateGraph stateGraph, NodeConfig nodeConfig, ChatClient chatClient) throws GraphStateException {
        log.debug("[DynamicGraphBuilder] 添加节点: {} (类型: {})", nodeConfig.getId(), nodeConfig.getType());

        // 查找对应的节点执行器
        NodeExecutor executor = findExecutor(nodeConfig.getType());

        // 创建节点动作
        AsyncNodeAction action = executor.createAction(nodeConfig, chatClient);

        // 添加到 StateGraph
        stateGraph.addNode(nodeConfig.getId(), action);
    }

    /**
     * 添加边
     */
    private void addEdge(StateGraph stateGraph, EdgeConfig edgeConfig) throws GraphStateException {
        // 转换 START/END 字符串为 StateGraph 常量
        String from = convertNodeId(edgeConfig.getFrom());
        String to = convertNodeId(edgeConfig.getTo());

        log.debug("[DynamicGraphBuilder] 添加边: {} -> {} (类型: {})",
                from, to, edgeConfig.getType());

        if (edgeConfig.isSimple()) {
            // 简单边：固定连接
            stateGraph.addEdge(from, to);
        } else if (edgeConfig.isConditional()) {
            // 条件边：从状态中读取 next_node
            AsyncEdgeAction edgeAction = createConditionalEdgeAction(edgeConfig);
            stateGraph.addConditionalEdges(
                    from,
                    edgeAction,
                    convertRoutes(edgeConfig.getRoutes())
            );
        }
    }

    /**
     * 转换节点 ID（处理 START/END 特殊值）
     */
    private String convertNodeId(String nodeId) {
        if (nodeId == null) {
            return null;
        }
        if ("START".equals(nodeId)) {
            return START;
        }
        if ("END".equals(nodeId)) {
            return END;
        }
        return nodeId;
    }

    /**
     * 转换路由映射中的节点 ID
     */
    private Map<String, String> convertRoutes(Map<String, String> routes) {
        if (routes == null) {
            return null;
        }
        return routes.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> convertNodeId(e.getValue())
                ));
    }

    /**
     * 创建条件边动作（状态驱动路由）
     */
    private AsyncEdgeAction createConditionalEdgeAction(EdgeConfig edgeConfig) {
        return (OverAllState state) -> CompletableFuture.supplyAsync(() -> {
            // 从状态中读取 next_node（由 ConditionNodeExecutor 写入）
            String nextNode = state.value("next_node")
                    .map(Object::toString)
                    .orElse(END);

            log.debug("[DynamicGraphBuilder] 条件路由决策: {}", nextNode);
            return nextNode;
        });
    }

    /**
     * 查找节点执行器
     */
    private NodeExecutor findExecutor(String nodeType) {
        // 尝试按类型查找（如 "LLM_NODE" -> "LlmNodeExecutor"）
        NodeExecutor executor = nodeExecutors.values().stream()
                .filter(e -> e.getNodeType().equals(nodeType))
                .findFirst()
                .orElse(null);

        if (executor == null) {
            // 尝试按 Bean 名称查找
            String beanName = convertTypeToBeanName(nodeType);
            executor = nodeExecutors.get(beanName);
        }

        if (executor == null) {
            throw new IllegalArgumentException("未找到节点类型的执行器: " + nodeType +
                    "，已注册的执行器: " + nodeExecutors.keySet());
        }

        return executor;
    }

    /**
     * 将节点类型转换为 Bean 名称
     * 例如：LLM_NODE -> LlmNodeExecutor
     */
    private String convertTypeToBeanName(String nodeType) {
        if (nodeType == null) {
            return null;
        }

        // 简单实现：LLM_NODE -> LlmNodeExecutor
        String[] parts = nodeType.split("_");
        StringBuilder beanName = new StringBuilder();

        for (String part : parts) {
            if (!part.isEmpty()) {
                beanName.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1).toLowerCase());
            }
        }

        beanName.append("Executor");
        return beanName.toString();
    }

    /**
     * 获取所有已注册的节点类型
     */
    public Map<String, String> getRegisteredNodeTypes() {
        return nodeExecutors.values().stream()
                .collect(java.util.stream.Collectors.toMap(
                        NodeExecutor::getNodeType,
                        NodeExecutor::getDescription
                ));
    }
}
