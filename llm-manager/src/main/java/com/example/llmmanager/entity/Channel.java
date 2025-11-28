package com.example.llmmanager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("channels")
public class Channel {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name; // e.g. "My OpenAI Account"

    private String type; // OPENAI, OLLAMA, AZURE_OPENAI

    private String baseUrl; // Optional, for custom endpoints
    private String apiKey;  // Encrypted in real app, plain for demo
    private String additionalConfig; // JSON or comma-separated for extra params (org-id, etc.)

    public enum ProviderType {
        OPENAI,
        OLLAMA,
        AZURE_OPENAI
    }
}
