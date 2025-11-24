package com.example.llmmanager.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.util.SaResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public SaResult handlerNotLoginException(NotLoginException nle) {
        nle.printStackTrace();
        return SaResult.code(401).setMsg("未登录或登录过期");
    }

    @ExceptionHandler(Exception.class)
    public SaResult handlerException(Exception e) {
        e.printStackTrace();
        return SaResult.error(e.getMessage());
    }
}
