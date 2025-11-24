package com.example.llmmanager.controller;

import com.example.llmmanager.entity.Channel;
import com.example.llmmanager.repository.ChannelRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/channels")
public class ChannelController {

    private final ChannelRepository repository;

    public ChannelController(ChannelRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Channel> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public Channel create(@RequestBody Channel channel) {
        return repository.save(channel);
    }

    @GetMapping("/{id}")
    public Channel get(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
    }

    @PutMapping("/{id}")
    public Channel update(@PathVariable Long id, @RequestBody Channel updated) {
        Channel existing = get(id);
        existing.setName(updated.getName());
        existing.setApiKey(updated.getApiKey());
        existing.setBaseUrl(updated.getBaseUrl());
        existing.setType(updated.getType());
        existing.setAdditionalConfig(updated.getAdditionalConfig());
        return repository.save(existing);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        repository.deleteById(id);
    }
}
