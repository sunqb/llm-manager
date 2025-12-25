package com.llmmanager.ops.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.llmmanager.common.result.Result;
import com.llmmanager.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Sa-Token 认证异常处理器
 * <p>
 * 专门处理 Sa-Token 相关的认证、授权异常
 */
@Slf4j
@RestControllerAdvice
@Order(10)  // 高优先级，优先于 GlobalExceptionHandler 处理
public class SaTokenExceptionHandler {

    /**
     * 处理 Sa-Token 未登录异常
     */
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleNotLoginException(NotLoginException e) {
        log.warn("[未登录] type={}, message={}", e.getType(), e.getMessage());
        return Result.fail(ResultCode.UNAUTHORIZED, "未登录或登录已过期");
    }

    /**
     * 处理 Sa-Token 无权限异常
     */
    @ExceptionHandler(NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNotPermissionException(NotPermissionException e) {
        log.warn("[无权限] permission={}", e.getPermission());
        return Result.fail(ResultCode.FORBIDDEN, "无权限访问: " + e.getPermission());
    }

    /**
     * 处理 Sa-Token 无角色异常
     */
    @ExceptionHandler(NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNotRoleException(NotRoleException e) {
        log.warn("[无角色] role={}", e.getRole());
        return Result.fail(ResultCode.FORBIDDEN, "无角色权限: " + e.getRole());
    }
}
