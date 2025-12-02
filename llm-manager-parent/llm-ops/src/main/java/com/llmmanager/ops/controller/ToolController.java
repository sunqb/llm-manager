package com.llmmanager.ops.controller;

import com.llmmanager.agent.config.ToolFunctionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

/**
 * 工具管理 Controller
 * 提供工具列表查询等功能，供前端选择工具使用
 *
 * @author LLM Manager
 */
@Slf4j
@RestController
@RequestMapping("/api/tools")
public class ToolController {

    @Resource
    private ToolFunctionManager toolFunctionManager;

    /**
     * 获取所有可用的工具列表
     *
     * @return 工具列表 {name -> description}
     */
    @GetMapping
    public Map<String, Object> listTools() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("tools", toolFunctionManager.getAllTools());
        response.put("count", toolFunctionManager.getToolCount());
        return response;
    }

    /**
     * 获取工具详情
     *
     * @param toolName 工具名称
     * @return 工具详情
     */
    @GetMapping("/{toolName}")
    public Map<String, Object> getToolDetail(@PathVariable String toolName) {
        Map<String, Object> response = new HashMap<>();

        ToolFunctionManager.ToolInfo toolInfo = toolFunctionManager.getToolInfo(toolName);
        if (toolInfo == null) {
            response.put("success", false);
            response.put("error", "工具不存在: " + toolName);
            return response;
        }

        response.put("success", true);
        response.put("tool", Map.of(
                "name", toolInfo.name(),
                "description", toolInfo.description(),
                "beanName", toolInfo.beanName(),
                "beanClass", toolInfo.beanClass().getSimpleName()
        ));
        return response;
    }

    /**
     * 检查工具是否存在
     *
     * @param toolName 工具名称
     * @return 是否存在
     */
    @GetMapping("/{toolName}/exists")
    public Map<String, Object> checkToolExists(@PathVariable String toolName) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("exists", toolFunctionManager.hasTool(toolName));
        return response;
    }
}
