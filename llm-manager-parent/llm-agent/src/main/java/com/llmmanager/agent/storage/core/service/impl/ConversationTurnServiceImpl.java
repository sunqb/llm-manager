package com.llmmanager.agent.storage.core.service.impl;

import com.llmmanager.agent.storage.core.entity.ConversationTurn;
import com.llmmanager.agent.storage.core.mapper.ConversationTurnMapper;
import com.llmmanager.agent.storage.core.service.ConversationTurnService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 对话轮次 Service 实现
 */
@Slf4j
@Service
public class ConversationTurnServiceImpl implements ConversationTurnService {

    @Resource
    private ConversationTurnMapper conversationTurnMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConversationTurn create(String conversationCode) {
        int nextIndex = getNextTurnIndex(conversationCode);
        ConversationTurn turn = ConversationTurn.create(conversationCode, nextIndex);
        conversationTurnMapper.insert(turn);
        log.debug("[ConversationTurnService] 创建轮次: {}, 会话: {}, 序号: {}",
                turn.getTurnCode(), conversationCode, nextIndex);
        return turn;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConversationTurn create(String conversationCode, Long modelId, String modelIdentifier) {
        int nextIndex = getNextTurnIndex(conversationCode);
        ConversationTurn turn = ConversationTurn.create(conversationCode, nextIndex, modelId, modelIdentifier);
        conversationTurnMapper.insert(turn);
        log.debug("[ConversationTurnService] 创建轮次: {}, 会话: {}, 序号: {}, 模型: {}",
                turn.getTurnCode(), conversationCode, nextIndex, modelIdentifier);
        return turn;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(ConversationTurn turn) {
        if (turn == null) {
            return;
        }
        conversationTurnMapper.insert(turn);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ConversationTurn turn) {
        if (turn == null || turn.getId() == null) {
            return;
        }
        conversationTurnMapper.updateById(turn);
    }

    @Override
    public Optional<ConversationTurn> findByTurnCode(String turnCode) {
        if (turnCode == null) {
            return Optional.empty();
        }
        ConversationTurn turn = conversationTurnMapper.selectByTurnCode(turnCode);
        return Optional.ofNullable(turn);
    }

    @Override
    public List<ConversationTurn> findByConversationCode(String conversationCode) {
        if (conversationCode == null) {
            return Collections.emptyList();
        }
        return conversationTurnMapper.selectByConversationCode(conversationCode);
    }

    @Override
    public List<ConversationTurn> findRecentTurns(String conversationCode, int limit) {
        if (conversationCode == null || limit <= 0) {
            return Collections.emptyList();
        }
        List<ConversationTurn> turns = conversationTurnMapper.selectRecentTurns(conversationCode, limit);
        // 反转列表（Mapper 返回倒序，这里转为正序）
        Collections.reverse(turns);
        return turns;
    }

    @Override
    public int getNextTurnIndex(String conversationCode) {
        if (conversationCode == null) {
            return 0;
        }
        Integer maxIndex = conversationTurnMapper.getMaxTurnIndex(conversationCode);
        return (maxIndex == null) ? 0 : maxIndex + 1;
    }

    @Override
    public int countByConversationCode(String conversationCode) {
        if (conversationCode == null) {
            return 0;
        }
        return conversationTurnMapper.countByConversationCode(conversationCode);
    }

    @Override
    public int countSuccessTurns(String conversationCode) {
        if (conversationCode == null) {
            return 0;
        }
        return conversationTurnMapper.countSuccessTurns(conversationCode);
    }

    @Override
    public int sumTotalTokens(String conversationCode) {
        if (conversationCode == null) {
            return 0;
        }
        return conversationTurnMapper.sumTotalTokens(conversationCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markProcessing(String turnCode) {
        if (turnCode == null) {
            return;
        }
        conversationTurnMapper.updateStatus(turnCode, ConversationTurn.STATUS_PROCESSING);
        log.debug("[ConversationTurnService] 轮次标记为处理中: {}", turnCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markSuccess(String turnCode, int promptTokens, int completionTokens, int latencyMs) {
        if (turnCode == null) {
            return;
        }
        int totalTokens = promptTokens + completionTokens;
        conversationTurnMapper.updateCompletion(
                turnCode,
                ConversationTurn.STATUS_SUCCESS,
                promptTokens,
                completionTokens,
                totalTokens,
                latencyMs
        );
        log.debug("[ConversationTurnService] 轮次标记为成功: {}, tokens: {}, 耗时: {}ms",
                turnCode, totalTokens, latencyMs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(String turnCode, String errorMessage) {
        if (turnCode == null) {
            return;
        }
        // 先获取轮次，然后更新
        ConversationTurn turn = conversationTurnMapper.selectByTurnCode(turnCode);
        if (turn != null) {
            turn.markFailed(errorMessage);
            conversationTurnMapper.updateById(turn);
            log.debug("[ConversationTurnService] 轮次标记为失败: {}, 错误: {}", turnCode, errorMessage);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserMessageCode(String turnCode, String userMessageCode) {
        if (turnCode == null || userMessageCode == null) {
            return;
        }
        conversationTurnMapper.updateUserMessageCode(turnCode, userMessageCode);
        log.debug("[ConversationTurnService] 更新用户消息: turnCode={}, messageCode={}",
                turnCode, userMessageCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAssistantMessageCode(String turnCode, String assistantMessageCode) {
        if (turnCode == null || assistantMessageCode == null) {
            return;
        }
        conversationTurnMapper.updateAssistantMessageCode(turnCode, assistantMessageCode);
        log.debug("[ConversationTurnService] 更新助手消息: turnCode={}, messageCode={}",
                turnCode, assistantMessageCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByConversationCode(String conversationCode) {
        if (conversationCode == null) {
            return;
        }
        conversationTurnMapper.softDeleteByConversationCode(conversationCode);
        log.debug("[ConversationTurnService] 删除会话轮次: {}", conversationCode);
    }

    @Override
    public Optional<ConversationTurn> findLatestPendingTurn(String conversationCode) {
        if (conversationCode == null) {
            return Optional.empty();
        }
        ConversationTurn turn = conversationTurnMapper.selectLatestPendingTurn(conversationCode);
        return Optional.ofNullable(turn);
    }
}
