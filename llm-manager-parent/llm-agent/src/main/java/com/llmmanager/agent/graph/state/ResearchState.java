package com.llmmanager.agent.graph.state;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.OverAllStateFactory;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

/**
 * DeepResearch 工作流状态管理
 * 
 * 状态键说明：
 * - question: 用户原始问题
 * - sub_questions: 分解后的子问题列表
 * - search_results: 搜索结果（追加模式）
 * - analysis: 分析结果
 * - final_answer: 最终答案
 * - iteration_count: 迭代次数
 * - quality_score: 质量评分
 */
public class ResearchState {

    public static final String KEY_QUESTION = "question";
    public static final String KEY_SUB_QUESTIONS = "sub_questions";
    public static final String KEY_SEARCH_RESULTS = "search_results";
    public static final String KEY_ANALYSIS = "analysis";
    public static final String KEY_FINAL_ANSWER = "final_answer";
    public static final String KEY_ITERATION_COUNT = "iteration_count";
    public static final String KEY_QUALITY_SCORE = "quality_score";
    public static final String KEY_CURRENT_NODE = "current_node";
    public static final String KEY_ERROR_MESSAGE = "error_message";

    /**
     * 创建状态工厂
     */
    public static OverAllStateFactory createFactory() {
        return () -> {
            OverAllState state = new OverAllState();
            // 使用 ReplaceStrategy 的键（单值）
            state.registerKeyAndStrategy(KEY_QUESTION, new ReplaceStrategy());
            state.registerKeyAndStrategy(KEY_ANALYSIS, new ReplaceStrategy());
            state.registerKeyAndStrategy(KEY_FINAL_ANSWER, new ReplaceStrategy());
            state.registerKeyAndStrategy(KEY_ITERATION_COUNT, new ReplaceStrategy());
            state.registerKeyAndStrategy(KEY_QUALITY_SCORE, new ReplaceStrategy());
            state.registerKeyAndStrategy(KEY_CURRENT_NODE, new ReplaceStrategy());
            state.registerKeyAndStrategy(KEY_ERROR_MESSAGE, new ReplaceStrategy());
            // 使用 AppendStrategy 的键（累积）
            state.registerKeyAndStrategy(KEY_SUB_QUESTIONS, new AppendStrategy());
            state.registerKeyAndStrategy(KEY_SEARCH_RESULTS, new AppendStrategy());
            return state;
        };
    }

    private ResearchState() {
    }
}
