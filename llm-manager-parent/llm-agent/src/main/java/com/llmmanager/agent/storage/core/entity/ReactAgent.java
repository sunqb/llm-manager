package com.llmmanager.agent.storage.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.llmmanager.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ReactAgent 配置实体
 * 支持 SINGLE / SEQUENTIAL / SUPERVISOR 三种模式
 *
 * @author LLM Manager
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "p_react_agent", autoResultMap = true)
public class ReactAgent extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Agent 名称
     */
    private String name;

    /**
     * 唯一标识（用于 API 调用）
     */
    private String slug;

    /**
     * 描述
     */
    private String description;

    /**
     * Agent 类型：SINGLE / SEQUENTIAL / SUPERVISOR
     */
    private String agentType;

    /**
     * 关联模型 ID
     */
    private Long modelId;

    /**
     * Agent 配置（JSON 格式）
     * 
     * SINGLE 类型：
     * {
     *   "instruction": "系统指令",
     *   "tools": ["weather", "stock"]
     * }
     * 
     * SEQUENTIAL 类型：
     * {
     *   "agents": [
     *     {"name": "researcher", "instruction": "...", "tools": ["news"]},
     *     {"name": "analyst", "instruction": "...", "tools": ["calculator"]}
     *   ]
     * }
     * 
     * SUPERVISOR 类型：
     * {
     *   "supervisorInstruction": "调度指令",
     *   "workers": [
     *     {"ref": "weather-expert"},
     *     {"name": "custom", "instruction": "...", "tools": ["news"]}
     *   ]
     * }
     */
    @TableField("agent_config")
    private String agentConfig;

    /**
     * 是否启用
     */
    private Boolean isActive;

    /**
     * Agent 类型枚举
     */
    public enum AgentType {
        SINGLE,
        SEQUENTIAL,
        SUPERVISOR
    }

    /**
     * 获取类型枚举
     */
    public AgentType getAgentTypeEnum() {
        if (agentType == null) {
            return AgentType.SINGLE;
        }
        try {
            return AgentType.valueOf(agentType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return AgentType.SINGLE;
        }
    }
}

