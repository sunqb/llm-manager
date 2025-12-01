package com.llmmanager.service.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.llmmanager.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("p_agents")
public class Agent extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Agent名称
     */
    private String name;

    /**
     * Agent唯一标识（用于URL访问）
     */
    private String slug;

    /**
     * Agent描述
     */
    private String description;

    /**
     * 系统提示词（定义Agent角色）
     */
    private String systemPrompt;

    /**
     * 关联的LLM模型ID
     */
    @TableField("llm_model_id")
    private Long llmModelId;

    /**
     * 温度覆盖值（覆盖模型默认温度）
     */
    private Double temperatureOverride;
}
