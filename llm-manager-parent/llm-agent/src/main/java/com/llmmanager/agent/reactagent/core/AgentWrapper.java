package com.llmmanager.agent.reactagent.core;

import com.alibaba.cloud.ai.graph.agent.Builder;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Optional;

/**
 * ReactAgent 封装类
 * 
 * 提供对 Spring AI Alibaba ReactAgent 的封装，简化使用方式。
 * 
 * 核心功能：
 * - 封装 ReactAgent 的构建和执行
 * - 提供同步和流式调用方式
 * - 支持工具调用和自主推理
 * 
 * 使用示例：
 * <pre>{@code
 * AgentWrapper agent = AgentWrapper.builder()
 *     .name("research-agent")
 *     .chatModel(chatModel)
 *     .instruction("你是一个研究助手...")
 *     .tools(weatherTool, calculatorTool)
 *     .build();
 * 
 * // 同步调用
 * String result = agent.call("今天北京天气怎么样？");
 * 
 * // 流式调用
 * agent.stream("帮我分析这个问题...").subscribe(System.out::println);
 * }</pre>
 * 
 * @author LLM Manager
 */
@Slf4j
@Getter
public class AgentWrapper {

    private final String name;
    private final String description;
    private final ReactAgent reactAgent;
    private final String instruction;

    private AgentWrapper(String name, String description, ReactAgent reactAgent, String instruction) {
        this.name = name;
        this.description = description;
        this.reactAgent = reactAgent;
        this.instruction = instruction;
    }

    /**
     * 同步调用 Agent
     * 
     * @param input 用户输入
     * @return Agent 响应内容
     */
    public String call(String input) {
        log.info("[AgentWrapper] Agent '{}' 开始处理: {}", name, input);
        try {
            AssistantMessage result = reactAgent.call(input);
            String content = result.getText();
            log.info("[AgentWrapper] Agent '{}' 处理完成", name);
            return content;
        } catch (Exception e) {
            log.error("[AgentWrapper] Agent '{}' 处理失败: {}", name, e.getMessage(), e);
            throw new RuntimeException("Agent 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 同步调用 Agent，返回完整状态
     *
     * @param input 用户输入
     * @return 完整的工作流状态（Optional）
     */
    public Optional<OverAllState> invoke(String input) {
        log.info("[AgentWrapper] Agent '{}' invoke: {}", name, input);
        try {
            return reactAgent.invoke(input);
        } catch (Exception e) {
            log.error("[AgentWrapper] Agent '{}' invoke 失败: {}", name, e.getMessage(), e);
            throw new RuntimeException("Agent invoke 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 同步调用 Agent，带配置
     * 
     * @param input 用户输入
     * @param config 运行配置
     * @return Agent 响应内容
     */
    public String call(String input, RunnableConfig config) {
        log.info("[AgentWrapper] Agent '{}' 开始处理(带配置): {}", name, input);
        try {
            AssistantMessage result = reactAgent.call(input, config);
            return result.getText();
        } catch (Exception e) {
            log.error("[AgentWrapper] Agent '{}' 处理失败: {}", name, e.getMessage(), e);
            throw new RuntimeException("Agent 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取内部的 ReactAgent 实例
     * 用于高级场景，如作为工具使用
     * 
     * @return ReactAgent 实例
     */
    public ReactAgent getReactAgent() {
        return reactAgent;
    }

    /**
     * 创建 Builder
     * 
     * @return AgentWrapperBuilder
     */
    public static AgentWrapperBuilder builder() {
        return new AgentWrapperBuilder();
    }

    /**
     * AgentWrapper 构建器
     */
    public static class AgentWrapperBuilder {
        private String name;
        private String description;
        private ChatModel chatModel;
        private String instruction;
        private String systemPrompt;
        private List<ToolCallback> tools;
        private Object[] methodTools;

        public AgentWrapperBuilder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * 设置 Agent 描述
         * 当 Agent 作为 Tool 被其他 Agent 调用时，这个描述会被用来决定是否调用此 Agent
         */
        public AgentWrapperBuilder description(String description) {
            this.description = description;
            return this;
        }

        public AgentWrapperBuilder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public AgentWrapperBuilder instruction(String instruction) {
            this.instruction = instruction;
            return this;
        }

        public AgentWrapperBuilder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public AgentWrapperBuilder tools(List<ToolCallback> tools) {
            this.tools = tools;
            return this;
        }

        public AgentWrapperBuilder methodTools(Object... methodTools) {
            this.methodTools = methodTools;
            return this;
        }

        public AgentWrapper build() {
            validateParams();

            // 使用 Builder 类型（新版本 API）
            Builder agentBuilder = ReactAgent.builder()
                    .name(name)
                    .model(chatModel);

            // 设置描述（用于 Agent-as-Tool）
            if (description != null && !description.isEmpty()) {
                agentBuilder.description(description);
            }

            // 设置指令
            if (instruction != null && !instruction.isEmpty()) {
                agentBuilder.instruction(instruction);
            }

            // 设置系统提示
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                agentBuilder.systemPrompt(systemPrompt);
            }

            // 设置工具回调
            if (tools != null && !tools.isEmpty()) {
                agentBuilder.tools(tools.toArray(new ToolCallback[0]));
            }

            // 设置方法工具
            if (methodTools != null && methodTools.length > 0) {
                agentBuilder.methodTools(methodTools);
            }

            ReactAgent reactAgent = agentBuilder.build();

            log.info("[AgentWrapper] 构建 Agent '{}' 完成, 工具数量: {}",
                    name, (tools != null ? tools.size() : 0));

            return new AgentWrapper(name, description, reactAgent, instruction);
        }

        private void validateParams() {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("Agent name 不能为空");
            }
            if (chatModel == null) {
                throw new IllegalArgumentException("ChatModel 不能为空");
            }
        }
    }
}

