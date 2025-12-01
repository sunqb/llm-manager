package com.llmmanager.service.core;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.llmmanager.service.core.entity.Channel;
import com.llmmanager.service.core.mapper.ChannelMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChannelService extends ServiceImpl<ChannelMapper, Channel> {

    public List<Channel> findAll() {
        return list();
    }

    public Channel findById(Long id) {
        return getById(id);
    }

    public Channel create(Channel channel) {
        save(channel);
        return channel;
    }

    public Channel update(Channel channel) {
        updateById(channel);
        return channel;
    }

    public void delete(Long id) {
        removeById(id);
    }
}