package com.llmmanager.agent.graph.dynamic.executor;

import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmmanager.agent.graph.dynamic.dto.NodeConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 数据转换节点执行器（增强版）
 *
 * 功能：转换或处理状态数据
 *
 * 配置参数：
 * - transform_type (必需): 转换类型
 * - input_keys (必需): 输入字段列表
 * - output_key (必需): 输出结果存储到状态的 key
 * - delimiter (可选): 分隔符，用于 SPLIT 操作
 * - threshold (可选): 阈值，用于 THRESHOLD_CHECK 操作
 *
 * 支持的转换类型：
 * - MERGE: 合并多个字段的值（用换行分隔）
 * - EXTRACT: 提取单个字段的值
 * - FORMAT: 格式化多个字段（key: value 格式）
 * - SPLIT_LINES: 按行分割文本为列表
 * - PARSE_NUMBER: 从文本中解析数字
 * - PARSE_JSON: 解析 JSON 字符串
 * - THRESHOLD_CHECK: 阈值检查（用于条件路由）
 * - INCREMENT: 递增数值
 */
@Slf4j
@Component("TransformNodeExecutor")
public class TransformNodeExecutor implements NodeExecutor {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getNodeType() {
        return "TRANSFORM_NODE";
    }

    @Override
    public String getDescription() {
        return "数据转换节点 - 转换或处理状态数据";
    }

    @Override
    @SuppressWarnings("unchecked")
    public AsyncNodeAction createAction(NodeConfig nodeConfig, ChatClient chatClient) {
        // 提取配置参数
        Map<String, Object> config = nodeConfig.getConfig();
        String transformType = (String) config.get("transform_type");
        List<String> inputKeys = (List<String>) config.get("input_keys");
        String outputKey = (String) config.get("output_key");

        // 可选参数
        String delimiter = (String) config.getOrDefault("delimiter", "\n");
        Number threshold = (Number) config.get("threshold");

        // 参数验证
        if (transformType == null || transformType.trim().isEmpty()) {
            throw new IllegalArgumentException("转换节点配置缺少 transform_type 参数");
        }
        if (inputKeys == null || inputKeys.isEmpty()) {
            throw new IllegalArgumentException("转换节点配置缺少 input_keys 参数");
        }
        if (outputKey == null || outputKey.trim().isEmpty()) {
            throw new IllegalArgumentException("转换节点配置缺少 output_key 参数");
        }

        // 返回异步节点动作
        return (OverAllState state) -> CompletableFuture.supplyAsync(() -> {
            try {
                log.info("[TransformNodeExecutor] 节点 '{}' 开始执行，转换类型: {}",
                        nodeConfig.getId(), transformType);

                Object result;
                switch (transformType.toUpperCase()) {
                    case "MERGE":
                        result = mergeValues(state, inputKeys);
                        break;
                    case "EXTRACT":
                        result = extractValue(state, inputKeys.get(0));
                        break;
                    case "FORMAT":
                        result = formatValues(state, inputKeys);
                        break;
                    case "SPLIT_LINES":
                        result = splitLines(state, inputKeys.get(0));
                        break;
                    case "PARSE_NUMBER":
                        result = parseNumber(state, inputKeys.get(0));
                        break;
                    case "PARSE_JSON":
                        result = parseJson(state, inputKeys.get(0));
                        break;
                    case "THRESHOLD_CHECK":
                        result = thresholdCheck(state, inputKeys.get(0), threshold);
                        break;
                    case "INCREMENT":
                        result = incrementValue(state, inputKeys.get(0));
                        break;
                    default:
                        throw new IllegalArgumentException("不支持的转换类型: " + transformType);
                }

                Map<String, Object> updates = new HashMap<>();
                updates.put(outputKey, result);
                updates.put("current_node", nodeConfig.getId());

                log.info("[TransformNodeExecutor] 节点 '{}' 执行成功，结果类型: {}",
                        nodeConfig.getId(), result != null ? result.getClass().getSimpleName() : "null");
                return updates;

            } catch (Exception e) {
                log.error("[TransformNodeExecutor] 节点 '{}' 执行失败", nodeConfig.getId(), e);

                Map<String, Object> updates = new HashMap<>();
                updates.put("error_message", "数据转换失败: " + e.getMessage());
                updates.put("current_node", nodeConfig.getId());
                return updates;
            }
        });
    }

    /**
     * MERGE：合并多个字段的值
     */
    private String mergeValues(OverAllState state, List<String> inputKeys) {
        return inputKeys.stream()
                .map(key -> state.value(key).orElse("").toString())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    /**
     * EXTRACT：提取单个字段的值
     */
    private Object extractValue(OverAllState state, String key) {
        return state.value(key).orElse(null);
    }

    /**
     * FORMAT：格式化多个字段
     */
    private String formatValues(OverAllState state, List<String> inputKeys) {
        StringBuilder sb = new StringBuilder();
        for (String key : inputKeys) {
            Object value = state.value(key).orElse("");
            sb.append(key).append(": ").append(value).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * SPLIT_LINES：按行分割文本为列表
     */
    private List<String> splitLines(OverAllState state, String key) {
        String value = state.value(key)
                .map(Object::toString)
                .orElse("");

        return Arrays.stream(value.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * PARSE_NUMBER：从文本中解析数字
     */
    private Integer parseNumber(OverAllState state, String key) {
        String value = state.value(key)
                .map(Object::toString)
                .orElse("0");

        // 提取所有数字字符
        String numStr = value.replaceAll("[^0-9]", "");
        if (numStr.isEmpty()) {
            return 0;
        }

        try {
            int num = Integer.parseInt(numStr);
            return Math.min(100, Math.max(0, num)); // 限制在 0-100 范围
        } catch (NumberFormatException e) {
            log.warn("[TransformNodeExecutor] 无法解析数字: {}", value);
            return 0;
        }
    }

    /**
     * PARSE_JSON：解析 JSON 字符串
     */
    private Object parseJson(OverAllState state, String key) {
        String value = state.value(key)
                .map(Object::toString)
                .orElse("{}");

        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            try {
                return objectMapper.readValue(value, new TypeReference<List<Object>>() {});
            } catch (Exception e2) {
                log.warn("[TransformNodeExecutor] JSON 解析失败，返回原始字符串: {}", value);
                return value;
            }
        }
    }

    /**
     * THRESHOLD_CHECK：阈值检查，返回路由决策
     * 用于质量检查等场景
     */
    private String thresholdCheck(OverAllState state, String key, Number threshold) {
        int value = state.value(key)
                .map(v -> {
                    if (v instanceof Number) {
                        return ((Number) v).intValue();
                    }
                    try {
                        return Integer.parseInt(v.toString().replaceAll("[^0-9]", ""));
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .orElse(0);

        int thresholdValue = threshold != null ? threshold.intValue() : 80;

        log.info("[TransformNodeExecutor] 阈值检查: value={}, threshold={}", value, thresholdValue);

        // 返回路由决策
        return value >= thresholdValue ? "PASS" : "NEED_IMPROVEMENT";
    }

    /**
     * INCREMENT：递增数值
     */
    private Integer incrementValue(OverAllState state, String key) {
        int currentValue = state.value(key)
                .map(v -> {
                    if (v instanceof Number) {
                        return ((Number) v).intValue();
                    }
                    try {
                        return Integer.parseInt(v.toString());
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .orElse(0);

        return currentValue + 1;
    }

    @Override
    public String getConfigSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "transform_type": {
                      "type": "string",
                      "enum": ["MERGE", "EXTRACT", "FORMAT", "SPLIT_LINES", "PARSE_NUMBER", "PARSE_JSON", "THRESHOLD_CHECK", "INCREMENT"],
                      "description": "转换类型"
                    },
                    "input_keys": {
                      "type": "array",
                      "items": {"type": "string"},
                      "description": "输入字段列表"
                    },
                    "output_key": {
                      "type": "string",
                      "description": "输出结果存储到状态的 key"
                    },
                    "delimiter": {
                      "type": "string",
                      "description": "分隔符（用于 SPLIT 操作），默认为换行符"
                    },
                    "threshold": {
                      "type": "number",
                      "description": "阈值（用于 THRESHOLD_CHECK 操作），默认为 80"
                    }
                  },
                  "required": ["transform_type", "input_keys", "output_key"]
                }
                """;
    }
}
