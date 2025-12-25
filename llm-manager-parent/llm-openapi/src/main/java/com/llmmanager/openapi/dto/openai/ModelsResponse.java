package com.llmmanager.openapi.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OpenAI 兼容的 Models 列表响应
 *
 * @author LLM Manager
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelsResponse {

    /**
     * 对象类型
     */
    @Builder.Default
    private String object = "list";

    /**
     * 模型列表
     */
    private List<Model> data;

    /**
     * 模型信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Model {
        /**
         * 模型 ID
         */
        private String id;

        /**
         * 对象类型
         */
        @Builder.Default
        private String object = "model";

        /**
         * 创建时间
         */
        @Builder.Default
        private Long created = System.currentTimeMillis() / 1000;

        /**
         * 所有者
         */
        @JsonProperty("owned_by")
        @Builder.Default
        private String ownedBy = "llm-manager";

        /**
         * 描述（扩展字段）
         */
        private String description;

        /**
         * Agent 类型（扩展字段）
         */
        @JsonProperty("agent_type")
        private String agentType;
    }
}
