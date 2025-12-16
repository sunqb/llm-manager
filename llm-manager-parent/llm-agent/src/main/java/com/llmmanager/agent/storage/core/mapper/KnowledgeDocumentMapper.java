package com.llmmanager.agent.storage.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.llmmanager.agent.storage.core.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 知识库文档 Mapper
 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    /**
     * 根据文档 Code 查询
     */
    @Select("SELECT * FROM a_knowledge_documents WHERE doc_code = #{docCode} AND is_delete = 0")
    KnowledgeDocument selectByDocCode(@Param("docCode") String docCode);

    /**
     * 根据知识库 Code 查询所有文档
     */
    @Select("SELECT * FROM a_knowledge_documents WHERE kb_code = #{kbCode} AND is_delete = 0 ORDER BY sort_order ASC")
    List<KnowledgeDocument> selectByKbCode(@Param("kbCode") String kbCode);

    /**
     * 根据知识库 Code 查询所有启用的文档
     */
    @Select("SELECT * FROM a_knowledge_documents WHERE kb_code = #{kbCode} AND enabled = 1 AND is_delete = 0 ORDER BY sort_order ASC")
    List<KnowledgeDocument> selectEnabledByKbCode(@Param("kbCode") String kbCode);

    /**
     * 查询待处理的文档
     */
    @Select("SELECT * FROM a_knowledge_documents WHERE status = 'PENDING' AND is_delete = 0 ORDER BY create_time ASC LIMIT #{limit}")
    List<KnowledgeDocument> selectPendingDocuments(@Param("limit") int limit);

    /**
     * 根据知识库 Code 查询待处理的文档
     */
    @Select("SELECT * FROM a_knowledge_documents WHERE kb_code = #{kbCode} AND status = 'PENDING' AND is_delete = 0 ORDER BY create_time ASC")
    List<KnowledgeDocument> selectPendingByKbCode(@Param("kbCode") String kbCode);

    /**
     * 更新文档状态
     */
    @Update("UPDATE a_knowledge_documents SET status = #{status} WHERE doc_code = #{docCode}")
    int updateStatus(@Param("docCode") String docCode, @Param("status") String status);

    /**
     * 更新文档状态和错误信息
     */
    @Update("UPDATE a_knowledge_documents SET status = #{status}, error_message = #{errorMessage} WHERE doc_code = #{docCode}")
    int updateStatusWithError(@Param("docCode") String docCode,
                              @Param("status") String status,
                              @Param("errorMessage") String errorMessage);

    /**
     * 更新文档处理结果
     */
    @Update("UPDATE a_knowledge_documents SET status = 'COMPLETED', chunk_count = #{chunkCount}, char_count = #{charCount} WHERE doc_code = #{docCode}")
    int updateProcessResult(@Param("docCode") String docCode,
                            @Param("chunkCount") int chunkCount,
                            @Param("charCount") int charCount);

    /**
     * 根据内容哈希查询（用于去重）
     */
    @Select("SELECT * FROM a_knowledge_documents WHERE kb_code = #{kbCode} AND content_hash = #{contentHash} AND is_delete = 0 LIMIT 1")
    KnowledgeDocument selectByContentHash(@Param("kbCode") String kbCode, @Param("contentHash") String contentHash);

    /**
     * 统计知识库文档数量
     */
    @Select("SELECT COUNT(*) FROM a_knowledge_documents WHERE kb_code = #{kbCode} AND is_delete = 0")
    int countByKbCode(@Param("kbCode") String kbCode);

    /**
     * 统计知识库已处理的向量数量
     */
    @Select("SELECT COALESCE(SUM(chunk_count), 0) FROM a_knowledge_documents WHERE kb_code = #{kbCode} AND status = 'COMPLETED' AND is_delete = 0")
    int sumChunkCountByKbCode(@Param("kbCode") String kbCode);
}
