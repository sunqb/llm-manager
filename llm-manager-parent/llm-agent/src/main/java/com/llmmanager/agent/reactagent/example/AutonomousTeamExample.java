package com.llmmanager.agent.reactagent.example;

import com.llmmanager.agent.config.ToolFunctionManager;
import com.llmmanager.agent.reactagent.autonomous.SupervisorAgentTeam;
import com.llmmanager.agent.reactagent.core.AgentWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 方案B：Agent 自主协作示例
 * 
 * 展示如何使用 SupervisorAgentTeam 实现 Agent 自主协作：
 * - Supervisor Agent 完全自主决定调用哪个 Worker
 * - Worker Agent 作为 Tool 被调用
 * - 流程由 Supervisor 推理决定
 * 
 * @author LLM Manager
 */
@Slf4j
@Component
public class AutonomousTeamExample {

    @Resource
    private ToolFunctionManager toolFunctionManager;

    /**
     * 示例1：研究团队
     * 
     * Supervisor 自主决定如何完成研究任务
     */
    public String demonstrateResearchTeam(ChatModel chatModel, String topic) {
        log.info("========== Agent 自主协作示例：研究团队 ==========");

        // 创建 Worker Agents（注意：description 用于 Agent-as-Tool 时的工具描述）
        AgentWrapper researchAgent = AgentWrapper.builder()
                .name("researcher")
                .description("调用研究专家收集和整理关于指定主题的信息")
                .chatModel(chatModel)
                .instruction("""
                    你是一个研究专家。
                    当被调用时，你需要对指定主题进行深入研究，收集相关信息和数据。
                    请提供详细、准确的研究结果。
                    """)
                .build();

        AgentWrapper analyzeAgent = AgentWrapper.builder()
                .name("analyzer")
                .description("调用分析专家对信息进行深度分析")
                .chatModel(chatModel)
                .instruction("""
                    你是一个分析专家。
                    当被调用时，你需要对提供的信息进行深度分析，提取关键洞察。
                    请提供有价值的分析结论。
                    """)
                .build();

        AgentWrapper writeAgent = AgentWrapper.builder()
                .name("writer")
                .description("调用写作专家撰写报告或文档")
                .chatModel(chatModel)
                .instruction("""
                    你是一个写作专家。
                    当被调用时，你需要将信息整理成清晰、专业的文档。
                    请提供高质量的文字内容。
                    """)
                .build();

        // 创建 Supervisor 团队
        SupervisorAgentTeam team = SupervisorAgentTeam.builder()
                .supervisorChatModel(chatModel)
                .supervisorName("research-supervisor")
                .supervisorInstruction("""
                    你是一个研究项目协调者。

                    你的团队成员：
                    - researcher: 负责收集和整理信息
                    - analyzer: 负责分析数据和提取洞察
                    - writer: 负责撰写报告和文档

                    根据用户的请求，你需要自主决定：
                    1. 调用哪些团队成员
                    2. 调用的顺序
                    3. 是否需要多次调用某个成员

                    最终，综合所有成员的工作成果，给出完整的答案。
                    """)
                .worker(researchAgent)
                .worker(analyzeAgent)
                .worker(writeAgent)
                .build();

        log.info("研究团队创建完成，Workers: {}", team.getWorkerNames());
        log.info("开始执行任务: {}", topic);

        String result = team.execute(topic);
        
        log.info("任务执行完成");
        return result;
    }

    /**
     * 示例2：智能助手团队
     * 
     * Supervisor 自主决定如何回答用户问题
     */
    public String demonstrateAssistantTeam(ChatModel chatModel, String userQuestion) {
        log.info("========== Agent 自主协作示例：智能助手团队 ==========");

        // 创建带工具的 Worker Agents
        Object[] weatherTools = toolFunctionManager.getToolObjects(java.util.List.of("getWeather"));
        Object[] calcTools = toolFunctionManager.getToolObjects(java.util.List.of("calculate"));

        AgentWrapper weatherAgent = AgentWrapper.builder()
                .name("weather-expert")
                .description("查询天气信息，如某城市的天气、温度等")
                .chatModel(chatModel)
                .instruction("你是一个天气专家，可以查询各城市的天气信息。")
                .methodTools(weatherTools)
                .build();

        AgentWrapper calcAgent = AgentWrapper.builder()
                .name("calculator-expert")
                .description("执行数学计算，如加减乘除等")
                .chatModel(chatModel)
                .instruction("你是一个计算专家，可以执行各种数学计算。")
                .methodTools(calcTools)
                .build();

        AgentWrapper knowledgeAgent = AgentWrapper.builder()
                .name("knowledge-expert")
                .description("回答常识性问题和一般性咨询")
                .chatModel(chatModel)
                .instruction("你是一个知识专家，可以回答各种常识性问题。")
                .build();

        // 创建 Supervisor 团队
        SupervisorAgentTeam team = SupervisorAgentTeam.builder()
                .supervisorChatModel(chatModel)
                .supervisorName("assistant-supervisor")
                .worker(weatherAgent)
                .worker(calcAgent)
                .worker(knowledgeAgent)
                .build();

        log.info("智能助手团队创建完成，Workers: {}", team.getWorkerNames());
        log.info("用户问题: {}", userQuestion);

        String result = team.execute(userQuestion);
        
        log.info("问题回答完成");
        return result;
    }

    /**
     * 示例3：复杂任务处理
     * 
     * 展示 Supervisor 如何处理需要多个 Worker 协作的复杂任务
     */
    public String demonstrateComplexTask(ChatModel chatModel, String complexTask) {
        log.info("========== Agent 自主协作示例：复杂任务 ==========");

        // 创建多个专业 Worker
        AgentWrapper plannerAgent = AgentWrapper.builder()
                .name("planner")
                .description("制定任务执行计划和步骤")
                .chatModel(chatModel)
                .instruction("你是一个规划专家，负责制定任务执行计划和步骤。")
                .build();

        AgentWrapper executorAgent = AgentWrapper.builder()
                .name("executor")
                .description("按照计划执行具体任务")
                .chatModel(chatModel)
                .instruction("你是一个执行专家，负责按照计划执行具体任务。")
                .build();

        AgentWrapper reviewerAgent = AgentWrapper.builder()
                .name("reviewer")
                .description("检查和评估任务执行结果")
                .chatModel(chatModel)
                .instruction("你是一个审核专家，负责检查和评估任务执行结果。")
                .build();

        // 创建 Supervisor 团队
        SupervisorAgentTeam team = SupervisorAgentTeam.builder()
                .supervisorChatModel(chatModel)
                .supervisorName("task-supervisor")
                .supervisorInstruction("""
                    你是一个任务管理专家。

                    对于复杂任务，你可以：
                    1. 先调用 planner 制定计划
                    2. 调用 executor 执行任务
                    3. 调用 reviewer 审核结果
                    4. 如果审核不通过，可以重新调用 executor

                    你需要自主判断任务的复杂度，决定调用策略。
                    简单任务可能只需要一个 Worker，复杂任务可能需要多个 Worker 协作。
                    """)
                .worker(plannerAgent)
                .worker(executorAgent)
                .worker(reviewerAgent)
                .build();

        log.info("任务管理团队创建完成，Workers: {}", team.getWorkerNames());
        log.info("复杂任务: {}", complexTask);

        String result = team.execute(complexTask);
        
        log.info("复杂任务处理完成");
        return result;
    }
}

