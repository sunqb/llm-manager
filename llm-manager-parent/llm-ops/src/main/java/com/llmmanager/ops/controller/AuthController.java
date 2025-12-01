package com.llmmanager.ops.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.llmmanager.service.core.entity.User;
import com.llmmanager.service.core.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public SaResult login(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");

        User user = userService.findByUsername(username);
        
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
