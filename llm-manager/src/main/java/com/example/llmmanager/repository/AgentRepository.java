package com.example.llmmanager.repository;

import com.example.llmmanager.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long> {
    Optional<Agent> findBySlug(String slug);
}
