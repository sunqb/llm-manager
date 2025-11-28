package com.example.llmmanager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("llm_models")
public class LlmModel {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name; // Internal display name
    private String modelIdentifier; // The actual model string, e.g., "gpt-4", "llama3"

    @TableField("channel_id")
    private Long channelId;

    private String description;
    private Double temperature = 0.7;
}
