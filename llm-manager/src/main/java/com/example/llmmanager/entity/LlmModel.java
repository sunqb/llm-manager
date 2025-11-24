package com.example.llmmanager.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "llm_models")
public class LlmModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // Internal display name
    private String modelIdentifier; // The actual model string, e.g., "gpt-4", "llama3"

    @ManyToOne
    @JoinColumn(name = "channel_id")
    private Channel channel;

    private String description;
    private Double temperature = 0.7;
}
