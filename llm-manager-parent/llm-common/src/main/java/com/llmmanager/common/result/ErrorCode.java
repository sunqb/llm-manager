package com.llmmanager.common.result;

/**
 * 错误码接口
 * <p>
 * 所有错误码枚举都应实现此接口
 */
public interface ErrorCode {

    /**
     * 获取错误码
     */
    Integer getCode();

    /**
     * 获取错误信息
     */
    String getMsg();
}
