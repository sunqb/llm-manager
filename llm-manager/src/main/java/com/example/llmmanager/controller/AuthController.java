package com.example.llmmanager.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.example.llmmanager.entity.User;
import com.example.llmmanager.repository.UserRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public SaResult login(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");

        User user = userRepository.findByUsername(username).orElse(null);
        
        if (user == null || !user.getPassword().equals(password)) {
            return SaResult.error("Invalid username or password");
        }

        StpUtil.login(user.getId());
        return SaResult.data(StpUtil.getTokenInfo());
    }

    @PostMapping("/logout")
    public SaResult logout() {
        StpUtil.logout();
        return SaResult.ok();
    }
    
    @GetMapping("/info")
    public SaResult info() {
        if(StpUtil.isLogin()) {
             return SaResult.data(Map.of("id", StpUtil.getLoginId(), "username", "admin"));
        }
        return SaResult.error("Not logged in");
    }
}
