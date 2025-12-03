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
        // 确保 messageCode 不为空
        if (chatHistory.getMessageCode() == null) {
            chatHistory.setMessageCode(ChatHistory.generateMessageCode());
        }
        chatHistoryMapper.insert(chatHistory);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBatch(List<ChatHistory> histories) {
        if (histories == null || histories.isEmpty()) {
            return;
        }
        for (ChatHistory history : histories) {
            // 确保 messageCode 不为空
            if (history.getMessageCode() == null) {
                history.setMessageCode(ChatHistory.generateMessageCode());
            }
            chatHistoryMapper.insert(history);
        }
    }

    @Override
    public List<ChatHistory> findByConversationCode(String conversationCode) {
        if (conversationCode == null) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<ChatHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatHistory::getConversationCode, conversationCode)
                    .orderByAsc(ChatHistory::getCreateTime);

        return chatHistoryMapper.selectList(queryWrapper);
    }

    @Override
    public List<ChatHistory> findByTurnCode(String turnCode) {
        if (turnCode == null) {
            return Collections.emptyList();
        }
        return chatHistoryMapper.selectByTurnCode(turnCode);
    }

    @Override
    public List<ChatHistory> findRecentMessages(String conversationCode, int limit) {
        if (conversationCode == null || limit <= 0) {
            return Collections.emptyList();
        }

        List<ChatHistory> histories = chatHistoryMapper.selectRecentMessages(conversationCode, limit);

        // 反转列表（Mapper 返回倒序，这里转为正序）
        Collections.reverse(histories);

        return histories;
    }

    @Override
    public ChatHistory findByMessageCode(String messageCode) {
        if (messageCode == null) {
            return null;
        }
        return chatHistoryMapper.selectByMessageCode(messageCode);
    }

    @Override
    public List<String> findAllConversationCodes() {
        return chatHistoryMapper.selectList(new LambdaQueryWrapper<ChatHistory>()
                        .select(ChatHistory::getConversationCode)
                        .groupBy(ChatHistory::getConversationCode))
                .stream()
                .map(ChatHistory::getConversationCode)
                .distinct()
                .toList();
    }

    @Override
    public int countByConversationCode(String conversationCode) {
        if (conversationCode == null) {
            return 0;
        }

        LambdaQueryWrapper<ChatHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatHistory::getConversationCode, conversationCode);

        return Math.toIntExact(chatHistoryMapper.selectCount(queryWrapper));
    }

    @Override
    public Integer getMaxMessageIndex(String conversationCode) {
        if (conversationCode == null) {
            return null;
        }
        return chatHistoryMapper.getMaxMessageIndex(conversationCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByConversationCode(String conversationCode) {
        if (conversationCode == null) {
            return;
        }

        // 软删除
        chatHistoryMapper.softDeleteByConversationCode(conversationCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteExpiredMessages(int retentionDays) {
        if (retentionDays <= 0) {
            return 0;
        }

        // 计算过期时间（当前时间 - 保留天数）
        LocalDateTime expireTime = LocalDateTime.now().minusDays(retentionDays);

        // 软删除超过保留天数的记录
        return chatHistoryMapper.deleteExpiredMessages(expireTime);
    }
}
