package com.llmmanager.agent.review.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核拒绝请求 DTO
 *
 * @author LLM Manager
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRejectRequest {

    /**
     * 审核人 ID（可选，从登录用户获取）
     */
    private Long reviewerId;

    /**
     * 拒绝原因（必需）
     */
    @NotBlank(message = "拒绝原因不能为空")
    private String rejectReason;
}
