package com.llmmanager.agent.reactagent.autonomous;

import com.llmmanager.agent.reactagent.core.AgentToolAdapter;
import com.llmmanager.agent.reactagent.core.AgentWrapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Supervisor Agent 团队
 * 
 * 方案B：Agent 自主协作框架。
 * 
 * 核心特点：
 * - Supervisor Agent 完全自主决定调用哪个 Worker Agent
 * - Worker Agent 作为 Tool 被 Supervisor 调用
 * - 流程不确定，由 Supervisor 推理决定
 * 
 * 工作原理：
 * 1. 将所有 Worker Agent 包装为 Tool
 * 2. Supervisor Agent 拥有这些 Tool
 * 3. 用户请求发送给 Supervisor
 * 4. Supervisor 自主决定调用哪个 Worker、调用几次、什么顺序
 * 5. Supervisor 综合 Worker 的结果给出最终答案
 * 
 * 使用示例：
 * <pre>{@code
 * SupervisorAgentTeam team = SupervisorAgentTeam.builder()
 *     .supervisorChatModel(chatModel)
 *     .supervisorInstruction("你是一个任务协调者...")
 *     .worker("researcher", researchAgent, "调用研究专家进行深度研究")
 *     .worker("analyzer", analyzeAgent, "调用分析专家进行数据分析")
 *     .worker("writer", writeAgent, "调用写作专家撰写报告")
 *     .build();
 * 
 * // Supervisor 自主决定调用谁
 * String result = team.execute("写一篇关于AI的报告");
 * }</pre>
 * 
 * @author LLM Manager
 */
@Slf4j
@Getter
public class SupervisorAgentTeam {

    private final AgentWrapper supervisor;
    private final Map<String, AgentWrapper> workers;
    private final Map<String, String> workerDescriptions;

    private SupervisorAgentTeam(AgentWrapper supervisor, 
                                Map<String, AgentWrapper> workers,
                                Map<String, String> workerDescriptions) {
        this.supervisor = supervisor;
        this.workers = workers;
        this.workerDescriptions = workerDescriptions;
    }

    /**
     * 执行任务
     * 
     * Supervisor 会自主决定如何完成任务，包括：
     * - 调用哪些 Worker
     * - 调用顺序
     * - 调用次数
     * 
     * @param input 用户输入
     * @return 最终结果
     */
    public String execute(String input) {
        log.info("[SupervisorAgentTeam] 开始执行, 输入: {}", 
                input.length() > 100 ? input.substring(0, 100) + "..." : input);
        log.info("[SupervisorAgentTeam] 可用 Workers: {}", workers.keySet());

        try {
            String result = supervisor.call(input);
            log.info("[SupervisorAgentTeam] 执行完成");
            return result;
        } catch (Exception e) {
            log.error("[SupervisorAgentTeam] 执行失败: {}", e.getMessage(), e);
            throw new RuntimeException("SupervisorAgentTeam 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取 Worker 数量
     */
    public int getWorkerCount() {
        return workers.size();
    }

    /**
     * 获取所有 Worker 名称
     */
    public List<String> getWorkerNames() {
        return new ArrayList<>(workers.keySet());
    }

    /**
     * 创建 Builder
     */
    public static SupervisorAgentTeamBuilder builder() {
        return new SupervisorAgentTeamBuilder();
    }

    /**
     * SupervisorAgentTeam 构建器
     */
    public static class SupervisorAgentTeamBuilder {
        private ChatModel supervisorChatModel;
        private String supervisorName = "supervisor";
        private String supervisorInstruction;
        private final Map<String, AgentWrapper> workers = new HashMap<>();
        private final Map<String, String> workerDescriptions = new HashMap<>();
        private final List<ToolCallback> additionalTools = new ArrayList<>();

        public SupervisorAgentTeamBuilder supervisorChatModel(ChatModel chatModel) {
            this.supervisorChatModel = chatModel;
            return this;
        }

        public SupervisorAgentTeamBuilder supervisorName(String name) {
            this.supervisorName = name;
            return this;
        }

        public SupervisorAgentTeamBuilder supervisorInstruction(String instruction) {
            this.supervisorInstruction = instruction;
            return this;
        }

        /**
         * 添加 Worker Agent
         *
         * Worker Agent 的 name 和 description 会自动从 AgentWrapper 中获取。
         * description 用于 Supervisor 决定是否调用此 Worker。
         *
         * @param agent Worker Agent（需要设置 name 和 description）
         */
        public SupervisorAgentTeamBuilder worker(AgentWrapper agent) {
            String name = agent.getName();
            String description = agent.getDescription();
            workers.put(name, agent);
            workerDescriptions.put(name, description != null ? description : "调用 " + name + " 处理任务");
            return this;
        }

        /**
         * 添加额外的工具（非 Agent 工具）
         */
        public SupervisorAgentTeamBuilder additionalTool(ToolCallback tool) {
            additionalTools.add(tool);
            return this;
        }

        public SupervisorAgentTeam build() {
            validateParams();

            // 将所有 Worker Agent 转换为 Tool
            List<ToolCallback> allTools = new ArrayList<>(additionalTools);
            for (Map.Entry<String, AgentWrapper> entry : workers.entrySet()) {
                String workerName = entry.getKey();
                AgentWrapper workerAgent = entry.getValue();
                String description = workerDescriptions.get(workerName);

                // 使用 AgentTool.create() 将 Agent 转换为 Tool
                // description 已经在 AgentWrapper 中设置，AgentTool.create() 会自动使用
                ToolCallback agentTool = AgentToolAdapter.asTool(workerAgent);

                allTools.add(agentTool);
                log.info("[SupervisorAgentTeam] 注册 Worker: {} -> {}", workerName, description);
            }

            // 构建默认的 Supervisor 指令
            String instruction = supervisorInstruction != null ? supervisorInstruction : buildDefaultInstruction();

            // 创建 Supervisor Agent
            AgentWrapper supervisor = AgentWrapper.builder()
                    .name(supervisorName)
                    .chatModel(supervisorChatModel)
                    .instruction(instruction)
                    .tools(allTools)
                    .build();

            log.info("[SupervisorAgentTeam] 构建完成, Supervisor: {}, Workers: {}", 
                    supervisorName, workers.keySet());

            return new SupervisorAgentTeam(supervisor, workers, workerDescriptions);
        }

        private String buildDefaultInstruction() {
            StringBuilder sb = new StringBuilder();
            sb.append("你是一个智能任务协调者（Supervisor）。\n\n");
            sb.append("你的职责是分析用户的请求，并决定如何完成任务。\n");
            sb.append("你可以调用以下专家来帮助你完成任务：\n\n");
            
            for (Map.Entry<String, String> entry : workerDescriptions.entrySet()) {
                sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            
            sb.append("\n工作方式：\n");
            sb.append("1. 分析用户请求，理解任务目标\n");
            sb.append("2. 决定需要调用哪些专家\n");
            sb.append("3. 按需调用专家，可以多次调用同一专家\n");
            sb.append("4. 综合专家的结果，给出最终答案\n\n");
            sb.append("请用中文回答用户的问题。");
            
            return sb.toString();
        }

        private void validateParams() {
            if (supervisorChatModel == null) {
                throw new IllegalArgumentException("必须设置 supervisorChatModel");
            }
            if (workers.isEmpty()) {
                throw new IllegalArgumentException("必须至少添加一个 Worker");
            }
        }
    }
}

