package com.llmmanager.ops.controller;

import com.llmmanager.common.exception.BusinessException;
import com.llmmanager.common.result.Result;
import com.llmmanager.common.result.ResultCode;
import com.llmmanager.service.core.entity.LlmModel;
import com.llmmanager.service.core.service.LlmModelService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/models")
public class ModelController {

    @Resource
    private LlmModelService llmModelService;

    @GetMapping
    public Result<List<LlmModel>> getAll() {
        return Result.success(llmModelService.findAll());
    }

    @PostMapping
    public Result<LlmModel> create(@RequestBody LlmModel model) {
        // 验证必填字段
        if (model.getChannelId() == null) {
            throw BusinessException.paramError("模型必须关联一个渠道（channelId 不能为空）");
        }
        if (!StringUtils.hasText(model.getName())) {
            throw BusinessException.paramError("模型名称不能为空");
        }
        if (!StringUtils.hasText(model.getModelIdentifier())) {
            throw BusinessException.paramError("模型标识符不能为空");
        }
        return Result.success(llmModelService.create(model));
    }

    @GetMapping("/{id}")
    public Result<LlmModel> get(@PathVariable Long id) {
        LlmModel model = llmModelService.findById(id);
        if (model == null) {
            throw new BusinessException(ResultCode.MODEL_NOT_FOUND, "模型不存在: " + id);
        }
        return Result.success(model);
    }

    @PutMapping("/{id}")
    public Result<LlmModel> update(@PathVariable Long id, @RequestBody LlmModel updated) {
        // 验证ID存在
        LlmModel existing = llmModelService.findById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.MODEL_NOT_FOUND, "模型不存在: " + id);
        }

        // 设置ID后直接更新
        updated.setId(id);
        llmModelService.update(updated);

        return Result.success(llmModelService.findById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        llmModelService.delete(id);
        return Result.success();
    }
}
