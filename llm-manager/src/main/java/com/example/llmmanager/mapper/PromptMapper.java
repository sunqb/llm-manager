package com.example.llmmanager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.llmmanager.entity.Prompt;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PromptMapper extends BaseMapper<Prompt> {
}