package com.llmmanager.agent.graph.dynamic.executor;

import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.llmmanager.agent.graph.dynamic.dto.NodeConfig;
import com.llmmanager.agent.review.HumanReviewRecordService;
import com.llmmanager.agent.review.exception.HumanReviewRequiredException;
import com.llmmanager.agent.review.snapshot.GraphStateSnapshot;
import com.llmmanager.agent.storage.core.entity.PendingReview;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 人工审核节点执行器
 *
 * 功能：暂停工作流执行，等待人工审核
 *
 * 设计理念：
 * - 遵循异常驱动暂停机制（抛出 HumanReviewRequiredException）
 * - 支持模板化审核提示（{key} 占位符）
 * - 自动创建审核记录并保存状态快照
 *
 * 配置参数：
 * - prompt_template (必需): 审核提示模板，支持 {key} 占位符
 * - context_keys (必需): 需要展示给审核人的上下文字段列表（逗号分隔或数组）
 * - output_key (必需): 审核结果存储到状态的 key
 * - timeout_seconds (可选): 审核超时时间（秒）
 *
 * 工作流程：
 * 1. 从状态中提取 context_keys 对应的数据
 * 2. 格式化审核提示（替换 {key} 占位符）
 * 3. 创建状态快照
 * 4. 创建审核记录（调用 HumanReviewService）
 * 5. 抛出 HumanReviewRequiredException 暂停执行
 *
 * 恢复执行（由 HumanReviewService.resumeGraphWorkflow 处理）：
 * - 从快照恢复状态
 * - 将审核结果写入 output_key
 * - 继续下一个节点
 *
 * @author LLM Manager
 */
@Slf4j
@Component("HumanReviewNodeExecutor")
public class HumanReviewNodeExecutor implements NodeExecutor {

    @Resource
    private HumanReviewRecordService humanReviewRecordService;

    // 占位符匹配模式：{key}
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\w+)}");

    @Override
    public String getNodeType() {
        return "HUMAN_REVIEW_NODE";
    }

    @Override
    public String getDescription() {
        return "人工审核节点 - 暂停工作流等待人工审核";
    }

    @Override
    public AsyncNodeAction createAction(NodeConfig nodeConfig, ChatClient chatClient) {
        // 提取配置参数
        Map<String, Object> config = nodeConfig.getConfig();
        String promptTemplate = (String) config.get("prompt_template");
        String outputKey = (String) config.get("output_key");
        Object contextKeysObj = config.get("context_keys");
        Integer timeoutSeconds = config.get("timeout_seconds") != null
                ? ((Number) config.get("timeout_seconds")).intValue()
                : 3600; // 默认 1 小时

        // 参数验证
        if (promptTemplate == null || promptTemplate.trim().isEmpty()) {
            throw new IllegalArgumentException("人工审核节点配置缺少 prompt_template 参数");
        }
        if (outputKey == null || outputKey.trim().isEmpty()) {
            throw new IllegalArgumentException("人工审核节点配置缺少 output_key 参数");
        }

        // 解析 context_keys
        String[] contextKeys = parseContextKeys(contextKeysObj);

        // 返回异步节点动作
        return (OverAllState state) -> CompletableFuture.supplyAsync(() -> {
            log.info("[HumanReviewNodeExecutor] 节点 '{}' 开始执行，准备创建人工审核", nodeConfig.getId());

            try {
                // 1. 从状态中提取上下文数据
                Map<String, Object> contextData = extractContextData(state, contextKeys);
                log.debug("[HumanReviewNodeExecutor] 提取的上下文数据: {}", contextData);

                // 2. 格式化审核提示（替换 {key} 占位符）
                String formattedPrompt = formatPrompt(promptTemplate, state);
                log.info("[HumanReviewNodeExecutor] 格式化后的审核提示: {}", formattedPrompt);

                // 3. 获取 graphTaskId（从状态中获取，可选）
                Long graphTaskId = extractGraphTaskId(state);

                // 4. 创建状态快照
                GraphStateSnapshot snapshot = createSnapshot(state, nodeConfig, graphTaskId);

                // 5. 创建审核记录
                PendingReview review = humanReviewRecordService.createGraphNodeReview(
                        graphTaskId,
                        nodeConfig.getId(),
                        formattedPrompt,
                        snapshot
                );

                log.info("[HumanReviewNodeExecutor] 创建审核记录成功，reviewCode: {}", review.getReviewCode());

                // 6. 抛出异常暂停执行
                throw new HumanReviewRequiredException(
                        review.getReviewCode(),
                        formattedPrompt,
                        PendingReview.ReviewType.GRAPH_NODE.name()
                );

            } catch (HumanReviewRequiredException e) {
                // 重新抛出审核异常（不要吞掉）
                throw e;
            } catch (Exception e) {
                log.error("[HumanReviewNodeExecutor] 节点 '{}' 创建审核记录失败", nodeConfig.getId(), e);

                // 返回错误信息（不中断工作流，继续执行）
                Map<String, Object> updates = new HashMap<>();
                updates.put("error_message", "创建审核记录失败: " + e.getMessage());
                updates.put("current_node", nodeConfig.getId());
                updates.put(outputKey, "ERROR");
                return updates;
            }
        });
    }

    /**
     * 解析 context_keys 配置
     */
    private String[] parseContextKeys(Object contextKeysObj) {
        if (contextKeysObj == null) {
            return new String[0];
        }

        if (contextKeysObj instanceof String) {
            // 逗号分隔的字符串
            return Arrays.stream(((String) contextKeysObj).split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
        } else if (contextKeysObj instanceof String[]) {
            return (String[]) contextKeysObj;
        } else if (contextKeysObj instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            java.util.List<String> list = (java.util.List<String>) contextKeysObj;
            return list.toArray(new String[0]);
        }

        return new String[0];
    }

    /**
     * 从状态中提取上下文数据
     */
    private Map<String, Object> extractContextData(OverAllState state, String[] contextKeys) {
        Map<String, Object> contextData = new HashMap<>();

        for (String key : contextKeys) {
            state.value(key).ifPresent(value -> contextData.put(key, value));
        }

        return contextData;
    }

    /**
     * 格式化审核提示（替换 {key} 占位符）
     */
    private String formatPrompt(String template, OverAllState state) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = state.value(key)
                    .map(Object::toString)
                    .orElse("{" + key + "}"); // 如果找不到，保留原占位符

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 从状态中提取 graphTaskId
     */
    private Long extractGraphTaskId(OverAllState state) {
        return state.value("graph_task_id")
                .map(v -> {
                    if (v instanceof Long) {
                        return (Long) v;
                    } else if (v instanceof Number) {
                        return ((Number) v).longValue();
                    } else if (v instanceof String) {
                        try {
                            return Long.parseLong((String) v);
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    }
                    return null;
                })
                .orElse(null);
    }

    /**
     * 创建状态快照
     */
    private GraphStateSnapshot createSnapshot(OverAllState state, NodeConfig nodeConfig, Long graphTaskId) {
        // 获取状态中的所有值
        Map<String, Object> stateValues = new HashMap<>();
        if (state.data() != null) {
            stateValues.putAll(state.data());
        }

        return GraphStateSnapshot.builder()
                .graphTaskId(graphTaskId)
                .currentNodeId(nodeConfig.getId())
                .nextNodeId(null)  // 由 DynamicGraphBuilder 根据边配置确定
                .stateValues(stateValues)
                .workflowType("DYNAMIC")  // 标记为动态工作流
                .build();
    }

    @Override
    public String getConfigSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "prompt_template": {
                      "type": "string",
                      "description": "审核提示模板，支持 {key} 占位符引用状态中的值"
                    },
                    "context_keys": {
                      "type": "array",
                      "items": { "type": "string" },
                      "description": "需要展示给审核人的上下文字段列表"
                    },
                    "output_key": {
                      "type": "string",
                      "description": "审核结果存储到状态的 key"
                    },
                    "timeout_seconds": {
                      "type": "integer",
                      "description": "审核超时时间（秒），默认 3600",
                      "default": 3600
                    }
                  },
                  "required": ["prompt_template", "context_keys", "output_key"]
                }
                """;
    }
}
