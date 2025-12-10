package com.llmmanager.service.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.llmmanager.service.core.entity.GraphWorkflow;
import com.llmmanager.service.core.mapper.GraphWorkflowMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * Graph 工作流配置服务
 */
@Service
public class GraphWorkflowConfigService {

    @Resource
    private GraphWorkflowMapper graphWorkflowMapper;

    public List<GraphWorkflow> findAll() {
        return graphWorkflowMapper.selectList(
                new LambdaQueryWrapper<GraphWorkflow>()
                        .orderByAsc(GraphWorkflow::getId)
        );
    }

    public List<GraphWorkflow> findActive() {
        return graphWorkflowMapper.selectList(
                new LambdaQueryWrapper<GraphWorkflow>()
                        .eq(GraphWorkflow::getIsActive, true)
                        .orderByAsc(GraphWorkflow::getId)
        );
    }

    public GraphWorkflow findById(Long id) {
        return graphWorkflowMapper.selectById(id);
    }

    public GraphWorkflow findBySlug(String slug) {
        return graphWorkflowMapper.selectOne(
                new LambdaQueryWrapper<GraphWorkflow>()
                        .eq(GraphWorkflow::getSlug, slug)
        );
    }

    public GraphWorkflow create(GraphWorkflow graphWorkflow) {
        if (graphWorkflow.getIsActive() == null) {
            graphWorkflow.setIsActive(true);
        }
        if (graphWorkflow.getMaxIterations() == null) {
            graphWorkflow.setMaxIterations(3);
        }
        if (graphWorkflow.getQualityThreshold() == null) {
            graphWorkflow.setQualityThreshold(80);
        }
        graphWorkflowMapper.insert(graphWorkflow);
        return graphWorkflow;
    }

    public GraphWorkflow update(GraphWorkflow graphWorkflow) {
        graphWorkflowMapper.updateById(graphWorkflow);
        return graphWorkflowMapper.selectById(graphWorkflow.getId());
    }

    public void delete(Long id) {
        graphWorkflowMapper.deleteById(id);
    }
}
