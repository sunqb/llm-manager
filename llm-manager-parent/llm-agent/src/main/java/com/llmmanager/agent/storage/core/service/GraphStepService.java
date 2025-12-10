package com.llmmanager.agent.storage.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.llmmanager.agent.storage.core.entity.GraphStep;
import com.llmmanager.agent.storage.core.mapper.GraphStepMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * Graph 工作流步骤服务
 */
@Service
public class GraphStepService {

    @Resource
    private GraphStepMapper stepMapper;

    public List<GraphStep> findByTaskCode(String taskCode) {
        return stepMapper.selectList(
                new LambdaQueryWrapper<GraphStep>()
                        .eq(GraphStep::getTaskCode, taskCode)
                        .orderByAsc(GraphStep::getIterationRound)
                        .orderByAsc(GraphStep::getStepIndex)
        );
    }

    public GraphStep save(GraphStep step) {
        stepMapper.insert(step);
        return step;
    }

    public void update(GraphStep step) {
        stepMapper.updateById(step);
    }
}
