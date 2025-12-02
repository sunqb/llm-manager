package com.llmmanager.ops.controller;

import com.llmmanager.service.core.entity.Channel;
import com.llmmanager.service.core.service.ChannelService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/channels")
public class ChannelController {

    @Resource
    private ChannelService channelService;

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
        // 验证ID存在
        Channel existing = get(id);

        // 设置ID后直接更新，MyBatis-Plus 会自动忽略 null 字段
        updated.setId(id);
        channelService.update(updated);

        return channelService.findById(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        channelService.delete(id);
    }
}
