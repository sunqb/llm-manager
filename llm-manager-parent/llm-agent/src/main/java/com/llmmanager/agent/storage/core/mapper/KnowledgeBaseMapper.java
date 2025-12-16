package com.llmmanager.agent.storage.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.llmmanager.agent.storage.core.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 知识库 Mapper
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

    /**
     * 根据知识库 Code 查询
     */
    @Select("SELECT * FROM a_knowledge_bases WHERE kb_code = #{kbCode} AND is_delete = 0")
    KnowledgeBase selectByKbCode(@Param("kbCode") String kbCode);

    /**
     * 查询所有启用的知识库
     */
    @Select("SELECT * FROM a_knowledge_bases WHERE enabled = 1 AND is_delete = 0 ORDER BY sort_order ASC")
    List<KnowledgeBase> selectAllEnabled();

    /**
     * 查询所有公开的知识库
     */
    @Select("SELECT * FROM a_knowledge_bases WHERE is_public = 1 AND enabled = 1 AND is_delete = 0 ORDER BY sort_order ASC")
    List<KnowledgeBase> selectAllPublic();

    /**
     * 增加文档数量
     */
    @Update("UPDATE a_knowledge_bases SET document_count = document_count + #{delta} WHERE kb_code = #{kbCode}")
    int incrementDocumentCount(@Param("kbCode") String kbCode, @Param("delta") int delta);

    /**
     * 增加向量数量
     */
    @Update("UPDATE a_knowledge_bases SET vector_count = vector_count + #{delta} WHERE kb_code = #{kbCode}")
    int incrementVectorCount(@Param("kbCode") String kbCode, @Param("delta") int delta);

    /**
     * 更新文档和向量数量
     */
    @Update("UPDATE a_knowledge_bases SET document_count = #{documentCount}, vector_count = #{vectorCount} WHERE kb_code = #{kbCode}")
    int updateCounts(@Param("kbCode") String kbCode,
                     @Param("documentCount") int documentCount,
                     @Param("vectorCount") int vectorCount);
}
