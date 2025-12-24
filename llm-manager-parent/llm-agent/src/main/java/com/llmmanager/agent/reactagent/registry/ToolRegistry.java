package com.llmmanager.agent.reactagent.registry;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 工具注册表
 * 管理所有可用的工具，根据工具名称获取工具实例
 *
 * @author LLM Manager
 */
@Slf4j
@Component
public class ToolRegistry {

    /**
     * 工具映射表：工具名称 -> 工具实例
     */
    private final Map<String, Object> toolMap = new HashMap<>();

    /**
     * 已注册的工具名称列表
     */
    private final List<String> registeredToolNames = new ArrayList<>();

    // 注入所有工具 Bean
    @Resource(name = "weatherTools")
    private Object weatherTools;

    @Resource(name = "calculatorTools")
    private Object calculatorTools;

    @Resource(name = "stockTools")
    private Object stockTools;

    @Resource(name = "translationTools")
    private Object translationTools;

    @Resource(name = "newsTools")
    private Object newsTools;

    @Resource(name = "dateTimeTools")
    private Object dateTimeTools;

    @Resource(name = "knowledgeTools")
    private Object knowledgeTools;

    @Resource(name = "httpTools")
    private Object httpTools;

    @Resource(name = "webSearchTools")
    private Object webSearchTools;

    @PostConstruct
    public void init() {
        // 注册所有工具
        registerTool("weather", weatherTools);
        registerTool("calculator", calculatorTools);
        registerTool("stock", stockTools);
        registerTool("translation", translationTools);
        registerTool("news", newsTools);
        registerTool("datetime", dateTimeTools);
        registerTool("knowledge", knowledgeTools);
        registerTool("http", httpTools);
        registerTool("websearch", webSearchTools);

        log.info("[ToolRegistry] 工具注册完成，共 {} 个工具: {}", registeredToolNames.size(), registeredToolNames);
    }

    /**
     * 注册工具
     */
    public void registerTool(String name, Object tool) {
        if (tool != null) {
            toolMap.put(name.toLowerCase(), tool);
            registeredToolNames.add(name);
        }
    }

    /**
     * 根据工具名称获取工具实例
     */
    public Object getTool(String name) {
        return toolMap.get(name.toLowerCase());
    }

    /**
     * 根据工具名称列表获取 ToolCallback 列表
     *
     * @param toolNames 工具名称列表
     * @return ToolCallback 列表
     */
    public List<ToolCallback> getToolCallbacks(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return Collections.emptyList();
        }

        Object[] tools = toolNames.stream()
                .map(name -> {
                    // 支持多种命名格式：weather, weatherTools, WeatherTools
                    String normalizedName = name.toLowerCase()
                            .replace("tools", "")
                            .replace("tool", "");
                    return toolMap.get(normalizedName);
                })
                .filter(Objects::nonNull)
                .toArray();

        if (tools.length == 0) {
            return Collections.emptyList();
        }

        return Arrays.asList(ToolCallbacks.from(tools));
    }

    /**
     * 获取所有已注册工具的 ToolCallback 列表
     *
     * @return 所有工具的 ToolCallback 列表
     */
    public List<ToolCallback> getAllToolCallbacks() {
        Object[] tools = toolMap.values().toArray();
        if (tools.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.asList(ToolCallbacks.from(tools));
    }

    /**
     * 获取所有已注册的工具名称
     */
    public List<String> getRegisteredToolNames() {
        return Collections.unmodifiableList(registeredToolNames);
    }

    /**
     * 检查工具是否已注册
     */
    public boolean isToolRegistered(String name) {
        return toolMap.containsKey(name.toLowerCase());
    }

    /**
     * 获取工具数量
     */
    public int getToolCount() {
        return toolMap.size();
    }
}
