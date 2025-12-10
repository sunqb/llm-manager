package com.llmmanager.agent.storage.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.llmmanager.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Graph 工作流步骤执行记录
 * 记录工作流中每个节点的执行情况
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("a_graph_steps")
public class GraphStep extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 步骤唯一标识（UUID）
     */
    private String stepCode;

    /**
     * 关联的任务标识
     */
    private String taskCode;

    /**
     * 节点名称
     */
    private String nodeName;

    /**
     * 迭代轮次（从1开始）
     */
    private Integer iterationRound;

    /**
     * 步骤序号（同一轮次内从1开始）
     */
    private Integer stepIndex;

    /**
     * 输入数据（JSON）
     */
    private String inputData;

    /**
     * 输出数据（JSON）
     */
    private String outputData;

    /**
     * 步骤状态
     * PENDING / RUNNING / SUCCESS / FAILED / SKIPPED
     */
    private String status;

    /**
     * 耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 错误信息
     */
    private String errorMessage;
}
