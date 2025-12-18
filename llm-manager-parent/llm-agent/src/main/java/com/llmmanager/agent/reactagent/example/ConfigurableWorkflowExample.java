package com.llmmanager.agent.reactagent.example;

import com.llmmanager.agent.reactagent.configurable.ConfigurableAgentWorkflow;
import com.llmmanager.agent.reactagent.configurable.WorkflowPattern;
import com.llmmanager.agent.reactagent.configurable.config.AgentConfig;
import com.llmmanager.agent.reactagent.configurable.config.AgentWorkflowConfig;
import com.llmmanager.agent.reactagent.configurable.pattern.WorkflowResult;
import com.llmmanager.agent.reactagent.core.AgentWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 方案A：配置协作模式示例
 * 
 * 展示如何使用 ConfigurableAgentWorkflow 配置不同的协作模式：
 * - Sequential：顺序执行
 * - Parallel：并行执行
 * - Routing：LLM 动态路由
 * 
 * @author LLM Manager
 */
@Slf4j
@Component
public class ConfigurableWorkflowExample {

    @Resource
    private SingleAgentExample singleAgentExample;

    /**
     * 示例1：顺序执行模式
     * 
     * 场景：研究 → 分析 → 写作 的流水线
     */
    public WorkflowResult demonstrateSequentialWorkflow(ChatModel chatModel, String topic) {
        log.info("========== 顺序执行模式示例 ==========");

        // 创建三个专业 Agent
        AgentWrapper researchAgent = createAgent(chatModel, "researcher", 
                "你是一个研究专家，负责收集和整理关于指定主题的信息。请提供详细的研究结果。");
        
        AgentWrapper analyzeAgent = createAgent(chatModel, "analyzer",
                "你是一个分析专家，负责分析研究结果，提取关键洞察和结论。请基于输入的研究结果进行深度分析。");
        
        AgentWrapper writeAgent = createAgent(chatModel, "writer",
                "你是一个写作专家，负责将分析结果整理成清晰、专业的报告。请基于输入的分析结果撰写报告。");

        // 配置顺序执行工作流
        AgentWorkflowConfig config = AgentWorkflowConfig.builder()
                .pattern(WorkflowPattern.SEQUENTIAL)
                .workflowName("研究报告生成流水线")
                .chainOutput(true)  // 前一个输出作为后一个输入
                .verboseLogging(true)
                .agents(List.of(
                        AgentConfig.of("researcher", researchAgent, "研究专家"),
                        AgentConfig.of("analyzer", analyzeAgent, "分析专家"),
                        AgentConfig.of("writer", writeAgent, "写作专家")
                ))
                .build();

        ConfigurableAgentWorkflow workflow = new ConfigurableAgentWorkflow(config);
        
        log.info("执行顺序工作流，主题: {}", topic);
        WorkflowResult result = workflow.execute("请研究以下主题：" + topic);
        
        log.info("顺序执行完成，成功: {}, 耗时: {}ms", result.isSuccess(), result.getTotalExecutionTimeMs());
        return result;
    }

    /**
     * 示例2：并行执行模式
     * 
     * 场景：多角度分析（技术 + 市场 + 风险）
     */
    public WorkflowResult demonstrateParallelWorkflow(ChatModel chatModel, String topic) {
        log.info("========== 并行执行模式示例 ==========");

        // 创建三个分析 Agent
        AgentWrapper techAgent = createAgent(chatModel, "tech-analyst",
                "你是一个技术分析专家，负责从技术角度分析问题，评估技术可行性和技术风险。");
        
        AgentWrapper marketAgent = createAgent(chatModel, "market-analyst",
                "你是一个市场分析专家，负责从市场角度分析问题，评估市场机会和竞争态势。");
        
        AgentWrapper riskAgent = createAgent(chatModel, "risk-analyst",
                "你是一个风险分析专家，负责识别和评估各类风险，提供风险缓解建议。");

        // 配置并行执行工作流
        AgentWorkflowConfig config = AgentWorkflowConfig.builder()
                .pattern(WorkflowPattern.PARALLEL)
                .workflowName("多角度分析")
                .mergeChatModel(chatModel)
                .parallelMergePrompt("请综合以上三位专家的分析结果，给出全面的评估报告和建议。")
                .verboseLogging(true)
                .agents(List.of(
                        AgentConfig.of("tech-analyst", techAgent, "技术分析专家"),
                        AgentConfig.of("market-analyst", marketAgent, "市场分析专家"),
                        AgentConfig.of("risk-analyst", riskAgent, "风险分析专家")
                ))
                .build();

        ConfigurableAgentWorkflow workflow = new ConfigurableAgentWorkflow(config);
        
        log.info("执行并行工作流，主题: {}", topic);
        WorkflowResult result = workflow.execute("请分析以下项目：" + topic);
        
        log.info("并行执行完成，成功: {}, 耗时: {}ms", result.isSuccess(), result.getTotalExecutionTimeMs());
        return result;
    }

    /**
     * 示例3：LLM 路由模式
     * 
     * 场景：智能客服（根据问题类型路由到不同专家）
     */
    public WorkflowResult demonstrateRoutingWorkflow(ChatModel chatModel, String userQuestion) {
        log.info("========== LLM 路由模式示例 ==========");

        // 创建不同领域的专家 Agent
        AgentWrapper techSupport = createAgent(chatModel, "tech-support",
                "你是一个技术支持专家，负责解答技术问题、故障排查和技术指导。");
        
        AgentWrapper salesAgent = createAgent(chatModel, "sales",
                "你是一个销售顾问，负责解答产品咨询、价格问题和购买建议。");
        
        AgentWrapper generalAgent = createAgent(chatModel, "general",
                "你是一个通用客服，负责解答一般性问题和提供基础帮助。");

        // 配置路由工作流
        AgentWorkflowConfig config = AgentWorkflowConfig.builder()
                .pattern(WorkflowPattern.ROUTING)
                .workflowName("智能客服路由")
                .routingChatModel(chatModel)
                .verboseLogging(true)
                .agents(List.of(
                        AgentConfig.of("tech-support", techSupport, "处理技术问题、故障排查、技术指导"),
                        AgentConfig.of("sales", salesAgent, "处理产品咨询、价格问题、购买建议"),
                        AgentConfig.of("general", generalAgent, "处理一般性问题和基础帮助")
                ))
                .build();

        ConfigurableAgentWorkflow workflow = new ConfigurableAgentWorkflow(config);
        
        log.info("执行路由工作流，问题: {}", userQuestion);
        WorkflowResult result = workflow.execute(userQuestion);
        
        log.info("路由执行完成，成功: {}, 耗时: {}ms", result.isSuccess(), result.getTotalExecutionTimeMs());
        return result;
    }

    private AgentWrapper createAgent(ChatModel chatModel, String name, String instruction) {
        return AgentWrapper.builder()
                .name(name)
                .chatModel(chatModel)
                .instruction(instruction)
                .build();
    }
}

