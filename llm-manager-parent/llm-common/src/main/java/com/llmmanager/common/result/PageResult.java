package com.llmmanager.common.result;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果
 *
 * @param <T> 数据类型
 */
@Data
public class PageResult<T> implements Serializable {

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
     * 是否成功
     */
    private Boolean success;

    /**
     * 数据列表
     */
    private List<T> records;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 当前页码
     */
    private Long current;

    /**
     * 每页大小
     */
    private Long size;

    /**
     * 总页数
     */
    private Long pages;

    public PageResult() {
        this.time = LocalDateTime.now().format(FORMATTER);
    }

    /**
     * 创建成功的分页结果
     */
    public static <T> PageResult<T> success(List<T> records, long total, long current, long size) {
        PageResult<T> result = new PageResult<>();
        result.setSuccess(true);
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMsg(ResultCode.SUCCESS.getMsg());
        result.setRecords(records);
        result.setTotal(total);
        result.setCurrent(current);
        result.setSize(size);
        result.setPages(size > 0 ? (total + size - 1) / size : 0);
        return result;
    }

    /**
     * 从 MyBatis-Plus IPage 创建
     */
    public static <T> PageResult<T> fromPage(com.baomidou.mybatisplus.core.metadata.IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setSuccess(true);
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMsg(ResultCode.SUCCESS.getMsg());
        result.setRecords(page.getRecords());
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setPages(page.getPages());
        return result;
    }

    /**
     * 创建空的分页结果
     */
    public static <T> PageResult<T> empty(long current, long size) {
        PageResult<T> result = new PageResult<>();
        result.setSuccess(true);
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMsg(ResultCode.SUCCESS.getMsg());
        result.setRecords(Collections.emptyList());
        result.setTotal(0L);
        result.setCurrent(current);
        result.setSize(size);
        result.setPages(0L);
        return result;
    }

    /**
     * 创建失败的分页结果
     */
    public static <T> PageResult<T> fail(String msg) {
        PageResult<T> result = new PageResult<>();
        result.setSuccess(false);
        result.setCode(ResultCode.SYSTEM_ERROR.getCode());
        result.setMsg(msg);
        result.setRecords(Collections.emptyList());
        result.setTotal(0L);
        result.setPages(0L);
        return result;
    }

    /**
     * 创建失败的分页结果
     */
    public static <T> PageResult<T> fail(ErrorCode errorCode) {
        PageResult<T> result = new PageResult<>();
        result.setSuccess(false);
        result.setCode(errorCode.getCode());
        result.setMsg(errorCode.getMsg());
        result.setRecords(Collections.emptyList());
        result.setTotal(0L);
        result.setPages(0L);
        return result;
    }
}
