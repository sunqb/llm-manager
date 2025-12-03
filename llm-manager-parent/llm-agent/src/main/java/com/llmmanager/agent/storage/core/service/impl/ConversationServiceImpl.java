package com.llmmanager.agent.storage.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.llmmanager.agent.storage.core.entity.Conversation;
import com.llmmanager.agent.storage.core.mapper.ConversationMapper;
import com.llmmanager.agent.storage.core.service.ConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 会话 Service 实现
 */
@Slf4j
@Service
public class ConversationServiceImpl implements ConversationService {

    @Resource
    private ConversationMapper conversationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Conversation create(String conversationCode) {
        Conversation conversation = Conversation.create(conversationCode);
        conversationMapper.insert(conversation);
        log.debug("[ConversationService] 创建会话: {}", conversationCode);
        return conversation;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Conversation create(String conversationCode, String agentSlug, Long modelId) {
        Conversation conversation = Conversation.create(conversationCode, agentSlug, modelId);
        conversationMapper.insert(conversation);
        log.debug("[ConversationService] 创建会话: {}, Agent: {}, Model: {}", conversationCode, agentSlug, modelId);
        return conversation;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Conversation getOrCreate(String conversationCode) {
        Conversation existing = conversationMapper.selectByConversationCode(conversationCode);
        if (existing != null) {
            return existing;
        }
        return create(conversationCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Conversation getOrCreate(String conversationCode, String agentSlug, Long modelId) {
        Conversation existing = conversationMapper.selectByConversationCode(conversationCode);
        if (existing != null) {
            // 更新关联信息（如果之前没有）
            boolean needUpdate = false;
            if (existing.getAgentSlug() == null && agentSlug != null) {
                existing.setAgentSlug(agentSlug);
                needUpdate = true;
            }
            if (existing.getModelId() == null && modelId != null) {
                existing.setModelId(modelId);
                needUpdate = true;
            }
            if (needUpdate) {
                conversationMapper.updateById(existing);
            }
            return existing;
        }
        return create(conversationCode, agentSlug, modelId);
    }

    @Override
    public Optional<Conversation> findByConversationCode(String conversationCode) {
        if (conversationCode == null) {
            return Optional.empty();
        }
        Conversation conversation = conversationMapper.selectByConversationCode(conversationCode);
        return Optional.ofNullable(conversation);
    }

    @Override
    public boolean exists(String conversationCode) {
        if (conversationCode == null) {
            return false;
        }
        return conversationMapper.existsByConversationCode(conversationCode) > 0;
    }

    @Override
    public List<Conversation> findByAgentSlug(String agentSlug) {
        if (agentSlug == null) {
            return Collections.emptyList();
        }
        return conversationMapper.selectByAgentSlug(agentSlug);
    }

    @Override
    public List<String> findAllConversationCodes() {
        return conversationMapper.selectAllConversationCodes();
    }

    @Override
    public List<Conversation> findAll(int page, int size, Boolean archived) {
        LambdaQueryWrapper<Conversation> queryWrapper = new LambdaQueryWrapper<>();

        if (archived != null) {
            queryWrapper.eq(Conversation::getIsArchived, archived ? 1 : 0);
        }

        queryWrapper.orderByDesc(Conversation::getIsPinned)
                    .orderByDesc(Conversation::getLastMessageTime);

        Page<Conversation> pageResult = conversationMapper.selectPage(
                new Page<>(page, size), queryWrapper);

        return pageResult.getRecords();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTitle(String conversationCode, String title) {
        if (conversationCode == null) {
            return;
        }
        conversationMapper.updateTitle(conversationCode, title);
        log.debug("[ConversationService] 更新会话标题: {} -> {}", conversationCode, title);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementMessageCount(String conversationCode, int count) {
        if (conversationCode == null || count <= 0) {
            return;
        }
        conversationMapper.incrementMessageCount(conversationCode, count);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addTokens(String conversationCode, int tokens) {
        if (conversationCode == null || tokens <= 0) {
            return;
        }
        conversationMapper.addTokens(conversationCode, tokens);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(String conversationCode) {
        if (conversationCode == null) {
            return;
        }
        conversationMapper.archiveByConversationCode(conversationCode);
        log.debug("[ConversationService] 归档会话: {}", conversationCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setPinned(String conversationCode, boolean pinned) {
        if (conversationCode == null) {
            return;
        }
        conversationMapper.updatePinned(conversationCode, pinned ? 1 : 0);
        log.debug("[ConversationService] 会话置顶状态: {} -> {}", conversationCode, pinned);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String conversationCode) {
        if (conversationCode == null) {
            return;
        }
        conversationMapper.softDeleteByConversationCode(conversationCode);
        log.debug("[ConversationService] 删除会话: {}", conversationCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Conversation conversation) {
        if (conversation == null || conversation.getId() == null) {
            return;
        }
        conversationMapper.updateById(conversation);
    }
}
