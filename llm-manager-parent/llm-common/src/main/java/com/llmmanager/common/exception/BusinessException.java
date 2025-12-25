package com.llmmanager.common.exception;

import com.llmmanager.common.result.ErrorCode;
import com.llmmanager.common.result.ResultCode;

/**
 * 业务异常
 * <p>
 * 用于抛出业务逻辑相关的异常，会被全局异常处理器捕获并返回友好的错误信息
 */
public class BusinessException extends BaseException {

    private static final long serialVersionUID = 1L;

    public BusinessException(String message) {
        super(ResultCode.OPERATION_FAILED.getCode(), message);
    }

    public BusinessException(Integer code, String message) {
        super(code, message);
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public BusinessException(String message, Throwable cause) {
        super(ResultCode.OPERATION_FAILED.getCode(), message, cause);
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    // ==================== 快捷创建方法 ====================

    /**
     * 数据不存在异常
     */
    public static BusinessException notFound(String message) {
        return new BusinessException(ResultCode.DATA_NOT_FOUND, message);
    }

    /**
     * 参数错误异常
     */
    public static BusinessException paramError(String message) {
        return new BusinessException(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 操作失败异常
     */
    public static BusinessException operationFailed(String message) {
        return new BusinessException(ResultCode.OPERATION_FAILED, message);
    }

    /**
     * 数据已存在异常
     */
    public static BusinessException alreadyExists(String message) {
        return new BusinessException(ResultCode.DATA_ALREADY_EXISTS, message);
    }
}
