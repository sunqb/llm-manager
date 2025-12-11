package com.llmmanager.agent.graph.dynamic.executor;

import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.llmmanager.agent.graph.dynamic.dto.NodeConfig;
import org.springframework.ai.chat.client.ChatClient;

/**
 * 节点执行器接口
 *
 * 符合 Spring AI Alibaba 官方推荐的设计理念：
 * - 通过接口抽象节点行为
 * - 使用配置驱动（NodeConfig）而非固定类
 * - 支持动态创建 NodeAction
 *
 * 所有节点执行器必须实现此接口，并注册为 Spring Bean。
 * Spring 会自动将所有实现类注入到 Map<String, NodeExecutor> 中。
 */
public interface NodeExecutor {

    /**
     * 获取节点类型代码
     *
     * @return 节点类型代码（如：LLM_NODE, TOOL_NODE）
     */
    String getNodeType();

    /**
     * 根据节点配置创建 Spring AI Alibaba 的 AsyncNodeAction
     *
     * 这是核心方法，实现了从配置到可执行节点的转换。
     *
     * @param nodeConfig 节点配置（包含节点 ID、类型、参数等）
     * @param chatClient ChatClient 实例（用于调用 LLM）
     * @return AsyncNodeAction 实例（Spring AI Alibaba 的节点动作）
     */
    AsyncNodeAction createAction(NodeConfig nodeConfig, ChatClient chatClient);

    /**
     * 获取节点类型的配置参数 JSON Schema（可选）
     *
     * 用于前端展示配置表单或验证配置参数。
     * 返回 null 表示不提供 Schema。
     *
     * @return JSON Schema 字符串（TEXT 格式）
     */
    default String getConfigSchema() {
        return null;
    }

    /**
     * 获取节点类型描述（可选）
     *
     * @return 节点类型的详细描述
     */
    default String getDescription() {
        return "节点执行器";
    }
}
