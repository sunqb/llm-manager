package com.llmmanager.agent.advisor;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Advisor 管理器
 *
 * 职责：
 * - 统一管理所有 Advisor（包括 MemoryAdvisor、LoggingAdvisor 等）
 * - 支持动态注册和获取 Advisor
 * - 提供灵活的 Advisor 组合机制
 *
 * 使用方式：
 * <pre>{@code
 * // 1. 自动注册（通过 @PostConstruct）
 * @Component
 * public class CustomAdvisor implements Advisor {
 *     @Resource
 *     private AdvisorManager advisorManager;
 *
 *     @PostConstruct
 *     public void register() {
 *         advisorManager.registerAdvisor(this);
 *     }
 * }
 *
 * // 2. 手动注册
 * advisorManager.registerAdvisor(new CustomAdvisor());
 *
 * // 3. 获取所有 Advisor
 * List<Advisor> advisors = advisorManager.getAllAdvisors();
 * }</pre>
 */
@Component
public class AdvisorManager {

    private final List<Advisor> advisors = new ArrayList<>();

    /**
     * 注册 Advisor
     *
     * @param advisor 要注册的 Advisor
     */
    public void registerAdvisor(Advisor advisor) {
        if (advisor != null && !advisors.contains(advisor)) {
            advisors.add(advisor);
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
