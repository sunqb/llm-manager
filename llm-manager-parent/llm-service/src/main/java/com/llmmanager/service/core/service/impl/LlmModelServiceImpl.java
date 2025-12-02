package com.llmmanager.service.core.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.llmmanager.service.core.service.LlmModelService;
import com.llmmanager.service.core.entity.LlmModel;
import com.llmmanager.service.core.mapper.LlmModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * LLM 模型 Service 实现
 */
@Service
public class LlmModelServiceImpl extends ServiceImpl<LlmModelMapper, LlmModel> implements LlmModelService {

    @Override
    public List<LlmModel> findAll() {
        return list();
    }

    @Override
    public LlmModel findById(Long id) {
        return getById(id);
    }

    @Override
    public LlmModel getById(Long id) {
        return super.getById(id);
    }

    @Override
    public LlmModel create(LlmModel model) {
        save(model);
        return model;
    }

    @Override
    public LlmModel update(LlmModel model) {
        updateById(model);
        return model;
    }

    @Override
    public void delete(Long id) {
        removeById(id);
    }
}
