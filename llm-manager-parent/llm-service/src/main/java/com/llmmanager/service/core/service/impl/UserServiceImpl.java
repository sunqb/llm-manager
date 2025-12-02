package com.llmmanager.service.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.llmmanager.service.core.service.UserService;
import com.llmmanager.service.core.entity.User;
import com.llmmanager.service.core.mapper.UserMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * User Service 实现
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @PostConstruct
    public void init() {
        Long userCount = count();
        if (userCount == 0) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword("123456");
            save(admin);
            System.out.println("Default admin user created: admin/123456");
        }
    }

    @Override
    public User findByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        return getOne(queryWrapper);
    }
}
