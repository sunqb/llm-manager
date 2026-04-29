package com.llmmanager.service.orchestration.executor;

import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.llmmanager.agent.graph.dynamic.dto.NodeConfig;
import com.llmmanager.agent.graph.dynamic.executor.NodeExecutor;
import com.llmmanager.agent.review.exception.HumanReviewRequiredException;
import com.llmmanager.service.orchestration.DynamicReactAgentExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * ReactAgent 节点执行器
 *
 * 功能：在 Graph 工作流中调用 ReactAgent 作为节点
 *
 * 设计理念：
 * - 支持将已配置的 ReactAgent 作为 Graph 节点使用
 * - 通过 agent_ref 引用数据库中的 Agent 配置
 * - 从状态中读取输入，执行后将结果写入状态
 * - 支持 Agent 内部触发人工审核（两层审核）
 *
 * 放置在 llm-service 层的原因：
 * - 需要调用 DynamicReactAgentExecutionService（业务编排服务）
 * - 避免 llm-agent 依赖 llm-service 导致的循环依赖
 * - 保持层级关系清晰：service 层直接调用 service 层
 *
 * 配置参数：
 * - agent_ref (必需): Agent 的唯一标识（slug）
 * - input_key (必需): 从状态中读取输入的 key
 * - output_key (必需): 输出结果存储到状态的 key
 *
 * 使用场景：
 * - Graph + ReactAgent 两层架构
 * - 在工作流中复用已有的 Agent
 * - 需要 Agent 自主推理能力的节点
 *
 * @author LLM Manager
 */
@Slf4j
@Component("ReactAgentNodeExecutor")
public class ReactAgentNodeExecutor implements NodeExecutor {

    @Resource
    private DynamicReactAgentExecutionService dynamicReactAgentExecutionService;

    @Override
    public String getNodeType() {
        return "REACT_AGENT_NODE";
    }

    @Override
    public String getDescription() {
        return "ReactAgent 节点 - 调用 ReactAgent 作为工作流节点";
    }

    @Override
    public AsyncNodeAction createAction(NodeConfig nodeConfig, ChatClient chatClient) {
        // 提取配置参数
        Map<String, Object> config = nodeConfig.getConfig();
        String agentRef = (String) config.get("agent_ref");
        String inputKey = (String) config.get("input_key");
        String outputKey = (String) config.get("output_key");

        // 参数验证
        if (agentRef == null || agentRef.trim().isEmpty()) {
            throw new IllegalArgumentException("ReactAgent 节点配置缺少 agent_ref 参数");
        }
        if (inputKey == null || inputKey.trim().isEmpty()) {
            throw new IllegalArgumentException("ReactAgent 节点配置缺少 input_key 参数");
        }
        if (outputKey == null || outputKey.trim().isEmpty()) {
            throw new IllegalArgumentException("ReactAgent 节点配置缺少 output_key 参数");
        }

        // 返回异步节点动作
        return (OverAllState state) -> CompletableFuture.supplyAsync(() -> {
            log.info("[ReactAgentNodeExecutor] 节点 '{}' 开始执行，调用 Agent: {}", nodeConfig.getId(), agentRef);

            try {
                // 1. 从状态中获取输入
                String input = state.value(inputKey)
                        .map(Object::toString)
                        .orElse("");

                if (input.isEmpty()) {
                    log.warn("[ReactAgentNodeExecutor] 输入为空，inputKey: {}", inputKey);
                }

                // 2. 获取或生成会话标识
                String conversationCode = state.value("conversation_code")
                        .map(Object::toString)
                        .orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));

                // 3. 直接调用 DynamicReactAgentExecutionService（无需依赖倒置接口）
                log.info("[ReactAgentNodeExecutor] 执行 Agent: {}, 输入长度: {}", agentRef, input.length());
                Map<String, Object> result = dynamicReactAgentExecutionService.execute(agentRef, input, conversationCode);

                // 4. 处理执行结果
                Map<String, Object> updates = new HashMap<>();
                updates.put("current_node", nodeConfig.getId());
                updates.put("conversation_code", conversationCode);

                if (Boolean.TRUE.equals(result.get("success"))) {
                    // 成功：提取结果
                    Object agentResult = result.get("result");
                    if (agentResult == null) {
                        agentResult = result.get("finalResult"); // SEQUENTIAL 类型返回 finalResult
                    }
                    updates.put(outputKey, agentResult);
                    log.info("[ReactAgentNodeExecutor] Agent 执行成功，输出长度: {}",
                            agentResult != null ? agentResult.toString().length() : 0);
                } else if (Boolean.TRUE.equals(result.get("pendingReview"))) {
                    // 需要人工审核：传播异常
                    String reviewCode = (String) result.get("reviewCode");
                    String reviewPrompt = (String) result.get("reviewPrompt");
                    String reviewType = (String) result.get("reviewType");

                    log.info("[ReactAgentNodeExecutor] Agent 需要人工审核，reviewCode: {}", reviewCode);

                    throw new HumanReviewRequiredException(reviewCode, reviewPrompt, reviewType);
                } else {
                    // 失败：记录错误
                    String error = (String) result.get("error");
                    updates.put("error_message", error);
                    updates.put(outputKey, null);
                    log.error("[ReactAgentNodeExecutor] Agent 执行失败: {}", error);
                }

                return updates;

            } catch (HumanReviewRequiredException e) {
                // 重新抛出审核异常（不要吞掉）
                throw e;
            } catch (Exception e) {
                log.error("[ReactAgentNodeExecutor] 节点 '{}' 执行失败", nodeConfig.getId(), e);

                Map<String, Object> updates = new HashMap<>();
                updates.put("error_message", "Agent 执行失败: " + e.getMessage());
                updates.put("current_node", nodeConfig.getId());
                updates.put(outputKey, null);
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
                    "agent_ref": {
                      "type": "string",
                      "description": "Agent 的唯一标识（slug），引用数据库中的 Agent 配置"
                    },
                    "input_key": {
                      "type": "string",
                      "description": "从状态中读取输入的 key"
                    },
                    "output_key": {
                      "type": "string",
                      "description": "输出结果存储到状态的 key"
                    }
                  },
                  "required": ["agent_ref", "input_key", "output_key"]
                }
                """;
    }
}
