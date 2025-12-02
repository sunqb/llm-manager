package com.llmmanager.service.core.service;

import com.llmmanager.service.core.entity.User;

/**
 * User Service 接口
 */
public interface UserService {

    /**
     * 根据用户名查询用户
     */
    User findByUsername(String username);
}
