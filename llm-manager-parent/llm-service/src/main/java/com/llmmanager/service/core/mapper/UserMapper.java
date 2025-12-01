package com.llmmanager.service.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.llmmanager.service.core.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}