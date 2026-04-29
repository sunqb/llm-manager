package com.llmmanager.agent.storage.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.llmmanager.agent.storage.core.entity.PendingReview;
import com.llmmanager.agent.storage.core.mapper.PendingReviewMapper;
import com.llmmanager.agent.storage.core.service.PendingReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 人工审核记录 Service 实现
 *
 * @author LLM Manager
 */
@Slf4j
@Service
public class PendingReviewServiceImpl implements PendingReviewService {

    @Resource
    private PendingReviewMapper pendingReviewMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PendingReview create(PendingReview review) {
        if (review == null) {
            throw new IllegalArgumentException("审核记录不能为空");
        }

        // 如果未设置 reviewCode，自动生成
        if (review.getReviewCode() == null || review.getReviewCode().isEmpty()) {
            review.setReviewCode(PendingReview.generateReviewCode());
        }

        // 设置默认值
        if (review.getStatus() == null || review.getStatus().isEmpty()) {
            review.setStatus(PendingReview.ReviewStatus.PENDING.name());
        }
        if (review.getResumeAfterApproval() == null) {
            review.setResumeAfterApproval(true);
        }
        if (review.getMaxRetryCount() == null) {
            review.setMaxRetryCount(3);
        }
        if (review.getCurrentRetryCount() == null) {
            review.setCurrentRetryCount(0);
        }

        pendingReviewMapper.insert(review);
        log.info("[PendingReviewService] 创建审核记录: {}, 类型: {}", review.getReviewCode(), review.getReviewType());
        return review;
    }

    @Override
    public Optional<PendingReview> findByReviewCode(String reviewCode) {
        if (reviewCode == null || reviewCode.isEmpty()) {
            return Optional.empty();
        }
        // 使用 LambdaQueryWrapper 而非 @Select，确保 autoResultMap 生效（正确反序列化 contextData JSON 字段）
        PendingReview review = pendingReviewMapper.selectOne(
                new LambdaQueryWrapper<PendingReview>()
                        .eq(PendingReview::getReviewCode, reviewCode)
                        .eq(PendingReview::getIsDelete, 0)
        );
        return Optional.ofNullable(review);
    }

    @Override
    public PendingReview getByReviewCode(String reviewCode) {
        return findByReviewCode(reviewCode)
                .orElseThrow(() -> new IllegalArgumentException("审核记录不存在: " + reviewCode));
    }

    @Override
    public boolean exists(String reviewCode) {
        if (reviewCode == null || reviewCode.isEmpty()) {
            return false;
        }
        return pendingReviewMapper.existsByReviewCode(reviewCode) > 0;
    }

    @Override
    public List<PendingReview> findPendingReviews(int limit) {
        if (limit <= 0) {
            return findAllPendingReviews();
        }
        return pendingReviewMapper.selectPendingReviews(limit);
    }

    @Override
    public List<PendingReview> findAllPendingReviews() {
        return pendingReviewMapper.selectAllPendingReviews();
    }

    @Override
    public List<PendingReview> findByConversationCode(String conversationCode) {
        if (conversationCode == null || conversationCode.isEmpty()) {
            return Collections.emptyList();
        }
        return pendingReviewMapper.selectByConversationCode(conversationCode);
    }

    @Override
    public List<PendingReview> findByGraphTaskId(Long graphTaskId) {
        if (graphTaskId == null) {
            return Collections.emptyList();
        }
        return pendingReviewMapper.selectByGraphTaskId(graphTaskId);
    }

    @Override
    public List<PendingReview> findByReviewType(PendingReview.ReviewType reviewType, int limit) {
        if (reviewType == null) {
            return Collections.emptyList();
        }
        return pendingReviewMapper.selectByReviewType(reviewType.name(), limit);
    }

    @Override
    public List<PendingReview> findByReviewerId(Long reviewerId, int limit) {
        if (reviewerId == null) {
            return Collections.emptyList();
        }
        return pendingReviewMapper.selectByReviewerId(reviewerId, limit);
    }

    @Override
    public List<PendingReview> findByTimeRange(String startTime, String endTime) {
        if (startTime == null || endTime == null) {
            return Collections.emptyList();
        }
        return pendingReviewMapper.selectByTimeRange(startTime, endTime);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(String reviewCode, Long reviewerId, String reviewComment) {
        if (reviewCode == null || reviewCode.isEmpty()) {
            throw new IllegalArgumentException("审核标识不能为空");
        }

        PendingReview review = getByReviewCode(reviewCode);

        // 检查审核状态
        if (!review.isPending()) {
            throw new IllegalStateException("审核记录已处理，当前状态: " + review.getStatus());
        }

        // 更新审核结果
        int updated = pendingReviewMapper.updateReviewResult(
                reviewCode,
                PendingReview.ReviewStatus.APPROVED.name(),
                true,
                reviewerId,
                reviewComment
        );

        if (updated > 0) {
            log.info("[PendingReviewService] 批准审核: {}, 审核人: {}", reviewCode, reviewerId);
        } else {
            throw new IllegalStateException("更新审核结果失败: " + reviewCode);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(String reviewCode, Long reviewerId, String reviewComment) {
        if (reviewCode == null || reviewCode.isEmpty()) {
            throw new IllegalArgumentException("审核标识不能为空");
        }

        PendingReview review = getByReviewCode(reviewCode);

        // 检查审核状态
        if (!review.isPending()) {
            throw new IllegalStateException("审核记录已处理，当前状态: " + review.getStatus());
        }

        // 更新审核结果
        int updated = pendingReviewMapper.updateReviewResult(
                reviewCode,
                PendingReview.ReviewStatus.REJECTED.name(),
                false,
                reviewerId,
                reviewComment
        );

        if (updated > 0) {
            log.info("[PendingReviewService] 拒绝审核: {}, 审核人: {}, 原因: {}", reviewCode, reviewerId, reviewComment);
        } else {
            throw new IllegalStateException("更新审核结果失败: " + reviewCode);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(PendingReview review) {
        if (review == null) {
            throw new IllegalArgumentException("审核记录不能为空");
        }
        if (review.getReviewCode() == null || review.getReviewCode().isEmpty()) {
            throw new IllegalArgumentException("审核标识不能为空");
        }

        PendingReview existing = getByReviewCode(review.getReviewCode());
        review.setId(existing.getId());

        pendingReviewMapper.updateById(review);
        log.debug("[PendingReviewService] 更新审核记录: {}", review.getReviewCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateById(Long id, PendingReview review) {
        if (id == null) {
            throw new IllegalArgumentException("审核记录 ID 不能为空");
        }
        if (review == null) {
            throw new IllegalArgumentException("审核记录不能为空");
        }

        review.setId(id);
        pendingReviewMapper.updateById(review);
        log.debug("[PendingReviewService] 更新审核记录，ID: {}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementRetryCount(String reviewCode) {
        if (reviewCode == null || reviewCode.isEmpty()) {
            return;
        }

        int updated = pendingReviewMapper.incrementRetryCount(reviewCode);
        if (updated > 0) {
            log.debug("[PendingReviewService] 增加重试次数: {}", reviewCode);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String reviewCode) {
        if (reviewCode == null || reviewCode.isEmpty()) {
            return;
        }

        int updated = pendingReviewMapper.softDeleteByReviewCode(reviewCode);
        if (updated > 0) {
            log.info("[PendingReviewService] 删除审核记录: {}", reviewCode);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByConversationCode(String conversationCode) {
        if (conversationCode == null || conversationCode.isEmpty()) {
            return;
        }

        int updated = pendingReviewMapper.softDeleteByConversationCode(conversationCode);
        if (updated > 0) {
            log.info("[PendingReviewService] 删除会话的审核记录: {}, 数量: {}", conversationCode, updated);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByGraphTaskId(Long graphTaskId) {
        if (graphTaskId == null) {
            return;
        }

        int updated = pendingReviewMapper.softDeleteByGraphTaskId(graphTaskId);
        if (updated > 0) {
            log.info("[PendingReviewService] 删除 Graph 任务的审核记录: {}, 数量: {}", graphTaskId, updated);
        }
    }

    @Override
    public int countPending() {
        return pendingReviewMapper.countPending();
    }

    @Override
    public int countPendingByType(PendingReview.ReviewType reviewType) {
        if (reviewType == null) {
            return 0;
        }
        return pendingReviewMapper.countPendingByType(reviewType.name());
    }
}
