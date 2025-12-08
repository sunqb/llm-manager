package com.llmmanager.agent.storage.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.llmmanager.agent.storage.core.entity.McpServer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MCP 服务器 Mapper
 */
@Mapper
public interface McpServerMapper extends BaseMapper<McpServer> {

    /**
     * 查询所有启用的服务器（按排序权重）
     */
    @Select("SELECT * FROM a_mcp_servers WHERE enabled = 1 AND is_delete = 0 ORDER BY sort_order ASC")
    List<McpServer> selectEnabledServers();

    /**
     * 根据服务器标识查询
     */
    @Select("SELECT * FROM a_mcp_servers WHERE server_code = #{serverCode} AND is_delete = 0")
    McpServer selectByServerCode(@Param("serverCode") String serverCode);

    /**
     * 根据传输类型查询启用的服务器
     */
    @Select("SELECT * FROM a_mcp_servers WHERE transport_type = #{transportType} AND enabled = 1 AND is_delete = 0 ORDER BY sort_order ASC")
    List<McpServer> selectEnabledByTransportType(@Param("transportType") String transportType);
}

