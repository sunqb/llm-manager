package com.example.llmmanager.controller;

import com.example.llmmanager.entity.LlmModel;
import com.example.llmmanager.repository.LlmModelRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/models")
public class ModelController {

    private final LlmModelRepository repository;

    public ModelController(LlmModelRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<LlmModel> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public LlmModel create(@RequestBody LlmModel model) {
        return repository.save(model);
    }

    @GetMapping("/{id}")
    public LlmModel get(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
    }

    @PutMapping("/{id}")
    public LlmModel update(@PathVariable Long id, @RequestBody LlmModel updated) {
        LlmModel existing = get(id);
        existing.setName(updated.getName());
        existing.setModelIdentifier(updated.getModelIdentifier());
        existing.setChannel(updated.getChannel());
        existing.setTemperature(updated.getTemperature());
        return repository.save(existing);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        repository.deleteById(id);
    }
}
