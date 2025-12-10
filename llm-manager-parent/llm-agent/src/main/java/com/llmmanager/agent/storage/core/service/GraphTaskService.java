package com.llmmanager.agent.storage.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.llmmanager.agent.storage.core.entity.GraphTask;
import com.llmmanager.agent.storage.core.mapper.GraphTaskMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * Graph 工作流任务服务
 */
@Service
public class GraphTaskService {

    @Resource
    private GraphTaskMapper taskMapper;

    public GraphTask findByTaskCode(String taskCode) {
        return taskMapper.selectOne(
                new LambdaQueryWrapper<GraphTask>()
                        .eq(GraphTask::getTaskCode, taskCode)
        );
    }

    public List<GraphTask> findByModelId(Long modelId) {
        return taskMapper.selectList(
                new LambdaQueryWrapper<GraphTask>()
                        .eq(GraphTask::getModelId, modelId)
                        .orderByDesc(GraphTask::getCreateTime)
        );
    }

    public List<GraphTask> findByGraphWorkflowId(Long graphWorkflowId) {
        return taskMapper.selectList(
                new LambdaQueryWrapper<GraphTask>()
                        .eq(GraphTask::getGraphWorkflowId, graphWorkflowId)
                        .orderByDesc(GraphTask::getCreateTime)
        );
    }

    public GraphTask save(GraphTask task) {
        taskMapper.insert(task);
        return task;
    }

    public void update(GraphTask task) {
        taskMapper.updateById(task);
    }
}
