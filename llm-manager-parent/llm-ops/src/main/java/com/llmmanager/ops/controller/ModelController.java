package com.llmmanager.ops.controller;

import com.llmmanager.service.core.entity.LlmModel;
import com.llmmanager.service.core.service.LlmModelService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/models")
public class ModelController {

    @Resource
    private LlmModelService llmModelService;

    @GetMapping
    public List<LlmModel> getAll() {
        return llmModelService.findAll();
    }

    @PostMapping
    public LlmModel create(@RequestBody LlmModel model) {
        // 验证必填字段
        if (model.getChannelId() == null) {
            throw new IllegalArgumentException("模型必须关联一个渠道（channelId 不能为空）");
        }
        if (model.getName() == null || model.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("模型名称不能为空");
        }
        if (model.getModelIdentifier() == null || model.getModelIdentifier().trim().isEmpty()) {
            throw new IllegalArgumentException("模型标识符不能为空");
        }
        return llmModelService.create(model);
    }

    @GetMapping("/{id}")
    public LlmModel get(@PathVariable Long id) {
        LlmModel model = llmModelService.findById(id);
        if (model == null) {
            throw new RuntimeException("Not found");
        }
        return model;
    }

    @PutMapping("/{id}")
    public LlmModel update(@PathVariable Long id, @RequestBody LlmModel updated) {
        // 验证ID存在
        LlmModel existing = get(id);

        // 设置ID后直接更新，MyBatis-Plus 会自动忽略 null 字段
        updated.setId(id);
        llmModelService.update(updated);

        return llmModelService.findById(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        llmModelService.delete(id);
    }
}
