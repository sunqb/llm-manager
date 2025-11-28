package com.example.llmmanager.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.llmmanager.entity.LlmModel;
import com.example.llmmanager.mapper.LlmModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LlmModelService extends ServiceImpl<LlmModelMapper, LlmModel> {

    public List<LlmModel> findAll() {
        return list();
    }

    public LlmModel findById(Long id) {
        return getById(id);
    }

    public LlmModel create(LlmModel model) {
        save(model);
        return model;
    }

    public LlmModel update(LlmModel model) {
        updateById(model);
        return model;
    }

    public void delete(Long id) {
        removeById(id);
    }
}