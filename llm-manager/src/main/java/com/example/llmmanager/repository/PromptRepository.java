package com.example.llmmanager.repository;

import com.example.llmmanager.entity.Prompt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromptRepository extends JpaRepository<Prompt, Long> {
}
