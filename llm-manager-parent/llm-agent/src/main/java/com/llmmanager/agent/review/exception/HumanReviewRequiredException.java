package com.llmmanager.agent.review.exception;

import lombok.Getter;

/**
 * 人工审核所需异常
 *
 * 当工作流或智能体需要人工审核时抛出此异常以暂停执行。
 * 异常包含审核标识和审核提示信息，用于后续恢复执行。
 *
 * 使用场景：
 * 1. Graph 工作流中的 HUMAN_REVIEW_NODE 节点
 * 2. ReactAgent 调用 HumanReviewTool
 * 3. SEQUENTIAL 工作流的 Agent 间审核
 * 4. SUPERVISOR 团队的人工审核
 *
 * @author LLM Manager
 */
@Getter
public class HumanReviewRequiredException extends RuntimeException {

    /**
     * 审核唯一标识（32位UUID，无连字符）
     */
    private final String reviewCode;

    /**
     * 展示给审核人的提示内容
     */
    private final String reviewPrompt;

    /**
     * 审核类型（可选）
     */
    private final String reviewType;

    /**
     * 构造函数
     *
     * @param reviewCode 审核唯一标识
     * @param reviewPrompt 审核提示内容
     */
    public HumanReviewRequiredException(String reviewCode, String reviewPrompt) {
        super("需要人工审核 - Review Code: " + reviewCode);
        this.reviewCode = reviewCode;
        this.reviewPrompt = reviewPrompt;
        this.reviewType = null;
    }

    /**
     * 构造函数（带审核类型）
     *
     * @param reviewCode 审核唯一标识
     * @param reviewPrompt 审核提示内容
     * @param reviewType 审核类型
     */
    public HumanReviewRequiredException(String reviewCode, String reviewPrompt, String reviewType) {
        super("需要人工审核 [" + reviewType + "] - Review Code: " + reviewCode);
        this.reviewCode = reviewCode;
        this.reviewPrompt = reviewPrompt;
        this.reviewType = reviewType;
    }

    /**
     * 构造函数（带原因）
     *
     * @param reviewCode 审核唯一标识
     * @param reviewPrompt 审核提示内容
     * @param cause 原因
     */
    public HumanReviewRequiredException(String reviewCode, String reviewPrompt, Throwable cause) {
        super("需要人工审核 - Review Code: " + reviewCode, cause);
        this.reviewCode = reviewCode;
        this.reviewPrompt = reviewPrompt;
        this.reviewType = null;
    }

    /**
     * 构造函数（完整参数）
     *
     * @param reviewCode 审核唯一标识
     * @param reviewPrompt 审核提示内容
     * @param reviewType 审核类型
     * @param cause 原因
     */
    public HumanReviewRequiredException(String reviewCode, String reviewPrompt, String reviewType, Throwable cause) {
        super("需要人工审核 [" + reviewType + "] - Review Code: " + reviewCode, cause);
        this.reviewCode = reviewCode;
        this.reviewPrompt = reviewPrompt;
        this.reviewType = reviewType;
    }

    /**
     * 获取详细信息
     *
     * @return 详细信息字符串
     */
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("需要人工审核").append("\n");
        sb.append("审核标识: ").append(reviewCode).append("\n");
        if (reviewType != null) {
            sb.append("审核类型: ").append(reviewType).append("\n");
        }
        sb.append("审核提示: ").append(reviewPrompt);
        return sb.toString();
    }

    @Override
    public String toString() {
        return "HumanReviewRequiredException{" +
                "reviewCode='" + reviewCode + '\'' +
                ", reviewType='" + reviewType + '\'' +
                ", reviewPrompt='" + reviewPrompt + '\'' +
                '}';
    }
}
