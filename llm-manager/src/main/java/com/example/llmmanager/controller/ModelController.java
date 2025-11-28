package com.example.llmmanager.controller;

import com.example.llmmanager.entity.LlmModel;
import com.example.llmmanager.service.LlmModelService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/models")
public class ModelController {

    private final LlmModelService llmModelService;

    public ModelController(LlmModelService llmModelService) {
        this.llmModelService = llmModelService;
    }

    @GetMapping
    public List<LlmModel> getAll() {
        return llmModelService.findAll();
    }

    @PostMapping
    public LlmModel create(@RequestBody LlmModel model) {
        return llmModelService.create(model);
    }

    @GetMapping("/{id}")
    public LlmModel get(@PathVariable Long id) {
        LlmModel model = llmModelService.findById(id);
        if (model == null) {
            throw new RuntimeException("Not found");
        }
        return model;
    }

    @PutMapping("/{id}")
    public LlmModel update(@PathVariable Long id, @RequestBody LlmModel updated) {
        LlmModel existing = get(id);
        existing.setName(updated.getName());
        existing.setModelIdentifier(updated.getModelIdentifier());
        existing.setChannelId(updated.getChannelId());
        existing.setTemperature(updated.getTemperature());
        return llmModelService.update(existing);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        llmModelService.delete(id);
    }
}
