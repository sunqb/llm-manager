package com.llmmanager.service.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.llmmanager.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * LLM模型实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("p_llm_model")
public class LlmModel extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 模型名称
     */
    private String name;

    /**
     * 模型标识符（如：gpt-4, llama3）
     */
    private String modelIdentifier;

    /**
     * 关联的渠道ID
     */
    @TableField("channel_id")
    private Long channelId;

    /**
     * 模型描述
     */
    private String description;

    /**
     * 温度参数
     */
    private Double temperature = 0.7;

    /**
     * 最大token数
     */
    private Integer maxTokens;
}
