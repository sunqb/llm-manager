package com.llmmanager.agent.graph.dynamic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 节点配置 DTO
 * 用于定义工作流中的单个节点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeConfig {

    /**
     * 节点 ID（在工作流中的唯一标识）
     */
    private String id;

    /**
     * 节点类型（如：LLM_NODE, TOOL_NODE, CONDITION_NODE）
     */
    private String type;

    /**
     * 节点名称（可选，用于展示）
     */
    private String name;

    /**
     * 节点配置参数（JSON 对象，根据节点类型不同而不同）
     * 示例：
     * - LLM_NODE: {"prompt_template": "...", "output_key": "result"}
     * - TOOL_NODE: {"tool_name": "weather", "output_key": "weather_data"}
     */
    private Map<String, Object> config;

    /**
     * 节点描述（可选）
     */
    private String description;
}
