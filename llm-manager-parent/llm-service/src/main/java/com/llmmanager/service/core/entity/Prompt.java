package com.llmmanager.service.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.llmmanager.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 提示词模板实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("p_prompt")
public class Prompt extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 模板名称
     */
    private String name;

    /**
     * 模板内容（支持变量占位符）
     */
    private String content;

    /**
     * 模板描述
     */
    private String description;
}
