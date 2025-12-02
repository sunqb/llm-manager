package com.llmmanager.service.core.service;

import com.llmmanager.service.core.entity.Channel;

import java.util.List;

/**
 * 渠道 Service 接口
 */
public interface ChannelService {

    /**
     * 查询所有渠道
     */
    List<Channel> findAll();

    /**
     * 根据 ID 查询渠道
     */
    Channel findById(Long id);

    /**
     * 创建渠道
     */
    Channel create(Channel channel);

    /**
     * 更新渠道
     */
    Channel update(Channel channel);

    /**
     * 删除渠道
     */
    void delete(Long id);

    /**
     * 根据 ID 获取渠道（MyBatis-Plus 方法）
     */
    Channel getById(Long id);
}
