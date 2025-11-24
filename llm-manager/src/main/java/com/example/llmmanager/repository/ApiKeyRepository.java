package com.example.llmmanager.repository;

import com.example.llmmanager.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByToken(String token);
}
