package com.llmmanager.service.core.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.llmmanager.service.core.service.PromptService;
import com.llmmanager.service.core.entity.Prompt;
import com.llmmanager.service.core.mapper.PromptMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Prompt Service 实现
 */
@Service
public class PromptServiceImpl extends ServiceImpl<PromptMapper, Prompt> implements PromptService {

    @Override
    public List<Prompt> findAll() {
        return list();
    }

    @Override
    public Prompt findById(Long id) {
        return getById(id);
    }

    @Override
    public Prompt create(Prompt prompt) {
        save(prompt);
        return prompt;
    }

    @Override
    public Prompt update(Prompt prompt) {
        updateById(prompt);
        return prompt;
    }

    @Override
    public void delete(Long id) {
        removeById(id);
    }
}
