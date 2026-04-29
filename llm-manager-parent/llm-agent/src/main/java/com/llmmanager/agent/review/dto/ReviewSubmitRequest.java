package com.llmmanager.agent.review.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核提交请求 DTO
 *
 * @author LLM Manager
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSubmitRequest {

    /**
     * 审核人 ID（可选，从登录用户获取）
     */
    private Long reviewerId;

    /**
     * 审核意见（批准时可选，拒绝时必需）
     */
    private String reviewComment;

    /**
     * 审核结果：true=批准，false=拒绝
     */
    @NotNull(message = "审核结果不能为空")
    private Boolean approve;
}
