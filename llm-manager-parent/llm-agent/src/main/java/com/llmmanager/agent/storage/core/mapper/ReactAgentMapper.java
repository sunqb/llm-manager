package com.llmmanager.agent.storage.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.llmmanager.agent.storage.core.entity.ReactAgent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * ReactAgent Mapper
 *
 * @author LLM Manager
 */
@Mapper
public interface ReactAgentMapper extends BaseMapper<ReactAgent> {

    /**
     * 根据 slug 查询 Agent
     */
    @Select("SELECT * FROM p_react_agent WHERE slug = #{slug} AND is_delete = 0")
    ReactAgent selectBySlug(@Param("slug") String slug);

    /**
     * 查询所有启用的 Agent
     */
    @Select("SELECT * FROM p_react_agent WHERE is_active = 1 AND is_delete = 0 ORDER BY id ASC")
    List<ReactAgent> selectActiveAgents();

    /**
     * 根据类型查询 Agent
     */
    @Select("SELECT * FROM p_react_agent WHERE agent_type = #{agentType} AND is_delete = 0")
    List<ReactAgent> selectByAgentType(@Param("agentType") String agentType);

    /**
     * 查询所有启用的指定类型 Agent
     */
    @Select("SELECT * FROM p_react_agent WHERE agent_type = #{agentType} AND is_active = 1 AND is_delete = 0")
    List<ReactAgent> selectActiveByAgentType(@Param("agentType") String agentType);
}

