package com.llmmanager.agent.storage.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.llmmanager.agent.storage.core.entity.GraphWorkflow;
import com.llmmanager.agent.storage.core.mapper.GraphWorkflowMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * Graph 工作流配置服务
 */
@Service
public class GraphWorkflowService {

    @Resource
    private GraphWorkflowMapper workflowMapper;

    /**
     * 查询所有工作流
     */
    public List<GraphWorkflow> findAll() {
        return workflowMapper.selectList(
                new LambdaQueryWrapper<GraphWorkflow>()
                        .eq(GraphWorkflow::getIsDelete, 0)
                        .orderByAsc(GraphWorkflow::getId)
        );
    }

    /**
     * 根据 ID 查询
     */
    public GraphWorkflow findById(Long id) {
        return workflowMapper.selectById(id);
    }

    /**
     * 根据 slug 查询
     */
    public GraphWorkflow findBySlug(String slug) {
        return workflowMapper.selectBySlug(slug);
    }

    /**
     * 根据 slug 获取工作流（不存在则抛出异常）
     */
    public GraphWorkflow getWorkflowBySlug(String slug) {
        GraphWorkflow workflow = workflowMapper.selectBySlug(slug);
        if (workflow == null) {
            throw new IllegalArgumentException("工作流不存在: " + slug);
        }
        return workflow;
    }

    /**
     * 查询所有启用的工作流
     */
    public List<GraphWorkflow> findActiveWorkflows() {
        return workflowMapper.selectActiveWorkflows();
    }

    /**
     * 根据工作流类型查询
     */
    public List<GraphWorkflow> findByWorkflowType(String workflowType) {
        return workflowMapper.selectByWorkflowType(workflowType);
    }

    /**
     * 保存工作流
     */
    public GraphWorkflow save(GraphWorkflow workflow) {
        workflowMapper.insert(workflow);
        return workflow;
    }

    /**
     * 更新工作流
     */
    public void update(GraphWorkflow workflow) {
        workflowMapper.updateById(workflow);
    }

    /**
     * 删除工作流（软删除）
     */
    public void delete(Long id) {
        GraphWorkflow workflow = workflowMapper.selectById(id);
        if (workflow != null) {
            workflow.setIsDelete(1);
            workflowMapper.updateById(workflow);
        }
    }
}
