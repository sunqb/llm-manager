package com.llmmanager.common.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器
 * 自动填充创建时间、更新时间、创建人、更新人
 */
@Component
public class MyBatisPlusMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());

        // TODO: 从上下文获取当前登录用户
        String currentUser = getCurrentUser();
        this.strictInsertFill(metaObject, "createBy", String.class, currentUser);
        this.strictInsertFill(metaObject, "updateBy", String.class, currentUser);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());

        // TODO: 从上下文获取当前登录用户
        String currentUser = getCurrentUser();
        this.strictUpdateFill(metaObject, "updateBy", String.class, currentUser);
    }

    /**
     * 获取当前登录用户
     * TODO: 集成 Sa-Token 获取真实用户
     */
    private String getCurrentUser() {
        // 暂时返回系统用户
        return "system";
    }
}
