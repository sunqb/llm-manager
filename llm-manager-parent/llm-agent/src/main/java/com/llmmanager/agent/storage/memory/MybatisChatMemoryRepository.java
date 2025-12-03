package com.llmmanager.agent.storage.memory;

import com.llmmanager.agent.storage.core.entity.ChatHistory;
import com.llmmanager.agent.storage.core.entity.ConversationTurn;
import com.llmmanager.agent.storage.core.service.ChatHistoryService;
import com.llmmanager.agent.storage.core.service.ConversationService;
import com.llmmanager.agent.storage.core.service.ConversationTurnService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MyBatis 实现的 ChatMemoryRepository
 *
 * 职责：将 Spring AI 的 ChatMemoryRepository 接口适配到 MySQL 存储（通过 Service 层）
 *
 * 数据流向：Spring AI ChatMemory → 本类 → ChatHistoryService/ConversationService/ConversationTurnService → Mapper → MySQL
 *
 * 会话管理：
 * - 保存消息时自动创建会话（如果不存在）
 * - 自动更新会话的消息计数和最后消息时间
 * - 自动创建和管理对话轮次（Turn）
 *
 * 命名规范：
 * - conversationCode：会话业务唯一标识（对应 Spring AI 的 conversationId 参数）
 * - messageCode：消息业务唯一标识
 * - turnCode：轮次业务唯一标识
 */
@Slf4j
@Repository
public class MybatisChatMemoryRepository implements ChatMemoryRepository {

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private ConversationService conversationService;

    @Resource
    private ConversationTurnService conversationTurnService;

    /**
     * 保存消息到指定会话
     * 注意：Spring AI 的 conversationId 参数在本系统中对应 conversationCode
     *
     * Turn 管理逻辑：
     * - 当检测到新的 USER 消息时，创建新的 Turn
     * - 将同一轮次的消息（USER + ASSISTANT）关联到同一个 Turn
     */
    @Override
    public void saveAll(String conversationCode, List<org.springframework.ai.chat.messages.Message> messages) {
        if (conversationCode == null || CollectionUtils.isEmpty(messages)) {
            return;
        }

        // 确保会话存在（如果不存在则创建）
        conversationService.getOrCreate(conversationCode);

        // 查询数据库中已有的最大消息序号
        Integer maxIndex = chatHistoryService.getMaxMessageIndex(conversationCode);
        int startIndex = (maxIndex == null) ? 0 : maxIndex + 1;

        // 只保存增量部分（新增的消息）
        if (messages.size() <= startIndex) {
            // 没有新消息需要保存
            return;
        }

        // 获取需要新增的消息（跳过已存在的部分）
        List<org.springframework.ai.chat.messages.Message> newMessages = messages.subList(startIndex, messages.size());

        // 创建或获取当前轮次的 Turn
        ConversationTurn currentTurn = null;
        String userMessageCode = null;
        String assistantMessageCode = null;

        List<ChatHistory> histories = new ArrayList<>();
        int currentIndex = startIndex;

        for (org.springframework.ai.chat.messages.Message message : newMessages) {
            String messageType = mapMessageType(message);

            // 当遇到 USER 消息时，创建新的 Turn
            if ("USER".equals(messageType)) {
                currentTurn = conversationTurnService.create(conversationCode);
                log.debug("[MybatisChatMemoryRepository] 创建新轮次: {}", currentTurn.getTurnCode());
            }
            // 当遇到 ASSISTANT 消息且 currentTurn 为空时，查询最近的未完成 Turn
            else if ("ASSISTANT".equals(messageType) && currentTurn == null) {
                currentTurn = conversationTurnService.findLatestPendingTurn(conversationCode).orElse(null);
                if (currentTurn != null) {
                    log.debug("[MybatisChatMemoryRepository] 找到未完成轮次: {}", currentTurn.getTurnCode());
                }
            }

            ChatHistory chatHistory = ChatHistory.create(
                    conversationCode,
                    currentIndex,
                    messageType,
                    message.getText()
            );

            // 关联 Turn
            if (currentTurn != null) {
                chatHistory.setTurnCode(currentTurn.getTurnCode());
            }

            // 保存元数据（可选）
            if (message.getMetadata() != null && !message.getMetadata().isEmpty()) {
                chatHistory.setMetadata(message.getMetadata());
            }

            histories.add(chatHistory);

            // 记录用户消息和助手消息的 messageCode
            if ("USER".equals(messageType)) {
                userMessageCode = chatHistory.getMessageCode();
            } else if ("ASSISTANT".equals(messageType)) {
                assistantMessageCode = chatHistory.getMessageCode();
            }

            currentIndex++;
        }

        // 批量保存消息
        chatHistoryService.saveBatch(histories);

        // 更新 Turn 的消息关联
        if (currentTurn != null) {
            if (userMessageCode != null) {
                conversationTurnService.updateUserMessageCode(currentTurn.getTurnCode(), userMessageCode);
            }
            if (assistantMessageCode != null) {
                conversationTurnService.updateAssistantMessageCode(currentTurn.getTurnCode(), assistantMessageCode);
                // 标记为成功（此时对话已完成）
                conversationTurnService.markSuccess(currentTurn.getTurnCode(), 0, 0, 0);
            }
        }

        // 更新会话的消息计数
        conversationService.incrementMessageCount(conversationCode, newMessages.size());

        log.debug("[MybatisChatMemoryRepository] 保存 {} 条消息到会话: {}, 轮次: {}",
                newMessages.size(), conversationCode, currentTurn != null ? currentTurn.getTurnCode() : "无");
    }

    /**
     * 查询指定会话的所有消息
     * 注意：Spring AI 的 conversationId 参数在本系统中对应 conversationCode
     */
    @Override
    public List<org.springframework.ai.chat.messages.Message> findByConversationId(String conversationCode) {
        if (conversationCode == null) {
            return Collections.emptyList();
        }

        List<ChatHistory> histories = chatHistoryService.findByConversationCode(conversationCode);

        if (CollectionUtils.isEmpty(histories)) {
            return Collections.emptyList();
        }

        // 转换为 Spring AI Message
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        for (ChatHistory history : histories) {
            org.springframework.ai.chat.messages.Message message = convertToSpringAiMessage(history);
            if (message != null) {
                messages.add(message);
            }
        }

        return messages;
    }

    /**
     * 查询指定数量的最近消息（扩展方法，非接口要求）
     */
    public List<org.springframework.ai.chat.messages.Message> findByConversationCode(String conversationCode, int lastN) {
        if (conversationCode == null) {
            return Collections.emptyList();
        }

        List<ChatHistory> histories = chatHistoryService.findRecentMessages(conversationCode, lastN);

        if (CollectionUtils.isEmpty(histories)) {
            return Collections.emptyList();
        }

        // 转换为 Spring AI Message
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        for (ChatHistory history : histories) {
            org.springframework.ai.chat.messages.Message message = convertToSpringAiMessage(history);
            if (message != null) {
                messages.add(message);
            }
        }

        return messages;
    }

    @Override
    public List<String> findConversationIds() {
        return conversationService.findAllConversationCodes();
    }

    /**
     * 删除指定会话的所有消息
     * 注意：Spring AI 的 conversationId 参数在本系统中对应 conversationCode
     */
    @Override
    public void deleteByConversationId(String conversationCode) {
        if (conversationCode == null) {
            return;
        }

        // 删除消息历史
        chatHistoryService.deleteByConversationCode(conversationCode);

        // 删除轮次记录
        conversationTurnService.deleteByConversationCode(conversationCode);

        // 删除会话
        conversationService.delete(conversationCode);

        log.debug("[MybatisChatMemoryRepository] 删除会话、轮次及消息: {}", conversationCode);
    }

    /**
     * 映射消息类型：Spring AI Message → 字符串
     */
    private String mapMessageType(org.springframework.ai.chat.messages.Message message) {
        if (message instanceof org.springframework.ai.chat.messages.SystemMessage) {
            return "SYSTEM";
        } else if (message instanceof org.springframework.ai.chat.messages.UserMessage) {
            return "USER";
        } else if (message instanceof org.springframework.ai.chat.messages.AssistantMessage) {
            return "ASSISTANT";
        } else {
            return "TOOL";
        }
    }

    /**
     * 转换：ChatHistory → Spring AI Message
     */
    private org.springframework.ai.chat.messages.Message convertToSpringAiMessage(ChatHistory history) {
        String messageType = history.getMessageType();
        String content = history.getContent();

        switch (messageType) {
            case "SYSTEM":
                return new org.springframework.ai.chat.messages.SystemMessage(content);
            case "USER":
                return new org.springframework.ai.chat.messages.UserMessage(content);
            case "ASSISTANT":
                return new org.springframework.ai.chat.messages.AssistantMessage(content);
            case "TOOL":
                // Spring AI 的 ToolResponseMessage 需要更多参数，暂时返回 null
                return null;
            default:
                return new org.springframework.ai.chat.messages.UserMessage(content);
        }
    }
}
