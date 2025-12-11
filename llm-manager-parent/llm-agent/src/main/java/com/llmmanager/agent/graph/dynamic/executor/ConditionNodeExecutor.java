package com.llmmanager.agent.graph.dynamic.executor;

import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.llmmanager.agent.graph.dynamic.dto.NodeConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 条件路由节点执行器
 *
 * 功能：根据状态值决定下一步路由（实现 Spring AI Alibaba 官方推荐的状态驱动路由）
 *
 * 配置参数：
 * - condition_field (必需): 条件判断的状态字段名
 * - routes (必需): 路由映射（值 -> 节点ID），格式：{"value1": "node_id_1", "value2": "node_id_2"}
 * - default_route (可选): 默认路由（当条件值不匹配任何路由时使用，默认为 "END"）
 *
 * 工作原理：
 * 1. 从状态中读取 condition_field 的值
 * 2. 在 routes 中查找匹配的路由
 * 3. 将路由结果写入状态的 "next_node" 键（供 DynamicGraphBuilder 使用）
 */
@Slf4j
@Component("ConditionNodeExecutor")
public class ConditionNodeExecutor implements NodeExecutor {

    @Override
    public String getNodeType() {
        return "CONDITION_NODE";
    }

    @Override
    public String getDescription() {
        return "条件路由节点 - 根据状态值决定下一步路由";
    }

    @Override
    @SuppressWarnings("unchecked")
    public AsyncNodeAction createAction(NodeConfig nodeConfig, ChatClient chatClient) {
        // 提取配置参数
        Map<String, Object> config = nodeConfig.getConfig();
        String conditionField = (String) config.get("condition_field");
        Map<String, String> routes = (Map<String, String>) config.get("routes");
        String defaultRoute = (String) config.getOrDefault("default_route", "END");

        // 参数验证
        if (conditionField == null || conditionField.trim().isEmpty()) {
            throw new IllegalArgumentException("条件节点配置缺少 condition_field 参数");
        }
        if (routes == null || routes.isEmpty()) {
            throw new IllegalArgumentException("条件节点配置缺少 routes 参数");
        }

        // 返回异步节点动作
        return (OverAllState state) -> CompletableFuture.supplyAsync(() -> {
            try {
                log.info("[ConditionNodeExecutor] 节点 '{}' 开始执行", nodeConfig.getId());

                // 1. 从状态中读取条件字段的值
                Object conditionValue = state.value(conditionField).orElse(null);
                log.debug("[ConditionNodeExecutor] 条件字段 '{}' 的值: {}", conditionField, conditionValue);

                // 2. 查找匹配的路由
                String nextNode;
                if (conditionValue != null) {
                    String conditionStr = conditionValue.toString();
                    nextNode = routes.getOrDefault(conditionStr, defaultRoute);
                    log.info("[ConditionNodeExecutor] 条件值 '{}' 匹配路由: {}", conditionStr, nextNode);
                } else {
                    nextNode = defaultRoute;
                    log.warn("[ConditionNodeExecutor] 条件字段 '{}' 值为 null，使用默认路由: {}",
                            conditionField, defaultRoute);
                }

                // 3. 更新状态（写入 next_node）
                Map<String, Object> updates = new HashMap<>();
                updates.put("next_node", nextNode);  // 官方推荐方式
                updates.put("current_node", nodeConfig.getId());

                log.info("[ConditionNodeExecutor] 节点 '{}' 执行成功，下一步: {}", nodeConfig.getId(), nextNode);
                return updates;

            } catch (Exception e) {
                log.error("[ConditionNodeExecutor] 节点 '{}' 执行失败", nodeConfig.getId(), e);

                // 返回错误信息
                Map<String, Object> updates = new HashMap<>();
                updates.put("error_message", "条件路由失败: " + e.getMessage());
                updates.put("next_node", defaultRoute);  // 失败时使用默认路由
                updates.put("current_node", nodeConfig.getId());
                return updates;
            }
        });
    }

    @Override
    public String getConfigSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "condition_field": {
                      "type": "string",
                      "description": "条件判断的状态字段名"
                    },
                    "routes": {
                      "type": "object",
                      "description": "路由映射（值 -> 节点ID），例如：{\\"approved\\": \\"node_2\\", \\"rejected\\": \\"node_3\\"}"
                    },
                    "default_route": {
                      "type": "string",
                      "description": "默认路由（当条件值不匹配任何路由时使用），默认为 END",
                      "default": "END"
                    }
                  },
                  "required": ["condition_field", "routes"]
                }
                """;
    }
}
