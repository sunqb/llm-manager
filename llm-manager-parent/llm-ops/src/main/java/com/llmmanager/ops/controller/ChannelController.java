package com.llmmanager.ops.controller;

import com.llmmanager.service.core.entity.Channel;
import com.llmmanager.service.core.ChannelService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/channels")
public class ChannelController {

    private final ChannelService channelService;

    public ChannelController(ChannelService channelService) {
        this.channelService = channelService;
    }

    @GetMapping
    public List<Channel> getAll() {
        return channelService.findAll();
    }

    @PostMapping
    public Channel create(@RequestBody Channel channel) {
        return channelService.create(channel);
    }

    @GetMapping("/{id}")
    public Channel get(@PathVariable Long id) {
        Channel channel = channelService.findById(id);
        if (channel == null) {
            throw new RuntimeException("Not found");
        }
        return channel;
    }

    @PutMapping("/{id}")
    public Channel update(@PathVariable Long id, @RequestBody Channel updated) {
        Channel existing = get(id);
        existing.setName(updated.getName());
        existing.setApiKey(updated.getApiKey());
        existing.setBaseUrl(updated.getBaseUrl());
        existing.setType(updated.getType());
        existing.setAdditionalConfig(updated.getAdditionalConfig());
        return channelService.update(existing);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        channelService.delete(id);
    }
}
