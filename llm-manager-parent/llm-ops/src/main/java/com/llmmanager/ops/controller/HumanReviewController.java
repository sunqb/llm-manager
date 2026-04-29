package com.llmmanager.ops.controller;

import com.llmmanager.agent.review.dto.PendingReviewVO;
import com.llmmanager.agent.review.dto.ReviewRejectRequest;
import com.llmmanager.agent.review.dto.ReviewSubmitRequest;
import com.llmmanager.agent.storage.core.entity.PendingReview;
import com.llmmanager.agent.storage.core.service.PendingReviewService;
import com.llmmanager.common.result.Result;
import com.llmmanager.service.orchestration.HumanReviewOrchestrationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 人工审核 REST API 控制器
 *
 * 提供审核管理的 HTTP 接口：
 * - 获取待审核列表
 * - 获取审核详情
 * - 提交审核结果（批准/拒绝）
 *
 * @author LLM Manager
 */
@Slf4j
@RestController
@RequestMapping("/api/human-review")
public class HumanReviewController {

    @Resource
    private PendingReviewService pendingReviewService;

    @Resource
    private HumanReviewOrchestrationService humanReviewOrchestrationService;

    /**
     * 提交审核结果（统一接口）
     *
     * POST /api/human-review/submit/{reviewCode}
     *
     * @param reviewCode 审核标识
     * @param request    审核提交请求
     * @return 提交结果
     */
    @PostMapping("/submit/{reviewCode}")
    public Result<Void> submitReview(
            @PathVariable String reviewCode,
            @Validated @RequestBody ReviewSubmitRequest request) {

        log.info("[HumanReviewController] 提交审核: {}, 结果: {}", reviewCode, request.getApprove());

        try {
            if (Boolean.TRUE.equals(request.getApprove())) {
                // 批准审核
                humanReviewOrchestrationService.approveReview(
                        reviewCode,
                        request.getReviewerId(),
                        request.getReviewComment()
                );
            } else {
                // 拒绝审核
                humanReviewOrchestrationService.rejectReview(
                        reviewCode,
                        request.getReviewerId(),
                        request.getReviewComment()
                );
            }

            return Result.success();
        } catch (IllegalArgumentException e) {
            log.warn("[HumanReviewController] 审核参数错误: {}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("[HumanReviewController] 提交审核失败", e);
            return Result.fail("提交审核失败: " + e.getMessage());
        }
    }

    /**
     * 批准审核（快捷接口）
     *
     * POST /api/human-review/approve/{reviewCode}
     *
     * @param reviewCode    审核标识
     * @param reviewComment 审核意见（可选）
     * @return 批准结果
     */
    @PostMapping("/approve/{reviewCode}")
    public Result<Void> approveReview(
            @PathVariable String reviewCode,
            @RequestParam(required = false) String reviewComment) {

        log.info("[HumanReviewController] 批准审核: {}", reviewCode);

        try {
            humanReviewOrchestrationService.approveReview(reviewCode, null, reviewComment);
            return Result.success();
        } catch (Exception e) {
            log.error("[HumanReviewController] 批准审核失败", e);
            return Result.fail("批准审核失败: " + e.getMessage());
        }
    }

    /**
     * 拒绝审核（快捷接口）
     *
     * POST /api/human-review/reject/{reviewCode}
     *
     * @param reviewCode 审核标识
     * @param request    拒绝请求
     * @return 拒绝结果
     */
    @PostMapping("/reject/{reviewCode}")
    public Result<Void> rejectReview(
            @PathVariable String reviewCode,
            @Validated @RequestBody ReviewRejectRequest request) {

        log.info("[HumanReviewController] 拒绝审核: {}, 原因: {}", reviewCode, request.getRejectReason());

        try {
            humanReviewOrchestrationService.rejectReview(
                    reviewCode,
                    request.getReviewerId(),
                    request.getRejectReason()
            );
            return Result.success();
        } catch (Exception e) {
            log.error("[HumanReviewController] 拒绝审核失败", e);
            return Result.fail("拒绝审核失败: " + e.getMessage());
        }
    }

    /**
     * 获取待审核列表
     *
     * GET /api/human-review/pending?limit=20
     *
     * @param limit 查询数量限制（默认 20，0 表示全部）
     * @return 待审核记录列表
     */
    @GetMapping("/pending")
    public Result<List<PendingReviewVO>> getPendingReviews(
            @RequestParam(defaultValue = "20") int limit) {

        log.info("[HumanReviewController] 获取待审核列表，limit: {}", limit);

        try {
            List<PendingReview> reviews = pendingReviewService.findPendingReviews(limit);

            List<PendingReviewVO> vos = reviews.stream()
                    .map(PendingReviewVO::fromEntity)
                    .collect(Collectors.toList());

            return Result.success(vos);
        } catch (Exception e) {
            log.error("[HumanReviewController] 获取待审核列表失败", e);
            return Result.fail("获取待审核列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取审核详情
     *
     * GET /api/human-review/{reviewCode}
     *
     * @param reviewCode 审核标识
     * @return 审核详情
     */
    @GetMapping("/{reviewCode}")
    public Result<PendingReviewVO> getReviewDetail(@PathVariable String reviewCode) {
        log.info("[HumanReviewController] 获取审核详情: {}", reviewCode);

        try {
            PendingReview review = pendingReviewService.getByReviewCode(reviewCode);
            PendingReviewVO vo = PendingReviewVO.fromEntity(review);
            return Result.success(vo);
        } catch (IllegalArgumentException e) {
            log.warn("[HumanReviewController] 审核记录不存在: {}", reviewCode);
            return Result.fail("审核记录不存在: " + reviewCode);
        } catch (Exception e) {
            log.error("[HumanReviewController] 获取审核详情失败", e);
            return Result.fail("获取审核详情失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定类型的待审核列表
     *
     * GET /api/human-review/pending/{reviewType}?limit=20
     *
     * @param reviewType 审核类型
     * @param limit      查询数量限制
     * @return 待审核记录列表
     */
    @GetMapping("/pending/{reviewType}")
    public Result<List<PendingReviewVO>> getPendingReviewsByType(
            @PathVariable String reviewType,
            @RequestParam(defaultValue = "20") int limit) {

        log.info("[HumanReviewController] 获取待审核列表，类型: {}, limit: {}", reviewType, limit);

        try {
            PendingReview.ReviewType type = PendingReview.ReviewType.valueOf(reviewType.toUpperCase());
            List<PendingReview> reviews = pendingReviewService.findByReviewType(type, limit);

            List<PendingReviewVO> vos = reviews.stream()
                    .map(PendingReviewVO::fromEntity)
                    .collect(Collectors.toList());

            return Result.success(vos);
        } catch (IllegalArgumentException e) {
            log.warn("[HumanReviewController] 无效的审核类型: {}", reviewType);
            return Result.fail("无效的审核类型: " + reviewType);
        } catch (Exception e) {
            log.error("[HumanReviewController] 获取待审核列表失败", e);
            return Result.fail("获取待审核列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据会话标识获取审核记录
     *
     * GET /api/human-review/conversation/{conversationCode}
     *
     * @param conversationCode 会话标识
     * @return 审核记录列表
     */
    @GetMapping("/conversation/{conversationCode}")
    public Result<List<PendingReviewVO>> getReviewsByConversation(@PathVariable String conversationCode) {
        log.info("[HumanReviewController] 获取会话的审核记录: {}", conversationCode);

        try {
            List<PendingReview> reviews = pendingReviewService.findByConversationCode(conversationCode);

            List<PendingReviewVO> vos = reviews.stream()
                    .map(PendingReviewVO::fromEntity)
                    .collect(Collectors.toList());

            return Result.success(vos);
        } catch (Exception e) {
            log.error("[HumanReviewController] 获取会话审核记录失败", e);
            return Result.fail("获取会话审核记录失败: " + e.getMessage());
        }
    }

    /**
     * 根据 Graph 任务 ID 获取审核记录
     *
     * GET /api/human-review/graph-task/{graphTaskId}
     *
     * @param graphTaskId Graph 任务 ID
     * @return 审核记录列表
     */
    @GetMapping("/graph-task/{graphTaskId}")
    public Result<List<PendingReviewVO>> getReviewsByGraphTask(@PathVariable Long graphTaskId) {
        log.info("[HumanReviewController] 获取 Graph 任务的审核记录: {}", graphTaskId);

        try {
            List<PendingReview> reviews = pendingReviewService.findByGraphTaskId(graphTaskId);

            List<PendingReviewVO> vos = reviews.stream()
                    .map(PendingReviewVO::fromEntity)
                    .collect(Collectors.toList());

            return Result.success(vos);
        } catch (Exception e) {
            log.error("[HumanReviewController] 获取 Graph 任务审核记录失败", e);
            return Result.fail("获取 Graph 任务审核记录失败: " + e.getMessage());
        }
    }

    /**
     * 统计待审核数量
     *
     * GET /api/human-review/count/pending
     *
     * @return 待审核数量
     */
    @GetMapping("/count/pending")
    public Result<Integer> countPending() {
        log.info("[HumanReviewController] 统计待审核数量");

        try {
            int count = pendingReviewService.countPending();
            return Result.success(count);
        } catch (Exception e) {
            log.error("[HumanReviewController] 统计待审核数量失败", e);
            return Result.fail("统计待审核数量失败: " + e.getMessage());
        }
    }

    /**
     * 统计指定类型的待审核数量
     *
     * GET /api/human-review/count/pending/{reviewType}
     *
     * @param reviewType 审核类型
     * @return 待审核数量
     */
    @GetMapping("/count/pending/{reviewType}")
    public Result<Integer> countPendingByType(@PathVariable String reviewType) {
        log.info("[HumanReviewController] 统计待审核数量，类型: {}", reviewType);

        try {
            PendingReview.ReviewType type = PendingReview.ReviewType.valueOf(reviewType.toUpperCase());
            int count = pendingReviewService.countPendingByType(type);
            return Result.success(count);
        } catch (IllegalArgumentException e) {
            log.warn("[HumanReviewController] 无效的审核类型: {}", reviewType);
            return Result.fail("无效的审核类型: " + reviewType);
        } catch (Exception e) {
            log.error("[HumanReviewController] 统计待审核数量失败", e);
            return Result.fail("统计待审核数量失败: " + e.getMessage());
        }
    }
}
