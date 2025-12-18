package com.llmmanager.agent.reactagent.configurable.pattern;

import com.llmmanager.agent.reactagent.configurable.config.AgentWorkflowConfig;

/**
 * 模式执行器接口
 * 
 * 定义不同协作模式的执行逻辑。
 * 
 * @author LLM Manager
 */
public interface PatternExecutor {

    /**
     * 执行工作流
     * 
     * @param input 用户输入
     * @param config 工作流配置
     * @return 执行结果
     */
    WorkflowResult execute(String input, AgentWorkflowConfig config);

    /**
     * 获取支持的模式
     * 
     * @return 模式代码
     */
    String getPatternCode();
}

