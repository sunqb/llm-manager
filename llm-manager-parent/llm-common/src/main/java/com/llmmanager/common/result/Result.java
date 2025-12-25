package com.llmmanager.common.result;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 统一返回结果
 *
 * @param <T> 数据类型
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 状态码
     */
    private Integer code;

    /**
     * 响应时间
     */
    private String time;

    /**
     * 消息
     */
    private String msg;

    /**
     * 数据
     */
    private T data;

    /**
     * 是否成功
     */
    private Boolean success;

    public Result() {
        this.time = LocalDateTime.now().format(FORMATTER);
    }

    // ==================== 成功响应 ====================

    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMsg(ResultCode.SUCCESS.getMsg());
        return result;
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMsg(ResultCode.SUCCESS.getMsg());
        result.setData(data);
        return result;
    }

    public static <T> Result<T> success(T data, String msg) {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMsg(msg);
        result.setData(data);
        return result;
    }

    // ==================== 失败响应 ====================

    public static <T> Result<T> fail() {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setCode(ResultCode.SYSTEM_ERROR.getCode());
        result.setMsg(ResultCode.SYSTEM_ERROR.getMsg());
        return result;
    }

    public static <T> Result<T> fail(String msg) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setCode(ResultCode.SYSTEM_ERROR.getCode());
        result.setMsg(msg);
        return result;
    }

    public static <T> Result<T> fail(Integer code, String msg) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setCode(code);
        result.setMsg(msg);
        return result;
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setCode(errorCode.getCode());
        result.setMsg(errorCode.getMsg());
        return result;
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String msg) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setCode(errorCode.getCode());
        result.setMsg(msg);
        return result;
    }

    // ==================== 警告响应 ====================

    public static <T> Result<T> warn(String msg) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setCode(ResultCode.WARN.getCode());
        result.setMsg(msg);
        return result;
    }

    // ==================== 未授权响应 ====================

    public static <T> Result<T> unauthorized() {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setCode(ResultCode.UNAUTHORIZED.getCode());
        result.setMsg(ResultCode.UNAUTHORIZED.getMsg());
        return result;
    }

    public static <T> Result<T> unauthorized(String msg) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setCode(ResultCode.UNAUTHORIZED.getCode());
        result.setMsg(msg);
        return result;
    }

    // ==================== 辅助方法 ====================

    /**
     * 判断是否失败
     */
    public boolean isFailed() {
        return !Boolean.TRUE.equals(success);
    }

    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return Boolean.TRUE.equals(success);
    }
}
