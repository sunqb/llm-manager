package com.example.llmmanager.controller;

import com.example.llmmanager.entity.Agent;
import com.example.llmmanager.repository.AgentRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentRepository repository;

    public AgentController(AgentRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Agent> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public Agent create(@RequestBody Agent agent) {
        return repository.save(agent);
    }

    @GetMapping("/{id}")
    public Agent get(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
    }

    @PutMapping("/{id}")
    public Agent update(@PathVariable Long id, @RequestBody Agent updated) {
        Agent existing = get(id);
        existing.setName(updated.getName());
        existing.setSlug(updated.getSlug());
        existing.setSystemPrompt(updated.getSystemPrompt());
        existing.setLlmModel(updated.getLlmModel());
        existing.setTemperatureOverride(updated.getTemperatureOverride());
        return repository.save(existing);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        repository.deleteById(id);
    }
}
