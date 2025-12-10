package com.llmmanager.agent.graph.workflow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.llmmanager.agent.graph.node.*;
import com.llmmanager.agent.graph.state.ResearchState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.async.AsyncGenerator;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;

/**
 * DeepResearch 深度研究工作流
 * 
 * 基于 Spring AI Alibaba Graph Core 实现
 * 
 * 工作流程：
 * 1. 问题分解 -> 将复杂问题拆分为子问题
 * 2. 信息收集 -> 针对每个子问题收集信息
 * 3. 分析 -> 分析收集到的信息
 * 4. 综合 -> 生成研究报告
 * 5. 质量检查 -> 评估报告质量，决定是否迭代
 */
@Slf4j
public class DeepResearchWorkflow {

    private final CompiledGraph compiledGraph;
    private final int maxIterations;

    public DeepResearchWorkflow(ChatClient chatClient) {
        this(chatClient, 3);
    }

    public DeepResearchWorkflow(ChatClient chatClient, int maxIterations) {
        this.maxIterations = maxIterations;
        try {
            this.compiledGraph = buildGraph(chatClient).compile();
            log.info("[DeepResearch] 工作流初始化完成，最大迭代次数: {}", maxIterations);
        } catch (GraphStateException e) {
            throw new RuntimeException("Failed to compile DeepResearch workflow", e);
        }
    }

    private StateGraph buildGraph(ChatClient chatClient) throws GraphStateException {
        // 创建节点
        QueryDecompositionNode decompositionNode = new QueryDecompositionNode(chatClient);
        InformationGatheringNode gatheringNode = new InformationGatheringNode(chatClient);
        AnalysisNode analysisNode = new AnalysisNode(chatClient);
        SynthesisNode synthesisNode = new SynthesisNode(chatClient);
        QualityCheckNode qualityCheckNode = new QualityCheckNode(chatClient, maxIterations);

        // 质量路由器：根据评分决定是结束还是继续迭代
        AsyncEdgeAction qualityRouter = (state) -> {
            int score = state.<Integer>value(ResearchState.KEY_QUALITY_SCORE).orElse(0);
            int iterations = state.<Integer>value(ResearchState.KEY_ITERATION_COUNT).orElse(0);

            if (score >= 80 || iterations >= maxIterations) {
                log.info("[DeepResearch] 质量达标或达到最大迭代，结束工作流");
                return CompletableFuture.completedFuture("end");
            } else {
                log.info("[DeepResearch] 质量不足，进行第 {} 轮迭代", iterations + 1);
                return CompletableFuture.completedFuture("iterate");
            }
        };

        // 构建工作流图
        return new StateGraph("DeepResearch Workflow", ResearchState.createFactory())
                // 添加节点
                .addNode("query_decomposition", decompositionNode)
                .addNode("information_gathering", gatheringNode)
                .addNode("analysis", analysisNode)
                .addNode("synthesis", synthesisNode)
                .addNode("quality_check", qualityCheckNode)

                // 定义边（工作流顺序）
                .addEdge(START, "query_decomposition")
                .addEdge("query_decomposition", "information_gathering")
                .addEdge("information_gathering", "analysis")
                .addEdge("analysis", "synthesis")
                .addEdge("synthesis", "quality_check")

                // 条件路由：质量检查后决定是结束还是迭代
                .addConditionalEdges("quality_check",
                        qualityRouter,
                        Map.of("end", END, "iterate", "information_gathering"));
    }

    /**
     * 同步执行研究
     */
    public ResearchResult research(String question) {
        log.info("[DeepResearch] 开始研究: {}", question);

        Map<String, Object> initialState = Map.of(
                ResearchState.KEY_QUESTION, question,
                ResearchState.KEY_ITERATION_COUNT, 0
        );

        RunnableConfig config = RunnableConfig.builder()
                .threadId(UUID.randomUUID().toString())
                .build();

        try {
            Optional<OverAllState> result = compiledGraph.invoke(initialState, config);

            if (result.isPresent()) {
                OverAllState state = result.get();
                return ResearchResult.builder()
                        .question(question)
                        .answer(state.<String>value(ResearchState.KEY_FINAL_ANSWER).orElse(""))
                        .analysis(state.<String>value(ResearchState.KEY_ANALYSIS).orElse(""))
                        .qualityScore(state.<Integer>value(ResearchState.KEY_QUALITY_SCORE).orElse(0))
                        .iterationCount(state.<Integer>value(ResearchState.KEY_ITERATION_COUNT).orElse(0))
                        .success(true)
                        .build();
            }

            return ResearchResult.builder()
                    .question(question)
                    .success(false)
                    .errorMessage("工作流执行失败")
                    .build();
        } catch (Exception e) {
            log.error("[DeepResearch] 研究失败", e);
            return ResearchResult.builder()
                    .question(question)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 流式执行研究（返回进度更新）
     * 将 AsyncGenerator 转换为 Flux
     */
    public Flux<ResearchProgress> researchStream(String question) {
        log.info("[DeepResearch] 开始流式研究: {}", question);

        Map<String, Object> initialState = Map.of(
                ResearchState.KEY_QUESTION, question,
                ResearchState.KEY_ITERATION_COUNT, 0
        );

        RunnableConfig config = RunnableConfig.builder()
                .threadId(UUID.randomUUID().toString())
                .build();

        AsyncGenerator<NodeOutput> generator = compiledGraph.stream(initialState, config);

        // 将 AsyncGenerator 转换为 Flux
        return Flux.create(sink -> {
            try {
                generator.forEachAsync(nodeOutput -> {
                    OverAllState state = nodeOutput.state();
                    String currentNode = state.<String>value(ResearchState.KEY_CURRENT_NODE).orElse(nodeOutput.node());

                    ResearchProgress progress = ResearchProgress.builder()
                            .nodeName(currentNode)
                            .question(state.<String>value(ResearchState.KEY_QUESTION).orElse(""))
                            .currentAnswer(state.<String>value(ResearchState.KEY_FINAL_ANSWER).orElse(""))
                            .analysis(state.<String>value(ResearchState.KEY_ANALYSIS).orElse(""))
                            .qualityScore(state.<Integer>value(ResearchState.KEY_QUALITY_SCORE).orElse(0))
                            .iterationCount(state.<Integer>value(ResearchState.KEY_ITERATION_COUNT).orElse(0))
                            .build();

                    sink.next(progress);
                }).thenAccept(v -> sink.complete());
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    /**
     * 同步获取所有进度（用于简单场景）
     */
    public List<ResearchProgress> researchWithProgress(String question) {
        List<ResearchProgress> progressList = new ArrayList<>();

        Map<String, Object> initialState = Map.of(
                ResearchState.KEY_QUESTION, question,
                ResearchState.KEY_ITERATION_COUNT, 0
        );

        RunnableConfig config = RunnableConfig.builder()
                .threadId(UUID.randomUUID().toString())
                .build();

        AsyncGenerator<NodeOutput> generator = compiledGraph.stream(initialState, config);

        generator.forEachAsync(nodeOutput -> {
            OverAllState state = nodeOutput.state();
            String currentNode = state.<String>value(ResearchState.KEY_CURRENT_NODE).orElse(nodeOutput.node());

            ResearchProgress progress = ResearchProgress.builder()
                    .nodeName(currentNode)
                    .question(state.<String>value(ResearchState.KEY_QUESTION).orElse(""))
                    .currentAnswer(state.<String>value(ResearchState.KEY_FINAL_ANSWER).orElse(""))
                    .analysis(state.<String>value(ResearchState.KEY_ANALYSIS).orElse(""))
                    .qualityScore(state.<Integer>value(ResearchState.KEY_QUALITY_SCORE).orElse(0))
                    .iterationCount(state.<Integer>value(ResearchState.KEY_ITERATION_COUNT).orElse(0))
                    .build();

            progressList.add(progress);
        }).join();

        return progressList;
    }

    /**
     * 研究结果
     */
    @lombok.Data
    @lombok.Builder
    public static class ResearchResult {
        private String question;
        private String answer;
        private String analysis;
        private int qualityScore;
        private int iterationCount;
        private boolean success;
        private String errorMessage;
    }

    /**
     * 研究进度
     */
    @lombok.Data
    @lombok.Builder
    public static class ResearchProgress {
        private String nodeName;
        private String question;
        private String currentAnswer;
        private String analysis;
        private int qualityScore;
        private int iterationCount;
    }
}
