package com.llmmanager.ops.controller;

import com.llmmanager.common.result.Result;
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
    public Result<List<ApiKey>> getAll() {
        return Result.success(apiKeyService.findAll());
    }

    @PostMapping
    public Result<ApiKey> create(@RequestBody ApiKey apiKey) {
        return Result.success(apiKeyService.create(apiKey));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        apiKeyService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/revoke")
    public Result<ApiKey> revoke(@PathVariable Long id) {
        return Result.success(apiKeyService.revoke(id));
    }
}
