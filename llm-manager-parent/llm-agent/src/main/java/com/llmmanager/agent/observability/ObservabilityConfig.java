package com.llmmanager.agent.observability;

import com.llmmanager.agent.advisor.AdvisorManager;
import com.llmmanager.agent.advisor.MetricsAdvisor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * 可观测性配置类
 *
 * 职责：
 * - 自动注册 MetricsAdvisor 到 AdvisorManager
 * - 启用 ObservabilityProperties 配置属性
 *
 * 配置开关：llm.observability.enabled=true
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnProperty(name = "llm.observability.enabled", havingValue = "true", matchIfMissing = false)
public class ObservabilityConfig {

    @Resource
    private AdvisorManager advisorManager;

    @Resource
    private ObservabilityProperties observabilityProperties;

    @Autowired(required = false)
    private MetricsAdvisor metricsAdvisor;

    @PostConstruct
    public void initialize() {
        log.info("[ObservabilityConfig] 可观测性配置初始化...");
        log.info("[ObservabilityConfig] 配置状态: enabled={}, metricsEnabled={}, tracingEnabled={}",
                observabilityProperties.isEnabled(),
                observabilityProperties.isMetricsEnabled(),
                observabilityProperties.isTracingEnabled());

        // 自动注册 MetricsAdvisor
        if (observabilityProperties.isMetricsEnabled() && metricsAdvisor != null) {
            advisorManager.registerAdvisor(metricsAdvisor);
            log.info("[ObservabilityConfig] 已注册 MetricsAdvisor 到 AdvisorManager");
        }

        log.info("[ObservabilityConfig] 可观测性配置初始化完成");
    }
}
