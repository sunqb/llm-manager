package com.llmmanager.agent.observability;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 可观测性配置属性
 *
 * 配置示例：
 * <pre>{@code
 * llm:
 *   observability:
 *     enabled: true           # 总开关
 *     metrics-enabled: true   # 指标收集开关
 *     tracing-enabled: true   # 分布式追踪开关（TraceId/SpanId 日志记录）
 * }</pre>
 *
 * 注意：Zipkin 开关通过排除自动配置类实现，参见 application.yml 中的 spring.autoconfigure.exclude
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm.observability")
public class ObservabilityProperties {

    /**
     * 可观测性总开关
     * 设置为 false 则不加载任何可观测性组件
     */
    private boolean enabled = true;

    /**
     * 指标收集开关
     * 控制 MetricsAdvisor 和 ObservabilityAspect 是否生效
     */
    private boolean metricsEnabled = true;

    /**
     * 分布式追踪开关
     * 控制 Micrometer Tracing 是否启用（TraceId/SpanId 生成和日志记录）
     */
    private boolean tracingEnabled = true;
}
