package com.llmmanager.agent.storage.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.llmmanager.agent.storage.core.entity.GraphWorkflow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Graph 工作流 Mapper
 */
@Mapper
public interface GraphWorkflowMapper extends BaseMapper<GraphWorkflow> {

    /**
     * 根据 slug 查询工作流
     */
    @Select("SELECT * FROM p_graph_workflows WHERE slug = #{slug} AND is_delete = 0")
    GraphWorkflow selectBySlug(@Param("slug") String slug);

    /**
     * 查询所有启用的工作流
     */
    @Select("SELECT * FROM p_graph_workflows WHERE is_active = 1 AND is_delete = 0 ORDER BY id ASC")
    List<GraphWorkflow> selectActiveWorkflows();

    /**
     * 根据类型查询工作流
     */
    @Select("SELECT * FROM p_graph_workflows WHERE workflow_type = #{workflowType} AND is_delete = 0")
    List<GraphWorkflow> selectByWorkflowType(@Param("workflowType") String workflowType);
}
