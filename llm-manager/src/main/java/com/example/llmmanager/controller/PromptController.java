package com.example.llmmanager.controller;

import com.example.llmmanager.entity.Prompt;
import com.example.llmmanager.service.PromptService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/prompts")
public class PromptController {

    private final PromptService promptService;

    public PromptController(PromptService promptService) {
        this.promptService = promptService;
    }

    @GetMapping
    public List<Prompt> getAll() {
        return promptService.findAll();
    }

    @PostMapping
    public Prompt create(@RequestBody Prompt prompt) {
        return promptService.create(prompt);
    }
}
