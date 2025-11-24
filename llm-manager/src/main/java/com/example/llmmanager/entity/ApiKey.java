package com.example.llmmanager.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Table(name = "api_keys")
public class ApiKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // e.g. "Client A", "Mobile App"
    
    @Column(unique = true, nullable = false)
    private String token;

    private boolean active = true;

    private LocalDateTime createdAt;
    private LocalDateTime expiresAt; // Optional

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (token == null) {
            token = "sk-" + UUID.randomUUID().toString().replace("-", "");
        }
    }
}
