package com.llmmanager.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天模型选项配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatOptions {

    /**
     * 模型标识符（如：gpt-4, gpt-3.5-turbo）
     */
    private String model;

    /**
     * 温度参数（0.0-2.0）
     * 控制输出的随机性
     */
    @Builder.Default
    private Double temperature = 0.7;

    /**
     * Top P 参数（0.0-1.0）
     * 控制输出的多样性
     */
    private Double topP;

    /**
     * 最大生成 token 数
     */
    private Integer maxTokens;

    /**
     * 停止序列
     */
    private String[] stop;

    /**
     * 频率惩罚（-2.0 到 2.0）
     */
    private Double frequencyPenalty;

    /**
     * 存在惩罚（-2.0 到 2.0）
     */
    private Double presencePenalty;

    /**
     * 是否流式输出
     */
    @Builder.Default
    private Boolean stream = false;
}
