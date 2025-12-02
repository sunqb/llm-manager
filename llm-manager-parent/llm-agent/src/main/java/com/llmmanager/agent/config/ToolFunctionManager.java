package com.llmmanager.agent.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具管理器 - 管理 Spring AI @Tool 注解的工具类
 *
 * 职责：
 * - 自动扫描所有带 @Tool 注解方法的 Bean
 * - 提供工具的注册、查询、获取功能
 * - 支持根据工具名称动态获取工具对象列表
 *
 * 使用方式：
 * <pre>{@code
 * // 1. 定义工具类（使用 Spring AI @Tool 注解）
 * @Component
 * public class WeatherTools {
 *     @Tool(description = "获取天气信息")
 *     public WeatherResponse getWeather(String city) { ... }
 * }
 *
 * // 2. 获取所有工具名称（用于前端展示）
 * Map<String, String> allTools = toolManager.getAllTools();
 *
 * // 3. 根据前端传入的工具名称，获取工具对象列表
 * Object[] tools = toolManager.getToolObjects(Arrays.asList("getWeather", "calculate"));
 *
 * // 4. 在 ChatClient 中使用
 * chatClient.prompt()
 *     .tools(toolManager.getToolObjects(requestedTools))
 *     .call();
 * }</pre>
 *
 * @author LLM Manager
 */
@Slf4j
@Component
public class ToolFunctionManager {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 存储工具信息
     * Key: 工具方法名, Value: ToolInfo（包含 Bean 实例和描述）
     */
    private final Map<String, ToolInfo> registeredTools = new ConcurrentHashMap<>();

    /**
     * 工具信息，这是 Java 17+ 的 record 语法，用于定义一个不可变数据载体类型。
     */
    public record ToolInfo(
            String name,           // 工具名称（方法名）
            String description,    // 工具描述
            Object beanInstance,   // Bean 实例
            String beanName,       // Bean 名称
            Class<?> beanClass     // Bean 类
    ) {}

    /**
     * 启动时自动扫描所有带 @Tool 注解的方法
     */
    @PostConstruct
    public void discoverTools() {
        log.info("[ToolFunctionManager] 开始扫描 @Tool 注解的工具...");

        // 获取所有 Bean
        String[] beanNames = applicationContext.getBeanDefinitionNames();

        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);
                Class<?> beanClass = bean.getClass();

                // 扫描该 Bean 的所有方法
                for (Method method : beanClass.getDeclaredMethods()) {
                    Tool toolAnnotation = method.getAnnotation(Tool.class);
                    if (toolAnnotation != null) {
                        String toolName = method.getName();
                        String description = toolAnnotation.description();

                        ToolInfo toolInfo = new ToolInfo(
                                toolName,
                                description,
                                bean,
                                beanName,
                                beanClass
                        );

                        registeredTools.put(toolName, toolInfo);
                        log.info("[ToolFunctionManager] ✓ 发现工具: {} - {} (Bean: {})",
                                toolName, description, beanName);
                    }
                }
            } catch (Exception e) {
                // 忽略无法处理的 Bean
                log.debug("[ToolFunctionManager] 跳过 Bean: {} ({})", beanName, e.getMessage());
            }
        }

        log.info("[ToolFunctionManager] 扫描完成，共发现 {} 个工具", registeredTools.size());
        printAllTools();
    }

    /**
     * 获取所有工具信息（用于前端展示）
     *
     * @return 工具名称和描述的映射
     */
    public Map<String, String> getAllTools() {
        Map<String, String> tools = new HashMap<>();
        registeredTools.forEach((name, info) -> tools.put(name, info.description()));
        return tools;
    }

    /**
     * 获取所有工具名称
     *
     * @return 工具名称列表
     */
    public List<String> getAllToolNames() {
        return new ArrayList<>(registeredTools.keySet());
    }

    /**
     * 根据工具名称列表获取工具对象数组
     * 用于传递给 ChatClient.tools()
     *
     * @param toolNames 工具名称列表（null 或空表示使用所有工具）
     * @return 工具对象数组
     */
    public Object[] getToolObjects(List<String> toolNames) {
        List<String> validNames = getValidToolNames(toolNames);

        // 使用 Set 去重，避免同一个 Bean 实例被添加多次
        Set<Object> toolObjects = new LinkedHashSet<>();
        for (String toolName : validNames) {
            ToolInfo info = registeredTools.get(toolName);
            if (info != null) {
                toolObjects.add(info.beanInstance());
            }
        }

        return toolObjects.toArray();
    }

    /**
     * 获取有效的工具名称
     * - 如果传入 null 或空列表，返回所有已注册的工具
     * - 如果传入具体列表，过滤掉不存在的工具
     *
     * @param requestedTools 请求的工具名称列表
     * @return 有效的工具名称列表
     */
    public List<String> getValidToolNames(List<String> requestedTools) {
        // 如果未指定工具，返回所有已注册的工具
        if (CollectionUtils.isEmpty(requestedTools)) {
            return getAllToolNames();
        }

        // 过滤出有效的工具
        List<String> validTools = new ArrayList<>();
        for (String toolName : requestedTools) {
            if (registeredTools.containsKey(toolName)) {
                validTools.add(toolName);
            } else {
                log.warn("[ToolFunctionManager] 工具 '{}' 未注册，已忽略", toolName);
            }
        }

        return validTools;
    }

    /**
     * 检查工具是否存在
     *
     * @param toolName 工具名称
     * @return 是否存在
     */
    public boolean hasTool(String toolName) {
        return registeredTools.containsKey(toolName);
    }

    /**
     * 获取工具描述
     *
     * @param toolName 工具名称
     * @return 工具描述（不存在则返回 null）
     */
    public String getToolDescription(String toolName) {
        ToolInfo info = registeredTools.get(toolName);
        return info != null ? info.description() : null;
    }

    /**
     * 获取工具详细信息
     *
     * @param toolName 工具名称
     * @return 工具信息
     */
    public ToolInfo getToolInfo(String toolName) {
        return registeredTools.get(toolName);
    }

    /**
     * 获取已注册的工具数量
     *
     * @return 工具数量
     */
    public int getToolCount() {
        return registeredTools.size();
    }

    /**
     * 打印所有已注册的工具
     */
    public void printAllTools() {
        if (registeredTools.isEmpty()) {
            log.info("==================== 未发现任何工具 ====================");
            return;
        }

        log.info("==================== 已注册的工具 ====================");
        registeredTools.forEach((name, info) ->
                log.info("  🔧 {} : {} (Bean: {})", name, info.description(), info.beanName())
        );
        log.info("======================================================");
    }
}
