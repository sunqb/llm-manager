package com.llmmanager.service.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.llmmanager.service.core.service.ApiKeyService;
import com.llmmanager.service.core.entity.ApiKey;
import com.llmmanager.service.core.mapper.ApiKeyMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * API Key Service 实现
 */
@Service
public class ApiKeyServiceImpl extends ServiceImpl<ApiKeyMapper, ApiKey> implements ApiKeyService {

    @Override
    public List<ApiKey> findAll() {
        return list();
    }

    @Override
    public ApiKey findById(Long id) {
        return getById(id);
    }

    @Override
    public ApiKey findByToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        LambdaQueryWrapper<ApiKey> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ApiKey::getToken, token);
        return getOne(queryWrapper);
    }

    @Override
    public ApiKey create(ApiKey apiKey) {
        apiKey.generateToken();
        save(apiKey);
        return apiKey;
    }

    @Override
    public ApiKey update(ApiKey apiKey) {
        updateById(apiKey);
        return apiKey;
    }

    @Override
    public void delete(Long id) {
        removeById(id);
    }

    @Override
    public ApiKey revoke(Long id) {
        ApiKey key = getById(id);
        if (key != null) {
            key.setActive(0); // 0：禁用
            updateById(key);
        }
        return key;
    }
}
