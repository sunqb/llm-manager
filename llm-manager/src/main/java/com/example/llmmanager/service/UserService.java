package com.example.llmmanager.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.llmmanager.entity.User;
import com.example.llmmanager.mapper.UserMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

@Service
public class UserService extends ServiceImpl<UserMapper, User> {

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

    public User findByUsername(String username) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        return getOne(queryWrapper);
    }
}
