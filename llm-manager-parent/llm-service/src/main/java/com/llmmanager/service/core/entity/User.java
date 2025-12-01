package com.llmmanager.service.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.llmmanager.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("p_users")
public class User extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码（生产环境应加密）
     */
    private String password;

    /**
     * 邮箱
     */
    private String email;
}
