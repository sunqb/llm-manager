package com.llmmanager.agent.review.dto;

import com.llmmanager.agent.storage.core.entity.PendingReview;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 待审核记录视图对象 VO
 *
 * @author LLM Manager
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingReviewVO {

    /**
     * 审核唯一标识
     */
    private String reviewCode;

    /**
     * 审核类型
     */
    private String reviewType;

    /**
     * 审核类型描述
     */
    private String reviewTypeDesc;

    /**
     * 关联的 Graph 任务 ID
     */
    private Long graphTaskId;

    /**
     * 关联的会话标识
     */
    private String conversationCode;

    /**
     * 关联的 Agent 配置 Code
     */
    private String agentConfigCode;

    /**
     * 当前节点/Agent 名称
     */
    private String currentNode;

    /**
     * 展示给审核人的提示内容
     */
    private String reviewerPrompt;

    /**
     * 上下文字段列表
     */
    private String[] contextKeys;

    /**
     * 上下文数据（精简版，只显示关键信息）
     */
    private Map<String, Object> contextDataSummary;

    /**
     * 审核状态
     */
    private String status;

    /**
     * 审核状态描述
     */
    private String statusDesc;

    /**
     * 审核结果
     */
    private Boolean reviewResult;

    /**
     * 审核意见/拒绝原因
     */
    private String reviewComment;

    /**
     * 审核人 ID
     */
    private Long reviewerId;

    /**
     * 审核时间
     */
    private LocalDateTime reviewedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 从实体转换为 VO
     *
     * @param review 审核记录实体
     * @return VO 对象
     */
    public static PendingReviewVO fromEntity(PendingReview review) {
        if (review == null) {
            return null;
        }

        return PendingReviewVO.builder()
                .reviewCode(review.getReviewCode())
                .reviewType(review.getReviewType())
                .reviewTypeDesc(getReviewTypeDesc(review.getReviewType()))
                .graphTaskId(review.getGraphTaskId())
                .conversationCode(review.getConversationCode())
                .agentConfigCode(review.getAgentConfigCode())
                .currentNode(review.getCurrentNode())
                .reviewerPrompt(review.getReviewerPrompt())
                .contextKeys(review.getContextKeys())
                .contextDataSummary(buildContextSummary(review.getContextData()))
                .status(review.getStatus())
                .statusDesc(getStatusDesc(review.getStatus()))
                .reviewResult(review.getReviewResult())
                .reviewComment(review.getReviewComment())
                .reviewerId(review.getReviewerId())
                .reviewedAt(review.getReviewedAt())
                .createTime(review.getCreateTime())
                .updateTime(review.getUpdateTime())
                .build();
    }

    /**
     * 获取审核类型描述
     */
    private static String getReviewTypeDesc(String reviewType) {
        if (reviewType == null) {
            return null;
        }

        return switch (reviewType) {
            case "GRAPH_NODE" -> "Graph 工作流审核";
            case "REACT_AGENT_TOOL" -> "ReactAgent 工具调用审核";
            case "REACT_AGENT_SEQUENTIAL" -> "SEQUENTIAL 工作流审核";
            case "REACT_AGENT_SUPERVISOR" -> "SUPERVISOR 团队审核";
            default -> reviewType;
        };
    }

    /**
     * 获取状态描述
     */
    private static String getStatusDesc(String status) {
        if (status == null) {
            return null;
        }

        return switch (status) {
            case "PENDING" -> "待审核";
            case "APPROVED" -> "已批准";
            case "REJECTED" -> "已拒绝";
            default -> status;
        };
    }

    /**
     * 构建上下文数据摘要（只显示关键信息，避免数据过大）
     */
    private static Map<String, Object> buildContextSummary(Map<String, Object> contextData) {
        if (contextData == null) {
            return null;
        }

        // 只返回 snapshotType，不返回完整的 snapshot 数据（避免 VO 过大）
        String snapshotType = (String) contextData.get("snapshotType");
        if (snapshotType != null) {
            return Map.of("snapshotType", snapshotType);
        }

        return Map.of("dataKeys", contextData.keySet());
    }
}
