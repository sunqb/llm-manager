package com.llmmanager.ops.controller;

import com.llmmanager.common.result.Result;
import com.llmmanager.service.core.entity.Prompt;
import com.llmmanager.service.core.service.PromptService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/prompts")
public class PromptController {

    @Resource
    private PromptService promptService;

    @GetMapping
    public Result<List<Prompt>> getAll() {
        return Result.success(promptService.findAll());
    }

    @PostMapping
    public Result<Prompt> create(@RequestBody Prompt prompt) {
        return Result.success(promptService.create(prompt));
    }
}
