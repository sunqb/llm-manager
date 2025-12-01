package com.llmmanager.service.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.llmmanager.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * API密钥实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("p_api_key")
public class ApiKey extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 密钥名称
     */
    private String name;

    /**
     * API令牌
     */
    private String token;

    /**
     * 是否激活（0：禁用，1：启用）
     */
    private Integer active = 1;

    /**
     * 过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 生成新的API令牌
     */
    public void generateToken() {
        if (token == null) {
            token = "sk-" + UUID.randomUUID().toString().replace("-", "");
        }
    }
}
