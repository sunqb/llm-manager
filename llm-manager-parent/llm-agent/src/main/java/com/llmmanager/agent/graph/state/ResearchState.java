package com.llmmanager.agent.graph.state;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import java.util.HashMap;
import java.util.Map;

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
     * 创建 KeyStrategyFactory
     *
     * 新版本 Spring AI Alibaba 使用 KeyStrategyFactory 替代 OverAllStateFactory
     */
    public static KeyStrategyFactory createKeyStrategyFactory() {
        return () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            // 使用 ReplaceStrategy 的键（单值）
            strategies.put(KEY_QUESTION, new ReplaceStrategy());
            strategies.put(KEY_ANALYSIS, new ReplaceStrategy());
            strategies.put(KEY_FINAL_ANSWER, new ReplaceStrategy());
            strategies.put(KEY_ITERATION_COUNT, new ReplaceStrategy());
            strategies.put(KEY_QUALITY_SCORE, new ReplaceStrategy());
            strategies.put(KEY_CURRENT_NODE, new ReplaceStrategy());
            strategies.put(KEY_ERROR_MESSAGE, new ReplaceStrategy());
            // 使用 AppendStrategy 的键（累积）
            strategies.put(KEY_SUB_QUESTIONS, new AppendStrategy());
            strategies.put(KEY_SEARCH_RESULTS, new AppendStrategy());
            return strategies;
        };
    }

    private ResearchState() {
    }
}
