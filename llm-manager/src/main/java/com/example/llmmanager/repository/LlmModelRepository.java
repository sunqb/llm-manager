package com.example.llmmanager.repository;

import com.example.llmmanager.entity.LlmModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmModelRepository extends JpaRepository<LlmModel, Long> {
}
