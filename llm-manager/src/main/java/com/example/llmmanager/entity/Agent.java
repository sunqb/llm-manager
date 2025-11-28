package com.example.llmmanager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("agents")
public class Agent {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name; // e.g. "Coding Assistant"
    
    private String slug; // e.g. "coder-v1" for URL access

    private String systemPrompt; // The persona definition

    @TableField("llm_model_id")
    private Long llmModelId;

    private Double temperatureOverride; // Optional override
}
