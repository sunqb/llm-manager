package com.example.llmmanager.service;

import com.example.llmmanager.entity.User;
import com.example.llmmanager.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void init() {
        if (userRepository.count() == 0) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword("123456");
            userRepository.save(admin);
            System.out.println("Default admin user created: admin/123456");
        }
    }
}
