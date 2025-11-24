package com.example.llmmanager.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "channels")
public class Channel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // e.g. "My OpenAI Account"

    @Enumerated(EnumType.STRING)
    private ProviderType type; // OPENAI, OLLAMA, AZURE

    private String baseUrl; // Optional, for custom endpoints
    private String apiKey;  // Encrypted in real app, plain for demo
    private String additionalConfig; // JSON or comma-separated for extra params (org-id, etc.)

    public enum ProviderType {
        OPENAI,
        OLLAMA,
        AZURE_OPENAI
    }
}
