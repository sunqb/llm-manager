package com.llmmanager.agent.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 可观测性切面（无侵入方式）
 *
 * 通过 AOP 拦截关键执行入口，自动收集指标和日志关联：
 * - Agent 执行指标：agent.execution.duration, agent.execution.total
 * - Graph 工作流指标：graph.workflow.duration, graph.workflow.total
 * - TraceId 传播：为每个执行生成或继承 TraceId
 *
 * 配置开关：llm.observability.enabled=true
 *
 * 拦截的执行入口：
 * - ReactAgentExecutionService.executeAgent/executeWorkflow/executeTeam
 * - GraphWorkflowExecutor.execute/executeStream/deepResearch
 * - DynamicWorkflowExecutionService.executeWorkflow
 * - DynamicReactAgentExecutionService.execute
 */
@Slf4j
@Aspect
@Component
@ConditionalOnProperty(name = "llm.observability.enabled", havingValue = "true", matchIfMissing = false)
public class ObservabilityAspect {

    @Resource
    private MeterRegistry meterRegistry;

    // ==================== Pointcuts ====================

    /**
     * ReactAgentExecutionService.executeAgent
     */
    @Pointcut("execution(* com.llmmanager.service.orchestration.ReactAgentExecutionService.executeAgent(..))")
    public void executeAgentPointcut() {}

    /**
     * ReactAgentExecutionService.executeWorkflow
     */
    @Pointcut("execution(* com.llmmanager.service.orchestration.ReactAgentExecutionService.executeWorkflow(..))")
    public void executeWorkflowPointcut() {}

    /**
     * ReactAgentExecutionService.executeTeam
     */
    @Pointcut("execution(* com.llmmanager.service.orchestration.ReactAgentExecutionService.executeTeam(..))")
    public void executeTeamPointcut() {}

    /**
     * GraphWorkflowExecutor.execute (通用执行)
     */
    @Pointcut("execution(* com.llmmanager.agent.graph.GraphWorkflowExecutor.execute(..))")
    public void graphExecutePointcut() {}

    /**
     * GraphWorkflowExecutor.deepResearch
     */
    @Pointcut("execution(* com.llmmanager.agent.graph.GraphWorkflowExecutor.deepResearch(..))")
    public void deepResearchPointcut() {}

    /**
     * DynamicWorkflowExecutionService.executeWorkflow
     */
    @Pointcut("execution(* com.llmmanager.service.orchestration.DynamicWorkflowExecutionService.executeWorkflow(..))")
    public void dynamicWorkflowPointcut() {}

    /**
     * DynamicReactAgentExecutionService.execute
     */
    @Pointcut("execution(* com.llmmanager.service.orchestration.DynamicReactAgentExecutionService.execute(..))")
    public void dynamicAgentPointcut() {}

    // ==================== Agent 执行监控 ====================

    @Around("executeAgentPointcut()")
    public Object aroundExecuteAgent(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithMetrics(joinPoint, "agent.execution", "SINGLE");
    }

    @Around("executeWorkflowPointcut()")
    public Object aroundExecuteWorkflow(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithMetrics(joinPoint, "agent.execution", "SEQUENTIAL");
    }

    @Around("executeTeamPointcut()")
    public Object aroundExecuteTeam(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithMetrics(joinPoint, "agent.execution", "SUPERVISOR");
    }

    // ==================== Graph 工作流监控 ====================

    @Around("graphExecutePointcut()")
    public Object aroundGraphExecute(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithMetrics(joinPoint, "graph.workflow", "GRAPH");
    }

    @Around("deepResearchPointcut()")
    public Object aroundDeepResearch(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithMetrics(joinPoint, "graph.workflow", "DEEP_RESEARCH");
    }

    @Around("dynamicWorkflowPointcut()")
    public Object aroundDynamicWorkflow(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithMetrics(joinPoint, "graph.workflow", "DYNAMIC");
    }

    @Around("dynamicAgentPointcut()")
    public Object aroundDynamicAgent(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithMetrics(joinPoint, "agent.execution", "DYNAMIC");
    }

    // ==================== 公共方法 ====================

    /**
     * 执行方法并收集指标
     *
     * @param joinPoint    切点
     * @param metricPrefix 指标前缀（agent.execution 或 graph.workflow）
     * @param type         执行类型（SINGLE, SEQUENTIAL, SUPERVISOR, GRAPH, DEEP_RESEARCH, DYNAMIC）
     */
    private Object executeWithMetrics(ProceedingJoinPoint joinPoint, String metricPrefix, String type) throws Throwable {
        // 生成或获取 TraceId
        String traceId = MDC.get("traceId");
        boolean traceIdGenerated = false;
        if (traceId == null || traceId.isEmpty()) {
            traceId = generateTraceId();
            MDC.put("traceId", traceId);
            traceIdGenerated = true;
        }

        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        long startTime = System.nanoTime();
        String status = "success";

        log.info("[Observability] 开始执行 {}.{}, traceId={}, type={}",
                className, methodName, traceId, type);

        try {
            Object result = joinPoint.proceed();

            // 检查结果状态（如果返回 Map 且包含 success 字段）
            if (result instanceof Map) {
                Map<?, ?> resultMap = (Map<?, ?>) result;
                Object success = resultMap.get("success");
                if (Boolean.FALSE.equals(success)) {
                    status = "error";
                }
            }

            return result;
        } catch (Throwable e) {
            status = "error";
            log.error("[Observability] 执行失败 {}.{}, traceId={}, error={}",
                    className, methodName, traceId, e.getMessage());

            // 记录错误计数
            Counter.builder(metricPrefix + ".error")
                    .tag("type", type)
                    .tag("method", methodName)
                    .tag("error_type", e.getClass().getSimpleName())
                    .description(metricPrefix + " 错误次数")
                    .register(meterRegistry)
                    .increment();

            throw e;
        } finally {
            long duration = System.nanoTime() - startTime;
            long durationMs = TimeUnit.NANOSECONDS.toMillis(duration);

            // 记录耗时指标
            Timer.builder(metricPrefix + ".duration")
                    .tag("type", type)
                    .tag("method", methodName)
                    .tag("status", status)
                    .description(metricPrefix + " 执行耗时")
                    .register(meterRegistry)
                    .record(duration, TimeUnit.NANOSECONDS);

            // 记录调用次数
            Counter.builder(metricPrefix + ".total")
                    .tag("type", type)
                    .tag("method", methodName)
                    .tag("status", status)
                    .description(metricPrefix + " 执行总次数")
                    .register(meterRegistry)
                    .increment();

            log.info("[Observability] 执行完成 {}.{}, traceId={}, status={}, duration={}ms",
                    className, methodName, traceId, status, durationMs);

            // 如果是本方法生成的 traceId，则清理
            if (traceIdGenerated) {
                MDC.remove("traceId");
            }
        }
    }

    /**
     * 生成 16 位 TraceId
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
