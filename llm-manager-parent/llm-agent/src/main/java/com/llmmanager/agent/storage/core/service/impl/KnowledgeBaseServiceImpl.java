package com.llmmanager.agent.storage.core.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.llmmanager.agent.storage.core.entity.KnowledgeBase;
import com.llmmanager.agent.storage.core.mapper.KnowledgeBaseMapper;
import com.llmmanager.agent.storage.core.mapper.KnowledgeDocumentMapper;
import com.llmmanager.agent.storage.core.service.KnowledgeBaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 知识库 Service 实现
 * 仅在 llm.rag.enabled=true 时启用。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "llm.rag.enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase>
        implements KnowledgeBaseService {

    @Resource
    private KnowledgeDocumentMapper documentMapper;

    @Override
    public KnowledgeBase getByKbCode(String kbCode) {
        return baseMapper.selectByKbCode(kbCode);
    }

    @Override
    public List<KnowledgeBase> listAllEnabled() {
        return baseMapper.selectAllEnabled();
    }

    @Override
    public List<KnowledgeBase> listAllPublic() {
        return baseMapper.selectAllPublic();
    }

    @Override
    public KnowledgeBase create(String name, String description, String kbType) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setKbCode(KnowledgeBase.generateKbCode());
        kb.setName(name);
        kb.setDescription(description);
        kb.setKbType(kbType != null ? kbType : "GENERAL");
        kb.setEmbeddingModel("text-embedding-3-small");
        kb.setEmbeddingDimensions(1536);
        kb.setDocumentCount(0);
        kb.setVectorCount(0);
        kb.setIsPublic(false);
        kb.setEnabled(true);
        kb.setSortOrder(0);

        save(kb);
        log.info("[KnowledgeBase] 创建知识库: {} ({})", name, kb.getKbCode());
        return kb;
    }

    @Override
    public void updateStatistics(String kbCode) {
        int documentCount = documentMapper.countByKbCode(kbCode);
        int vectorCount = documentMapper.sumChunkCountByKbCode(kbCode);
        baseMapper.updateCounts(kbCode, documentCount, vectorCount);
        log.debug("[KnowledgeBase] 更新统计: kbCode={}, docs={}, vectors={}", kbCode, documentCount, vectorCount);
    }

    @Override
    public void incrementDocumentCount(String kbCode, int delta) {
        baseMapper.incrementDocumentCount(kbCode, delta);
    }

    @Override
    public void incrementVectorCount(String kbCode, int delta) {
        baseMapper.incrementVectorCount(kbCode, delta);
    }
}
