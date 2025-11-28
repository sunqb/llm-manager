package com.example.llmmanager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.llmmanager.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}