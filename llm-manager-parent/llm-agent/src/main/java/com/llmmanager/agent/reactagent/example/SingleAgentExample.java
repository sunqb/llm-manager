package com.llmmanager.agent.reactagent.example;

import com.llmmanager.agent.reactagent.core.AgentWrapper;
import com.llmmanager.agent.config.ToolFunctionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

/**
 * 单个 ReactAgent 示例
 * 
 * 展示如何创建和使用单个 ReactAgent，包括：
 * - ReAct 循环（Reasoning + Acting）
 * - 工具调用
 * - 自主推理
 * 
 * ReAct 模式说明：
 * 1. Reasoning（推理）：LLM 分析问题，决定下一步行动
 * 2. Acting（行动）：执行工具调用
 * 3. Observing（观察）：处理工具结果
 * 4. 循环：直到任务完成
 * 
 * @author LLM Manager
 */
@Slf4j
@Component
public class SingleAgentExample {

    @Resource
    private ToolFunctionManager toolFunctionManager;

    /**
     * 创建一个简单的研究助手 Agent
     * 
     * 这个 Agent 可以：
     * - 查询天气信息
     * - 执行数学计算
     * - 自主决定使用哪个工具
     * 
     * @param chatModel 聊天模型
     * @return AgentWrapper 实例
     */
    public AgentWrapper createResearchAgent(ChatModel chatModel) {
        // 获取所有可用的工具对象
        Object[] toolObjects = toolFunctionManager.getToolObjects(null);
        
        log.info("[SingleAgentExample] 创建研究助手 Agent, 可用工具: {}", 
                toolFunctionManager.getAllToolNames());
        
        return AgentWrapper.builder()
                .name("research-assistant")
                .chatModel(chatModel)
                .instruction("""
                    你是一个智能研究助手，可以帮助用户解答各种问题。
                    
                    你的能力：
                    1. 查询天气信息 - 使用 getWeather 工具
                    2. 执行数学计算 - 使用 calculate 工具
                    
                    工作方式：
                    - 分析用户问题，判断是否需要使用工具
                    - 如果需要，选择合适的工具并调用
                    - 根据工具返回的结果，给出最终答案
                    - 如果不需要工具，直接回答问题
                    
                    请用中文回答用户的问题。
                    """)
                .methodTools(toolObjects)
                .build();
    }

    /**
     * 创建一个专业的天气查询 Agent
     * 
     * @param chatModel 聊天模型
     * @return AgentWrapper 实例
     */
    public AgentWrapper createWeatherAgent(ChatModel chatModel) {
        Object[] weatherTools = toolFunctionManager.getToolObjects(List.of("getWeather"));
        
        return AgentWrapper.builder()
                .name("weather-specialist")
                .chatModel(chatModel)
                .instruction("""
                    你是一个专业的天气查询助手。
                    
                    你的职责：
                    - 帮助用户查询各个城市的天气信息
                    - 提供天气预报和建议
                    - 使用 getWeather 工具获取实时天气数据
                    
                    回答格式：
                    - 清晰展示温度、天气状况、湿度等信息
                    - 根据天气给出穿衣或出行建议
                    """)
                .methodTools(weatherTools)
                .build();
    }

    /**
     * 创建一个数学计算 Agent
     * 
     * @param chatModel 聊天模型
     * @return AgentWrapper 实例
     */
    public AgentWrapper createCalculatorAgent(ChatModel chatModel) {
        Object[] calcTools = toolFunctionManager.getToolObjects(List.of("calculate"));
        
        return AgentWrapper.builder()
                .name("calculator-specialist")
                .chatModel(chatModel)
                .instruction("""
                    你是一个数学计算专家。
                    
                    你的职责：
                    - 帮助用户进行各种数学计算
                    - 使用 calculate 工具执行加减乘除运算
                    - 解释计算过程和结果
                    
                    支持的运算：
                    - add/plus/+ : 加法
                    - subtract/minus/- : 减法
                    - multiply/times/* : 乘法
                    - divide// : 除法
                    """)
                .methodTools(calcTools)
                .build();
    }

    /**
     * 演示 Agent 的使用
     * 
     * @param chatModel 聊天模型
     */
    public void demonstrateAgent(ChatModel chatModel) {
        log.info("========== 单个 ReactAgent 示例演示 ==========");
        
        // 创建研究助手
        AgentWrapper agent = createResearchAgent(chatModel);
        
        // 示例1：天气查询（需要工具调用）
        log.info("--- 示例1：天气查询 ---");
        String weatherQuery = "北京今天天气怎么样？";
        log.info("用户: {}", weatherQuery);
        String weatherResult = agent.call(weatherQuery);
        log.info("Agent: {}", weatherResult);
        
        // 示例2：数学计算（需要工具调用）
        log.info("--- 示例2：数学计算 ---");
        String calcQuery = "请帮我计算 123 加 456 等于多少？";
        log.info("用户: {}", calcQuery);
        String calcResult = agent.call(calcQuery);
        log.info("Agent: {}", calcResult);
        
        // 示例3：普通问答（不需要工具）
        log.info("--- 示例3：普通问答 ---");
        String generalQuery = "什么是人工智能？";
        log.info("用户: {}", generalQuery);
        String generalResult = agent.call(generalQuery);
        log.info("Agent: {}", generalResult);
        
        log.info("========== 演示结束 ==========");
    }
}

