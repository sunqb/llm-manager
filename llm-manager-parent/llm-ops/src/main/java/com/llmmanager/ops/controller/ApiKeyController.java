package com.llmmanager.ops.controller;

import com.llmmanager.service.core.entity.ApiKey;
import com.llmmanager.service.core.service.ApiKeyService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/tokens")
public class ApiKeyController {

    @Resource
    private ApiKeyService apiKeyService;

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
