package com.example.llmmanager.controller;

import com.example.llmmanager.entity.ApiKey;
import com.example.llmmanager.service.ApiKeyService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tokens")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @GetMapping
    public List<ApiKey> getAll() {
        return apiKeyService.findAll();
    }

    @PostMapping
    public ApiKey create(@RequestBody ApiKey apiKey) {
        return apiKeyService.create(apiKey);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        apiKeyService.delete(id);
    }

    @PostMapping("/{id}/revoke")
    public ApiKey revoke(@PathVariable Long id) {
        return apiKeyService.revoke(id);
    }
}
