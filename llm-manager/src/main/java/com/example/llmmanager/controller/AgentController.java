package com.example.llmmanager.controller;

import com.example.llmmanager.entity.Agent;
import com.example.llmmanager.service.AgentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping
    public List<Agent> getAll() {
        return agentService.findAll();
    }

    @PostMapping
    public Agent create(@RequestBody Agent agent) {
        return agentService.create(agent);
    }

    @GetMapping("/{id}")
    public Agent get(@PathVariable Long id) {
        Agent agent = agentService.findById(id);
        if (agent == null) {
            throw new RuntimeException("Not found");
        }
        return agent;
    }

    @PutMapping("/{id}")
    public Agent update(@PathVariable Long id, @RequestBody Agent updated) {
        Agent existing = get(id);
        existing.setName(updated.getName());
        existing.setSlug(updated.getSlug());
        existing.setSystemPrompt(updated.getSystemPrompt());
        existing.setLlmModelId(updated.getLlmModelId());
        existing.setTemperatureOverride(updated.getTemperatureOverride());
        return agentService.update(existing);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        agentService.delete(id);
    }
}
