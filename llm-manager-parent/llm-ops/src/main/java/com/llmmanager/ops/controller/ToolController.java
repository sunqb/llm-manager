package com.llmmanager.ops.controller;

import com.llmmanager.agent.config.ToolFunctionManager;
import com.llmmanager.common.exception.BusinessException;
import com.llmmanager.common.result.Result;
import com.llmmanager.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 工具管理 Controller
 * 提供工具列表查询等功能，供前端选择工具使用
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
    public Result<Map<String, Object>> listTools() {
        return Result.success(Map.of(
                "tools", toolFunctionManager.getAllTools(),
                "count", toolFunctionManager.getToolCount()
        ));
    }

    /**
     * 获取工具详情
     *
     * @param toolName 工具名称
     * @return 工具详情
     */
    @GetMapping("/{toolName}")
    public Result<Map<String, Object>> getToolDetail(@PathVariable String toolName) {
        ToolFunctionManager.ToolInfo toolInfo = toolFunctionManager.getToolInfo(toolName);
        if (toolInfo == null) {
            throw new BusinessException(ResultCode.TOOL_NOT_FOUND, "工具不存在: " + toolName);
        }

        return Result.success(Map.of(
                "name", toolInfo.name(),
                "description", toolInfo.description(),
                "beanName", toolInfo.beanName(),
                "beanClass", toolInfo.beanClass().getSimpleName()
        ));
    }

    /**
     * 检查工具是否存在
     *
     * @param toolName 工具名称
     * @return 是否存在
     */
    @GetMapping("/{toolName}/exists")
    public Result<Map<String, Boolean>> checkToolExists(@PathVariable String toolName) {
        return Result.success(Map.of("exists", toolFunctionManager.hasTool(toolName)));
    }
}
