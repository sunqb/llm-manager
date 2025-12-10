package com.llmmanager.agent.storage.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.llmmanager.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Graph 工作流任务执行记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("a_graph_tasks")
public class GraphTask extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务唯一标识（UUID）
     */
    private String taskCode;

    /**
     * 关联的工作流配置ID
     */
    private Long graphWorkflowId;

    /**
     * 使用的模型ID
     */
    private Long modelId;

    /**
     * 关联的会话标识
     */
    private String conversationCode;

    /**
     * 用户问题/输入
     */
    private String question;

    /**
     * 最终答案/输出
     */
    private String answer;

    /**
     * 分析结果
     */
    private String analysis;

    /**
     * 任务状态
     * PENDING / RUNNING / SUCCESS / FAILED / CANCELLED
     */
    private String status;

    /**
     * 质量评分（0-100）
     */
    private Integer qualityScore;

    /**
     * 实际迭代次数
     */
    private Integer iterationCount;

    /**
     * 总耗时（毫秒）
     */
    private Long totalDurationMs;

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
