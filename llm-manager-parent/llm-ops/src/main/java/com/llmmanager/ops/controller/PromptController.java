package com.llmmanager.ops.controller;

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
    public List<Prompt> getAll() {
        return promptService.findAll();
    }

    @PostMapping
    public Prompt create(@RequestBody Prompt prompt) {
        return promptService.create(prompt);
    }
}
