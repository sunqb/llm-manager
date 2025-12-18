# LLM Manager 可观测性集成指南

## 概述

本文档描述 LLM Manager 系统的可观测性（Observability）实现方案，采用**无侵入方式**集成监控、指标收集和分布式追踪功能。

## 快速开始

### 1. 启动应用

确保 `application.yml` 中可观测性配置已启用：
```yaml
llm:
  observability:
    enabled: true
    metrics-enabled: true
    tracing-enabled: true
```

### 2. 验证基础监控

```bash
# 健康检查
curl http://localhost:8080/actuator/health
# 返回: {"status":"UP"}

# 查看所有可用指标
curl http://localhost:8080/actuator/metrics
```

### 3. 触发 LLM 指标

**重要**：`llm.chat.*` 指标只有在**执行 LLM 对话后**才会生成。

```bash
# 先执行一次对话（需要登录）
curl -X POST http://localhost:8080/api/chat/1/stream \
  -H "Cookie: satoken={your-token}" \
  -H "Content-Type: text/plain" \
  -d "你好"

# 然后查看指标
curl http://localhost:8080/actuator/metrics/llm.chat.duration
curl http://localhost:8080/actuator/metrics/llm.chat.total
curl http://localhost:8080/actuator/metrics/llm.tokens.prompt
curl http://localhost:8080/actuator/metrics/llm.tokens.completion
```

### 4. 触发 Agent/Graph 指标

```bash
# 执行 Agent 或工作流后
curl http://localhost:8080/actuator/metrics/agent.execution.duration
curl http://localhost:8080/actuator/metrics/graph.workflow.duration
```

### 5. 查看日志中的 TraceId

```
2025-12-18 15:30:00.123 [http-nio-8080-exec-1] [a1b2c3d4e5f6g7h8] [i9j0k1l2] INFO ...
                                               ↑ TraceId           ↑ SpanId
```

---

## 架构设计

```
┌─────────────────────────────────────────────────────────────────────┐
│                         层 3：分布式追踪                              │
│  Micrometer Tracing + Brave + Zipkin/Jaeger                        │
│  - TraceId/SpanId 自动传播                                           │
│  - MDC 日志关联                                                      │
└─────────────────────────────────────────────────────────────────────┘
│                         层 2：业务指标                                │
│  MetricsAdvisor (Spring AI Advisor) + ObservabilityAspect (AOP)    │
│  - llm.chat.duration / tokens.prompt / tokens.completion            │
│  - agent.execution.duration / graph.workflow.duration               │
└─────────────────────────────────────────────────────────────────────┘
│                         层 1：基础监控                                │
│  Spring Boot Actuator                                               │
│  - /actuator/health  - /actuator/metrics  - /actuator/info         │
└─────────────────────────────────────────────────────────────────────┘
```

## 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| spring-boot-starter-actuator | 3.2.5 (BOM) | 基础监控端点 |
| micrometer-tracing | 1.2.x (BOM) | 追踪抽象层 |
| micrometer-tracing-bridge-brave | 1.2.x (BOM) | Brave 追踪桥接 |
| zipkin-reporter-brave | 2.16.x (BOM) | Zipkin 报告器 |
| spring-boot-starter-aop | 3.2.5 (BOM) | AOP 切面支持 |

> **注意**：所有可观测性依赖版本由 Spring Boot BOM 统一管理，子模块直接引用即可，无需声明版本。

## 配置说明

### application.yml 配置

```yaml
spring:
  # 排除 Zipkin 自动配置（仅保留 TraceId/SpanId 日志记录，不发送到 Zipkin）
  # 如需启用 Zipkin，注释掉下面的 exclude 配置
  autoconfigure:
    exclude:
      - org.springframework.boot.actuate.autoconfigure.tracing.zipkin.ZipkinAutoConfiguration

# 可观测性开关
llm:
  observability:
    enabled: true                    # 总开关（false 则所有组件不加载）
    metrics-enabled: true            # 指标收集开关
    tracing-enabled: true            # 分布式追踪开关（TraceId/SpanId 日志记录）

# Spring Boot Actuator 配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
      base-path: /actuator
  endpoint:
    health:
      show-details: when_authorized
      probes:
        enabled: true
  # 分布式追踪配置
  tracing:
    enabled: ${llm.observability.tracing-enabled:true}
    sampling:
      probability: ${TRACING_SAMPLING_PROBABILITY:1.0}  # 采样率
  # Zipkin 配置（仅当启用 Zipkin 时生效）
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}
```

### 配置项说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `llm.observability.enabled` | true | 总开关，控制所有可观测性组件加载 |
| `llm.observability.metrics-enabled` | true | 指标收集开关 |
| `llm.observability.tracing-enabled` | true | 分布式追踪开关（TraceId/SpanId 生成） |

### Zipkin 开关说明

由于 Spring Boot 3.2 没有提供 `management.zipkin.tracing.enabled` 配置项（参考 [Issue #34620](https://github.com/spring-projects/spring-boot/issues/34620)），
需要通过**排除自动配置类**来控制是否发送到 Zipkin：

**禁用 Zipkin（仅日志记录）**：
```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.actuate.autoconfigure.tracing.zipkin.ZipkinAutoConfiguration
```

**启用 Zipkin**：注释掉或删除上述 `exclude` 配置。

### 环境差异化配置建议

| 环境 | enabled | metrics-enabled | tracing-enabled | Zipkin | sampling |
|------|---------|-----------------|-----------------|--------|----------|
| 开发 | true | true | true | 禁用（排除自动配置） | 1.0 |
| 测试 | true | true | true | 启用 | 1.0 |
| 生产 | true | true | true | 启用 | 0.1 |

## 核心组件

### 1. MetricsAdvisor（LLM 对话指标）

**路径**：`llm-agent/src/main/java/com/llmmanager/agent/advisor/MetricsAdvisor.java`

通过 Spring AI Advisor 机制，在 LLM 对话前后自动收集指标：

```java
@Component
@ConditionalOnProperty(name = "llm.observability.metrics-enabled", havingValue = "true")
public class MetricsAdvisor implements BaseAdvisor {

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        // 记录开始时间，提取模型和渠道信息
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        // 记录耗时、Token 使用量等指标
    }
}
```

**收集的指标**：

| 指标名 | 类型 | 标签 | 说明 |
|--------|------|------|------|
| `llm.chat.duration` | Timer | model, channel, status | LLM 对话耗时 |
| `llm.tokens.prompt` | Counter | model, channel | 输入 Token 数 |
| `llm.tokens.completion` | Counter | model, channel | 输出 Token 数 |
| `llm.chat.total` | Counter | model, channel, status | 对话总次数 |

### 2. ObservabilityAspect（Agent/Graph 执行指标）

**路径**：`llm-agent/src/main/java/com/llmmanager/agent/observability/ObservabilityAspect.java`

通过 AOP 拦截关键执行入口，自动收集指标和日志关联：

```java
@Aspect
@Component
@ConditionalOnProperty(name = "llm.observability.enabled", havingValue = "true")
public class ObservabilityAspect {

    // 拦截 Agent 执行
    @Around("execution(* com.llmmanager.service.orchestration.ReactAgentExecutionService.executeAgent(..))")
    public Object aroundExecuteAgent(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithMetrics(joinPoint, "agent.execution", "SINGLE");
    }

    // 拦截 Graph 工作流执行
    @Around("execution(* com.llmmanager.agent.graph.GraphWorkflowExecutor.execute(..))")
    public Object aroundGraphExecute(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithMetrics(joinPoint, "graph.workflow", "GRAPH");
    }
}
```

**拦截的执行入口**：

| 类 | 方法 | 指标前缀 | 类型标签 |
|----|------|---------|---------|
| ReactAgentExecutionService | executeAgent | agent.execution | SINGLE |
| ReactAgentExecutionService | executeWorkflow | agent.execution | SEQUENTIAL |
| ReactAgentExecutionService | executeTeam | agent.execution | SUPERVISOR |
| GraphWorkflowExecutor | execute | graph.workflow | GRAPH |
| GraphWorkflowExecutor | deepResearch | graph.workflow | DEEP_RESEARCH |
| DynamicWorkflowExecutionService | executeWorkflow | graph.workflow | DYNAMIC |
| DynamicReactAgentExecutionService | execute | agent.execution | DYNAMIC |

**收集的指标**：

| 指标名 | 类型 | 标签 | 说明 |
|--------|------|------|------|
| `agent.execution.duration` | Timer | type, method, status | Agent 执行耗时 |
| `agent.execution.total` | Counter | type, method, status | Agent 执行次数 |
| `agent.execution.error` | Counter | type, method, error_type | Agent 错误次数 |
| `graph.workflow.duration` | Timer | type, method, status | 工作流执行耗时 |
| `graph.workflow.total` | Counter | type, method, status | 工作流执行次数 |
| `graph.workflow.error` | Counter | type, method, error_type | 工作流错误次数 |

### 3. ObservabilityConfig（配置类）

**路径**：`llm-agent/src/main/java/com/llmmanager/agent/observability/ObservabilityConfig.java`

自动注册 MetricsAdvisor 到 AdvisorManager：

```java
@Configuration
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnProperty(name = "llm.observability.enabled", havingValue = "true")
public class ObservabilityConfig {

    @PostConstruct
    public void initialize() {
        if (observabilityProperties.isMetricsEnabled() && metricsAdvisor != null) {
            advisorManager.registerAdvisor(metricsAdvisor);
        }
    }
}
```

### 4. ObservabilityProperties（配置属性）

**路径**：`llm-agent/src/main/java/com/llmmanager/agent/observability/ObservabilityProperties.java`

```java
@Data
@Component
@ConfigurationProperties(prefix = "llm.observability")
public class ObservabilityProperties {
    private boolean enabled = true;
    private boolean metricsEnabled = true;
    private boolean tracingEnabled = true;
}
```

## 日志配置

### logback-spring.xml

**路径**：`llm-ops/src/main/resources/logback-spring.xml`

日志格式包含 TraceId 和 SpanId：

```xml
<property name="LOG_PATTERN"
          value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId:-}] [%X{spanId:-}] %-5level %logger{36} - %msg%n"/>
```

**日志输出示例**：
```
2025-12-18 10:30:45.123 [http-nio-8080-exec-1] [a1b2c3d4e5f6g7h8] [i9j0k1l2] INFO  c.l.a.o.ObservabilityAspect - [Observability] 开始执行 ReactAgentExecutionService.executeAgent, traceId=a1b2c3d4e5f6g7h8, type=SINGLE
```

**可观测性专用日志文件**：
- 路径：`logs/{APP_NAME}-observability.log`
- 记录 `com.llmmanager.agent.observability` 和 `com.llmmanager.agent.advisor.MetricsAdvisor` 的日志

## 文件清单

### 新增文件

| 路径 | 说明 |
|------|------|
| `llm-agent/.../advisor/MetricsAdvisor.java` | LLM 对话指标收集 Advisor |
| `llm-agent/.../observability/ObservabilityAspect.java` | AOP 切面 |
| `llm-agent/.../observability/ObservabilityConfig.java` | 配置类 |
| `llm-agent/.../observability/ObservabilityProperties.java` | 配置属性 |
| `llm-ops/src/main/resources/logback-spring.xml` | 日志配置 |

### 修改文件

| 路径 | 修改内容 |
|------|---------|
| `pom.xml` (根) | 添加说明注释 |
| `llm-agent/pom.xml` | 添加 Actuator、AOP 依赖 |
| `llm-ops/pom.xml` | 添加 Actuator、Tracing、Zipkin 依赖 |
| `llm-openapi/pom.xml` | 添加 Actuator、Tracing、Zipkin 依赖 |
| `llm-ops/.../application.yml` | 添加 management 和 llm.observability 配置 |

## 验证步骤

### 1. 基础监控验证

```bash
# 启动应用后访问
curl http://localhost:8080/actuator/health
# 预期返回：{"status":"UP"}

curl http://localhost:8080/actuator/info

curl http://localhost:8080/actuator/metrics
# 预期返回：可用指标列表
```

### 2. LLM 指标验证

```bash
# 执行一次对话后
curl http://localhost:8080/actuator/metrics/llm.chat.duration
curl http://localhost:8080/actuator/metrics/llm.chat.total
curl http://localhost:8080/actuator/metrics/llm.tokens.prompt
curl http://localhost:8080/actuator/metrics/llm.tokens.completion
```

### 3. Agent/Graph 指标验证

```bash
# 执行 Agent 或工作流后
curl http://localhost:8080/actuator/metrics/agent.execution.duration
curl http://localhost:8080/actuator/metrics/graph.workflow.duration
```

### 4. 分布式追踪验证

1. 启动 Zipkin 服务：
   ```bash
   docker run -d -p 9411:9411 openzipkin/zipkin
   ```

2. 执行请求后访问 Zipkin UI：
   ```
   http://localhost:9411
   ```

3. 查看日志中的 TraceId：
   ```
   [a1b2c3d4e5f6g7h8] [i9j0k1l2]
   ```

## 扩展指南

### 添加自定义指标

```java
@Component
public class CustomMetrics {

    @Resource
    private MeterRegistry meterRegistry;

    public void recordCustomMetric(String name, double value, String... tags) {
        Counter.builder(name)
            .tags(tags)
            .register(meterRegistry)
            .increment(value);
    }
}
```

### 添加新的 AOP 切点

在 `ObservabilityAspect` 中添加：

```java
@Pointcut("execution(* com.llmmanager.service.xxx.YourService.yourMethod(..))")
public void yourMethodPointcut() {}

@Around("yourMethodPointcut()")
public Object aroundYourMethod(ProceedingJoinPoint joinPoint) throws Throwable {
    return executeWithMetrics(joinPoint, "your.metric.prefix", "YOUR_TYPE");
}
```

## 注意事项

1. **配置开关**：所有可观测性功能通过 `llm.observability.*` 配置项控制，默认关闭（`matchIfMissing = false`）

2. **性能影响**：
   - MetricsAdvisor 在每次 LLM 调用时执行，开销极小
   - ObservabilityAspect 使用 AOP 代理，有轻微性能开销
   - 生产环境建议调低采样率（`sampling.probability`）

3. **依赖管理**：可观测性依赖由 Spring Boot BOM 管理，无需在 `dependencyManagement` 中声明版本

4. **TraceId 传播**：
   - Micrometer Tracing 自动处理 HTTP 请求的 TraceId 传播
   - ObservabilityAspect 为无 TraceId 的内部调用自动生成 16 位 TraceId

## 参考资料

- [Spring Boot Actuator 文档](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Tracing 文档](https://micrometer.io/docs/tracing)
- [Zipkin 官方文档](https://zipkin.io/)
