package com.example.llmmanager.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.llmmanager.entity.Prompt;
import com.example.llmmanager.mapper.PromptMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromptService extends ServiceImpl<PromptMapper, Prompt> {

    public List<Prompt> findAll() {
        return list();
    }

    public Prompt findById(Long id) {
        return getById(id);
    }

    public Prompt create(Prompt prompt) {
        save(prompt);
        return prompt;
    }

    public Prompt update(Prompt prompt) {
        updateById(prompt);
        return prompt;
    }

    public void delete(Long id) {
        removeById(id);
    }
}