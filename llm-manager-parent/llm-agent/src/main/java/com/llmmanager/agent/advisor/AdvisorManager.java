package com.llmmanager.agent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Advisor 管理器
 *
 * 职责：
 * - 统一管理所有全局 Advisor（如 MetricsAdvisor、LoggingAdvisor 等）
 * - 支持动态注册和获取 Advisor
 * - 提供 ChatClient.Builder 增强方法，自动注入全局 Advisor
 *
 * 使用方式：
 * <pre>{@code
 * // 1. 注册全局 Advisor（通过 @PostConstruct 或配置类）
 * advisorManager.registerAdvisor(metricsAdvisor);
 *
 * // 2. 增强 ChatClient.Builder（自动注入所有全局 Advisor）
 * ChatClient.Builder builder = advisorManager.enhance(ChatClient.builder(chatModel));
 *
 * // 3. 继续添加业务相关的 Advisor
 * builder.defaultAdvisors(memoryAdvisor, ragAdvisor);
 * ChatClient client = builder.build();
 * }</pre>
 */
@Slf4j
@Component
public class AdvisorManager {

    private final List<Advisor> advisors = new ArrayList<>();

    /**
     * 增强 ChatClient.Builder，自动注入所有已注册的全局 Advisor
     *
     * @param builder ChatClient.Builder
     * @return 增强后的 Builder
     */
    public ChatClient.Builder enhance(ChatClient.Builder builder) {
        if (!advisors.isEmpty()) {
            builder.defaultAdvisors(advisors.toArray(new Advisor[0]));
            log.debug("[AdvisorManager] 已注入 {} 个全局 Advisor", advisors.size());
        }
        return builder;
    }

    /**
     * 注册 Advisor
     *
     * @param advisor 要注册的 Advisor
     */
    public void registerAdvisor(Advisor advisor) {
        if (advisor != null && !advisors.contains(advisor)) {
            advisors.add(advisor);
            log.info("[AdvisorManager] 注册 Advisor: {}", advisor.getName());
        }
    }

    /**
     * 批量注册 Advisor
     *
     * @param advisors 要注册的 Advisor 列表
     */
    public void registerAdvisors(List<Advisor> advisors) {
        if (advisors != null) {
            advisors.forEach(this::registerAdvisor);
        }
    }

    /**
     * 获取所有已注册的 Advisor
     *
     * @return Advisor 列表
     */
    public List<Advisor> getAllAdvisors() {
        return new ArrayList<>(advisors);
    }

    /**
     * 移除指定 Advisor
     *
     * @param advisor 要移除的 Advisor
     */
    public void removeAdvisor(Advisor advisor) {
        advisors.remove(advisor);
    }

    /**
     * 清空所有 Advisor
     */
    public void clearAdvisors() {
        advisors.clear();
    }

    /**
     * 获取 Advisor 数量
     *
     * @return Advisor 数量
     */
    public int getAdvisorCount() {
        return advisors.size();
    }
}
