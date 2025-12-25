package com.llmmanager.ops.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.llmmanager.common.exception.BusinessException;
import com.llmmanager.common.result.Result;
import com.llmmanager.common.result.ResultCode;
import com.llmmanager.service.core.entity.User;
import com.llmmanager.service.core.service.UserService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource
    private UserService userService;

    @PostMapping("/login")
    public Result<Object> login(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw BusinessException.paramError("用户名和密码不能为空");
        }

        User user = userService.findByUsername(username);

        if (user == null || !user.getPassword().equals(password)) {
            throw new BusinessException(ResultCode.LOGIN_FAILED, "用户名或密码错误");
        }

        StpUtil.login(user.getId());
        return Result.success(StpUtil.getTokenInfo());
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.success();
    }

    @GetMapping("/info")
    public Result<Object> info() {
        if (StpUtil.isLogin()) {
            return Result.success(Map.of("id", StpUtil.getLoginId(), "username", "admin"));
        }
        return Result.unauthorized("未登录");
    }
}
