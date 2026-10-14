package com.example.interview.knowledge.service;

import com.example.interview.common.exception.BizException;
import com.example.interview.common.result.ResultCode;
import com.example.interview.knowledge.dto.KnowledgeUpsertRequest;
import com.example.interview.knowledge.entity.KnowledgeChunk;
import com.example.interview.knowledge.mapper.KnowledgeChunkMapper;
import com.example.interview.position.mapper.PositionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库管理服务（计划 §5.5-25/26）：新增片段（落库即入全文索引，立即可检索）与
 * FULLTEXT 索引重建；新增后刷新关键词词典（计划 §6.5-1）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeAdminService {

    private final KnowledgeChunkMapper chunkMapper;
    private final PositionMapper positionMapper;
    private final KeywordExtractor keywordExtractor;

    /** 新增知识片段（sourceType=MANUAL），刷新词典使其立即可被检索。 */
    @Transactional
    public KnowledgeChunk create(KnowledgeUpsertRequest req) {
        if (positionMapper.selectById(req.getPositionId()) == null) {
            throw new BizException(ResultCode.PARAM_OUT_OF_RANGE, "岗位不存在：" + req.getPositionId());
        }
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setPositionId(req.getPositionId());
        chunk.setTitle(req.getTitle());
        chunk.setContent(req.getContent());
        chunk.setKeywords(req.getKeywords());
        chunk.setSkillTags(req.getSkillTags());
        chunk.setSourceType("MANUAL");
        chunk.setStatus("ENABLED");
        chunkMapper.insert(chunk);
        keywordExtractor.refresh();
        log.info("新增知识片段 id={} position={} title={}", chunk.getId(), req.getPositionId(), req.getTitle());
        return chunk;
    }

    /** 重建全文索引（OPTIMIZE TABLE）。 */
    public void rebuild() {
        chunkMapper.rebuildFulltextIndex();
        log.info("知识库全文索引已重建（OPTIMIZE TABLE knowledge_chunk）");
    }
}
