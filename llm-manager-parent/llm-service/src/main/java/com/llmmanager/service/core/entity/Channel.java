package com.llmmanager.service.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.llmmanager.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 渠道实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("p_channel")
public class Channel extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 渠道名称
     */
    private String name;

    /**
     * 渠道类型
     */
    private String type;

    /**
     * API基础URL
     */
    private String baseUrl;

    /**
     * API密钥（生产环境应加密）
     */
    private String apiKey;

    /**
     * 额外配置（JSON格式）
     */
    private String additionalConfig;

    /**
     * 渠道类型枚举
     */
    public enum ProviderType {
        OPENAI,
        OLLAMA,
        AZURE_OPENAI
    }
}
