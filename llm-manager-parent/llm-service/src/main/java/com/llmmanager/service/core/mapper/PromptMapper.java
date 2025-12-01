package com.llmmanager.service.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.llmmanager.service.core.entity.Prompt;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PromptMapper extends BaseMapper<Prompt> {
}