package com.example.interview.knowledge.service;

import com.example.interview.knowledge.entity.KnowledgeChunk;
import com.example.interview.knowledge.mapper.KnowledgeChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识检索（计划 §6.2 第 4 步 / §4.3 knowledge）：按关键词 FULLTEXT 检索 Top5 片段，
 * 格式化为 Prompt 可嵌入的短文本；无结果返回空列表，上层自然降级（方案 §14）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSearchService {

    private static final int TOP_K = 5;
    /** Prompt 中每个片段的正文截断长度（控制上下文规模） */
    private static final int EXCERPT_CHARS = 200;

    private final KnowledgeChunkMapper chunkMapper;
    private final KeywordExtractor keywordExtractor;

    /**
     * 从回答文本检索知识片段（FIX-15 两级查询）：提取关键词 → 严格查询（前 2 高置信词
     * AND）→ 0 命中回退宽松查询（前 4 词 OR，按 MATCH 分数排序）→ 仍空返回空列表
     * （检索失败/无结果均不阻断面试）。
     */
    public List<String> searchForAnswer(Long positionId, String answerText) {
        List<String> keywords = keywordExtractor.extract(answerText);
        String strict = keywordExtractor.buildBooleanQuery(keywords);
        if (strict == null) {
            log.debug("知识检索跳过：回答未提取到有效关键词 positionId={}", positionId);
            return List.of();
        }
        List<String> hits = search(positionId, strict);
        if (!hits.isEmpty()) {
            return hits;
        }
        String loose = keywordExtractor.buildLooseQuery(keywords);
        if (loose == null || loose.equals(strict)) {
            return hits;
        }
        log.debug("知识检索严格查询 0 命中，回退宽松查询 positionId={} strict={} loose={}",
                positionId, strict, loose);
        return search(positionId, loose);
    }

    /** 按构造好的 BOOLEAN MODE 查询串检索并格式化。 */
    public List<String> search(Long positionId, String booleanQuery) {
        try {
            List<KnowledgeChunk> chunks =
                    chunkMapper.searchByFulltext(positionId, booleanQuery, TOP_K);
            List<String> result = new ArrayList<>(chunks.size());
            for (KnowledgeChunk c : chunks) {
                result.add("《" + c.getTitle() + "》" + abbreviate(c.getContent()));
            }
            log.debug("知识检索 positionId={} query={} 命中 {}/{} 条",
                    positionId, booleanQuery, chunks.size(), TOP_K);
            return result;
        } catch (Exception e) {
            // 检索故障不阻断面试（方案 §14 知识库无匹配降级）
            log.warn("知识检索失败 positionId={} query={}: {}", positionId, booleanQuery, e.getMessage());
            return List.of();
        }
    }

    private String abbreviate(String content) {
        String c = content != null ? content.replaceAll("\\s+", "") : "";
        return c.length() <= EXCERPT_CHARS ? c : c.substring(0, EXCERPT_CHARS) + "…";
    }
}
