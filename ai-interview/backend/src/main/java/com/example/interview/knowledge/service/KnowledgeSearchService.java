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
     * 从回答文本检索知识片段：提取关键词 → BOOLEAN MODE 查询 → Top5 格式化。
     * 返回空列表表示无匹配（检索失败/无结果均不阻断面试）。
     */
    public List<String> searchForAnswer(Long positionId, String answerText) {
        List<String> keywords = keywordExtractor.extract(answerText);
        String query = keywordExtractor.buildBooleanQuery(keywords);
        if (query == null) {
            log.debug("知识检索跳过：回答未提取到有效关键词 positionId={}", positionId);
            return List.of();
        }
        return search(positionId, query);
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
