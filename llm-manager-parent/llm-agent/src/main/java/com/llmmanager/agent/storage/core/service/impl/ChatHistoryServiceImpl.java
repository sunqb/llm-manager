package com.llmmanager.agent.storage.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.llmmanager.agent.storage.core.entity.ChatHistory;
import com.llmmanager.agent.storage.core.mapper.ChatHistoryMapper;
import com.llmmanager.agent.storage.core.service.ChatHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 聊天历史 Service 实现
 */
@Service
public class ChatHistoryServiceImpl implements ChatHistoryService {

    @Resource
    private ChatHistoryMapper chatHistoryMapper;

    @Override
    public void save(ChatHistory chatHistory) {
        chatHistoryMapper.insert(chatHistory);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBatch(List<ChatHistory> histories) {
        if (histories == null || histories.isEmpty()) {
            return;
        }
        for (ChatHistory history : histories) {
            chatHistoryMapper.insert(history);
        }
    }

    @Override
    public List<ChatHistory> findByConversationId(String conversationId) {
        if (conversationId == null) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<ChatHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatHistory::getConversationId, conversationId)
                    .orderByAsc(ChatHistory::getCreateTime);

        return chatHistoryMapper.selectList(queryWrapper);
    }

    @Override
    public List<ChatHistory> findRecentMessages(String conversationId, int limit) {
        if (conversationId == null || limit <= 0) {
            return Collections.emptyList();
        }

        List<ChatHistory> histories = chatHistoryMapper.selectRecentMessages(conversationId, limit);

        // 反转列表（Mapper 返回倒序，这里转为正序）
        Collections.reverse(histories);

        return histories;
    }

    @Override
    public List<String> findAllConversationIds() {
        return chatHistoryMapper.selectList(new LambdaQueryWrapper<ChatHistory>()
                        .select(ChatHistory::getConversationId)
                        .groupBy(ChatHistory::getConversationId))
                .stream()
                .map(ChatHistory::getConversationId)
                .distinct()
                .toList();
    }

    @Override
    public int countByConversationId(String conversationId) {
        if (conversationId == null) {
            return 0;
        }

        LambdaQueryWrapper<ChatHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatHistory::getConversationId, conversationId);

        return Math.toIntExact(chatHistoryMapper.selectCount(queryWrapper));
    }

    @Override
    public Integer getMaxMessageIndex(String conversationId) {
        if (conversationId == null) {
            return null;
        }
        return chatHistoryMapper.getMaxMessageIndex(conversationId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByConversationId(String conversationId) {
        if (conversationId == null) {
            return;
        }

        // 软删除
        LambdaUpdateWrapper<ChatHistory> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ChatHistory::getConversationId, conversationId)
                     .set(ChatHistory::getIsDelete, 1);
        chatHistoryMapper.update(null, updateWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteExpiredMessages(int retentionDays) {
        if (retentionDays <= 0) {
            return 0;
        }

        // 计算过期时间（当前时间 - 保留天数）
        LocalDateTime expireTime = LocalDateTime.now().minusDays(retentionDays);

        // 硬删除超过保留天数的记录
        return chatHistoryMapper.deleteExpiredMessages(expireTime);
    }
}
