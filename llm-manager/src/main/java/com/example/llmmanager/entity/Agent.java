package com.example.llmmanager.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "agents")
public class Agent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // e.g. "Coding Assistant"
    
    @Column(unique = true)
    private String slug; // e.g. "coder-v1" for URL access

    @Column(length = 5000)
    private String systemPrompt; // The persona definition

    @ManyToOne
    @JoinColumn(name = "llm_model_id")
    private LlmModel llmModel;

    private Double temperatureOverride; // Optional override
}
