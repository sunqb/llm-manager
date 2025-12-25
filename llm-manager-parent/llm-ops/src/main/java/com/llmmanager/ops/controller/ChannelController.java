package com.llmmanager.ops.controller;

import com.llmmanager.common.exception.BusinessException;
import com.llmmanager.common.result.Result;
import com.llmmanager.common.result.ResultCode;
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
    public Result<List<Channel>> getAll() {
        return Result.success(channelService.findAll());
    }

    @PostMapping
    public Result<Channel> create(@RequestBody Channel channel) {
        return Result.success(channelService.create(channel));
    }

    @GetMapping("/{id}")
    public Result<Channel> get(@PathVariable Long id) {
        Channel channel = channelService.findById(id);
        if (channel == null) {
            throw new BusinessException(ResultCode.CHANNEL_NOT_FOUND, "渠道不存在: " + id);
        }
        return Result.success(channel);
    }

    @PutMapping("/{id}")
    public Result<Channel> update(@PathVariable Long id, @RequestBody Channel updated) {
        // 验证ID存在
        Channel existing = channelService.findById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.CHANNEL_NOT_FOUND, "渠道不存在: " + id);
        }

        // 设置ID后直接更新
        updated.setId(id);
        channelService.update(updated);

        return Result.success(channelService.findById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        channelService.delete(id);
        return Result.success();
    }
}
