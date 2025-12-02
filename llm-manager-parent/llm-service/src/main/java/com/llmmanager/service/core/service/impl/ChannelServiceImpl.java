package com.llmmanager.service.core.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.llmmanager.service.core.service.ChannelService;
import com.llmmanager.service.core.entity.Channel;
import com.llmmanager.service.core.mapper.ChannelMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 渠道 Service 实现
 */
@Service
public class ChannelServiceImpl extends ServiceImpl<ChannelMapper, Channel> implements ChannelService {

    @Override
    public List<Channel> findAll() {
        return list();
    }

    @Override
    public Channel findById(Long id) {
        return getById(id);
    }

    @Override
    public Channel getById(Long id) {
        return super.getById(id);
    }

    @Override
    public Channel create(Channel channel) {
        save(channel);
        return channel;
    }

    @Override
    public Channel update(Channel channel) {
        updateById(channel);
        return channel;
    }

    @Override
    public void delete(Long id) {
        removeById(id);
    }
}
