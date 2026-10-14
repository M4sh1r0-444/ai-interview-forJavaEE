package com.example.interview.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.interview.knowledge.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 知识片段 Mapper（计划 §3.3）：中文全文检索走 ngram 联合索引
 * ft_chunk_text(content, keywords)，BOOLEAN MODE 词间 + 连接。
 * 全参数绑定（#{query}），无字符串拼接（计划 §7-8 SQL 注入防护）。
 */
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    /**
     * 按岗位检索最相关的知识片段 TopN。
     *
     * @param positionId 岗位
     * @param query      BOOLEAN MODE 查询串（如 "+缓存 +redis"，词长 ≥2 汉字，风险 R3）
     * @param limit      返回条数（检索 Top5）
     */
    @Select("""
            SELECT id, position_id, title, content, keywords, skill_tags, source_type, status, created_at,
                   MATCH(content, keywords) AGAINST(#{query} IN BOOLEAN MODE) AS score
            FROM knowledge_chunk
            WHERE status = 'ENABLED'
              AND position_id = #{positionId}
              AND MATCH(content, keywords) AGAINST(#{query} IN BOOLEAN MODE) > 0
            ORDER BY score DESC
            LIMIT #{limit}
            """)
    List<KnowledgeChunk> searchByFulltext(@Param("positionId") Long positionId,
                                          @Param("query") String query,
                                          @Param("limit") int limit);

    /** 重建 FULLTEXT 索引（计划 §5.5-26：简化为 OPTIMIZE TABLE 重建表与索引）。 */
    @Update("OPTIMIZE TABLE knowledge_chunk")
    int rebuildFulltextIndex();
}
