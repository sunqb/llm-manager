package com.llmmanager.agent.model;

import com.llmmanager.agent.message.Message;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 聊天模型接口
 * 定义了与 LLM 交互的核心能力
 */
public interface ChatModel {

    /**
     * 同步对话（单轮）
     *
     * @param messages 消息列表
     * @param options  模型选项
     * @return 聊天响应
     */
    ChatResponse chat(List<Message> messages, ChatOptions options);

    /**
     * 流式对话（单轮）
     *
     * @param messages 消息列表
     * @param options  模型选项
     * @return 流式响应
     */
    Flux<String> streamChat(List<Message> messages, ChatOptions options);

    /**
     * 获取模型标识符
     */
    String getModelIdentifier();

    /**
     * 清除缓存
     */
    void clearCache();
}
