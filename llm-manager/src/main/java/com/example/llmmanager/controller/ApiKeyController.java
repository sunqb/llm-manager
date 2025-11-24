package com.example.llmmanager.controller;

import com.example.llmmanager.entity.ApiKey;
import com.example.llmmanager.repository.ApiKeyRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tokens")
public class ApiKeyController {

    private final ApiKeyRepository repository;

    public ApiKeyController(ApiKeyRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<ApiKey> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public ApiKey create(@RequestBody ApiKey apiKey) {
        // Token generation handled in @PrePersist if null, but we can force generation here too if needed
        return repository.save(apiKey);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        repository.deleteById(id);
    }

    @PostMapping("/{id}/revoke")
    public ApiKey revoke(@PathVariable Long id) {
        ApiKey key = repository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        key.setActive(false);
        return repository.save(key);
    }
}
