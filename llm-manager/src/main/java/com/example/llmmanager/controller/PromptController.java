package com.example.llmmanager.controller;

import com.example.llmmanager.entity.Prompt;
import com.example.llmmanager.repository.PromptRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/prompts")
public class PromptController {

    private final PromptRepository repository;

    public PromptController(PromptRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Prompt> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public Prompt create(@RequestBody Prompt prompt) {
        return repository.save(prompt);
    }
}
