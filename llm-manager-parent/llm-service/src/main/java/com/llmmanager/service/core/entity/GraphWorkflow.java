package com.llmmanager.service.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.llmmanager.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Graph 工作流配置实体
 * 支持 DeepResearch 等高级工作流
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "p_graph_workflows", autoResultMap = true)
public class GraphWorkflow extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 工作流名称
     */
    private String name;

    /**
     * 唯一标识（用于URL访问）
     */
    private String slug;

    /**
     * 描述
     */
    private String description;

    /**
     * 工作流类型
     * DEEP_RESEARCH / SEQUENTIAL / PARALLEL / CUSTOM
     */
    private String workflowType;

    /**
     * 关联的默认模型ID
     */
    @TableField("llm_model_id")
    private Long llmModelId;

    /**
     * 最大迭代轮数
     */
    private Integer maxIterations;

    /**
     * 质量评分阈值（达到此分数停止迭代）
     */
    private Integer qualityThreshold;

    /**
     * 启用的工具列表（JSON数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> enabledTools;

    /**
     * MCP 服务器配置（JSON数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> mcpServers;

    /**
     * 系统提示词（定制工作流行为）
     */
    private String systemPrompt;

    /**
     * 是否启用
     */
    private Boolean isActive;
}
