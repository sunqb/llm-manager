package com.llmmanager.agent.reactagent.configurable;

/**
 * 工作流模式枚举
 * 
 * 定义了配置驱动的多 Agent 协作模式。
 * 
 * @author LLM Manager
 */
public enum WorkflowPattern {

    /**
     * 顺序执行模式
     * 
     * Agent 按照配置的顺序依次执行，前一个 Agent 的输出作为后一个的输入。
     * 
     * 适用场景：
     * - 流水线处理（如：研究 → 分析 → 写作）
     * - 审批流程
     * - 数据处理管道
     * 
     * 示例：
     * Agent1 → Agent2 → Agent3 → 最终结果
     */
    SEQUENTIAL("sequential", "顺序执行模式"),

    /**
     * 并行执行模式
     * 
     * 多个 Agent 同时执行，最后合并结果。
     * 
     * 适用场景：
     * - 多角度分析（如：技术分析 + 市场分析 + 风险分析）
     * - 并行数据采集
     * - 多专家意见收集
     * 
     * 示例：
     *     ┌→ Agent1 ─┐
     * 输入 ├→ Agent2 ─┼→ 合并 → 最终结果
     *     └→ Agent3 ─┘
     */
    PARALLEL("parallel", "并行执行模式"),

    /**
     * LLM 路由模式
     * 
     * 由 LLM 动态决定将任务路由到哪个 Agent。
     * 
     * 适用场景：
     * - 智能客服（根据问题类型路由到不同专家）
     * - 任务分类处理
     * - 动态工作流
     * 
     * 示例：
     *           ┌→ Agent1（技术问题）
     * 输入 → LLM ├→ Agent2（销售问题）
     *           └→ Agent3（其他问题）
     */
    ROUTING("routing", "LLM 路由模式"),

    /**
     * 循环执行模式
     * 
     * Agent 循环执行直到满足终止条件。
     * 
     * 适用场景：
     * - 迭代优化（如：代码审查 → 修改 → 再审查）
     * - 持续改进
     * - 条件满足检查
     */
    LOOP("loop", "循环执行模式");

    private final String code;
    private final String description;

    WorkflowPattern(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据代码获取模式
     * 
     * @param code 模式代码
     * @return WorkflowPattern
     */
    public static WorkflowPattern fromCode(String code) {
        for (WorkflowPattern pattern : values()) {
            if (pattern.code.equalsIgnoreCase(code)) {
                return pattern;
            }
        }
        throw new IllegalArgumentException("未知的工作流模式: " + code);
    }
}

