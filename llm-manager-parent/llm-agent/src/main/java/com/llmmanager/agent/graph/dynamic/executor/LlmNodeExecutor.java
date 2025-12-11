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
 * LLM 调用节点执行器（官方推荐方式）
 *
 * 功能：调用语言模型进行文本生成
 *
 * 设计理念：遵循 Spring AI Alibaba 官方推荐
 * - 直接从 state 读取输入（input_key）
 * - 使用 system_prompt + user 消息构建请求
 * - 结果写入 state（output_key）
 *
 * 配置参数：
 * - input_key (必需): 从状态中读取输入的 key
 * - output_key (必需): 输出结果存储到状态的 key
 * - system_prompt (可选): 系统提示词，指导 LLM 行为
 * - temperature (可选): 温度参数（0-1）
 * - max_tokens (可选): 最大生成 token 数
 */
@Slf4j
@Component("LlmNodeExecutor")
public class LlmNodeExecutor implements NodeExecutor {

    @Override
    public String getNodeType() {
        return "LLM_NODE";
    }

    @Override
    public String getDescription() {
        return "LLM 调用节点 - 调用语言模型进行文本生成";
    }

    @Override
    public AsyncNodeAction createAction(NodeConfig nodeConfig, ChatClient chatClient) {
        // 提取配置参数
        Map<String, Object> config = nodeConfig.getConfig();
        String inputKey = (String) config.get("input_key");
        String outputKey = (String) config.get("output_key");
        String systemPrompt = (String) config.get("system_prompt");

        // 可选参数
        Double temperature = config.get("temperature") != null
                ? ((Number) config.get("temperature")).doubleValue()
                : null;
        Integer maxTokens = config.get("max_tokens") != null
                ? ((Number) config.get("max_tokens")).intValue()
                : null;

        // 参数验证
        if (inputKey == null || inputKey.trim().isEmpty()) {
            throw new IllegalArgumentException("LLM 节点配置缺少 input_key 参数");
        }
        if (outputKey == null || outputKey.trim().isEmpty()) {
            throw new IllegalArgumentException("LLM 节点配置缺少 output_key 参数");
        }

        // 返回异步节点动作
        return (OverAllState state) -> CompletableFuture.supplyAsync(() -> {
            try {
                log.info("[LlmNodeExecutor] 节点 '{}' 开始执行", nodeConfig.getId());

                // 1. 从状态中读取输入（官方推荐方式）
                String userInput = state.value(inputKey)
                        .map(Object::toString)
                        .orElse("");

                if (userInput.isEmpty()) {
                    log.warn("[LlmNodeExecutor] 输入键 '{}' 的值为空", inputKey);
                }

                log.debug("[LlmNodeExecutor] 输入内容: {}", userInput);

                // 2. 构建 ChatClient 请求（官方推荐方式）
                ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt();

                // 设置系统提示词（如果有）
                if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                    requestSpec = requestSpec.system(systemPrompt);
                    log.debug("[LlmNodeExecutor] 系统提示词: {}", systemPrompt);
                }

                // 设置用户消息
                requestSpec = requestSpec.user(userInput);

                // 设置可选参数
                if (temperature != null || maxTokens != null) {
                    org.springframework.ai.openai.OpenAiChatOptions.Builder optionsBuilder =
                            org.springframework.ai.openai.OpenAiChatOptions.builder();

                    if (temperature != null) {
                        optionsBuilder.temperature(temperature);
                    }
                    if (maxTokens != null) {
                        optionsBuilder.maxTokens(maxTokens);
                    }

                    requestSpec = requestSpec.options(optionsBuilder.build());
                }

                // 3. 调用 LLM
                String result = requestSpec.call().content();
                log.info("[LlmNodeExecutor] 节点 '{}' 执行成功，结果长度: {}",
                        nodeConfig.getId(), result != null ? result.length() : 0);

                // 4. 更新状态
                Map<String, Object> updates = new HashMap<>();
                updates.put(outputKey, result);

                // 记录当前节点名称（用于进度追踪）
                updates.put("current_node", nodeConfig.getId());

                return updates;

            } catch (Exception e) {
                log.error("[LlmNodeExecutor] 节点 '{}' 执行失败", nodeConfig.getId(), e);

                // 返回错误信息
                Map<String, Object> updates = new HashMap<>();
                updates.put("error_message", "LLM 调用失败: " + e.getMessage());
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
                    "input_key": {
                      "type": "string",
                      "description": "从状态中读取输入的 key"
                    },
                    "output_key": {
                      "type": "string",
                      "description": "输出结果存储到状态的 key"
                    },
                    "system_prompt": {
                      "type": "string",
                      "description": "系统提示词，指导 LLM 的行为和输出格式"
                    },
                    "temperature": {
                      "type": "number",
                      "description": "温度参数（0-1），控制输出的随机性",
                      "minimum": 0,
                      "maximum": 1
                    },
                    "max_tokens": {
                      "type": "integer",
                      "description": "最大生成 token 数"
                    }
                  },
                  "required": ["input_key", "output_key"]
                }
                """;
    }
}
