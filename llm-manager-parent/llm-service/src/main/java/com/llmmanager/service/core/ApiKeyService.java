package com.llmmanager.service.core;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.llmmanager.service.core.entity.ApiKey;
import com.llmmanager.service.core.mapper.ApiKeyMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ApiKeyService extends ServiceImpl<ApiKeyMapper, ApiKey> {

    public List<ApiKey> findAll() {
        return list();
    }

    public ApiKey findById(Long id) {
        return getById(id);
    }

    public ApiKey findByToken(String token) {
        QueryWrapper<ApiKey> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("token", token);
        return getOne(queryWrapper);
    }

    public ApiKey create(ApiKey apiKey) {
        apiKey.generateToken();
        save(apiKey);
        return apiKey;
    }

    public ApiKey update(ApiKey apiKey) {
        updateById(apiKey);
        return apiKey;
    }

    public void delete(Long id) {
        removeById(id);
    }

    public ApiKey revoke(Long id) {
        ApiKey key = getById(id);
        if (key != null) {
            key.setActive(0); // 0：禁用
            updateById(key);
        }
        return key;
    }
}