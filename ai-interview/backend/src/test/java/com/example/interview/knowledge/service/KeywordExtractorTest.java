package com.example.interview.knowledge.service;

import com.example.interview.knowledge.entity.KnowledgeChunk;
import com.example.interview.knowledge.mapper.KnowledgeChunkMapper;
import com.example.interview.position.entity.Position;
import com.example.interview.position.mapper.PositionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 计划 §6.5 词典法关键词提取：词典最长匹配、停用词过滤、英文技术词补充、
 * TopN 与 BOOLEAN MODE 查询串构造（词数 > 4 取前 4）。
 */
class KeywordExtractorTest {

    private final PositionMapper positionMapper = Mockito.mock(PositionMapper.class);
    private final KnowledgeChunkMapper chunkMapper = Mockito.mock(KnowledgeChunkMapper.class);
    private final KeywordExtractor extractor = new KeywordExtractor(positionMapper, chunkMapper);

    @BeforeEach
    void setUpDictionary() {
        Position p = new Position();
        p.setId(1L);
        p.setCompetencyModelJson("""
                {"skills":[
                  {"skillCode":"redis_mq","skillName":"Redis与消息队列"},
                  {"skillCode":"mysql","skillName":"MySQL数据库"}]}""");
        when(positionMapper.selectList(any())).thenReturn(List.of(p));

        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setKeywords("缓存穿透,缓存击穿,缓存雪崩,互斥锁,逻辑过期,布隆过滤器,消息积压,索引失效");
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk));
        extractor.refresh();
    }

    @Test
    void extractsDictionaryTermsWithLongestMatch() {
        // 「缓存击穿」命中后不再输出其子串「缓存」（词典中无「缓存」单项，子串抑制逻辑保护）
        List<String> kws = extractor.extract("缓存击穿是指热点key过期，我用互斥锁解决，还会配布隆过滤器防缓存穿透");
        assertThat(kws).contains("缓存击穿", "互斥锁", "布隆过滤器", "缓存穿透");
    }

    @Test
    void suppressesShorterTermCoveredByLongerHit() {
        // 词典含「缓存击穿」；若回答只含「缓存」但不含完整词，不输出碎片
        List<String> kws = extractor.extract("我会用缓存缓存缓存提升性能，配合 MySQL数据库 的事务");
        assertThat(kws).contains("mysql数据库"); // 命中词统一小写归一化
        assertThat(kws).doesNotContain("缓存");
    }

    @Test
    void addsEnglishTechTokensNotInDictionary() {
        List<String> kws = extractor.extract("我用 RedisTemplate 操作 Redis，并用 AOF 持久化");
        assertThat(kws).anyMatch(k -> k.startsWith("redis"));
        assertThat(kws).contains("aof");
    }

    @Test
    void filtersStopwordsAndSingleChars() {
        List<String> kws = extractor.extract("这个问题其实很简单，我们使用缓存穿透方案");
        assertThat(kws).doesNotContain("问题", "我们", "这个", "其实");
        assertThat(kws).noneMatch(k -> k.length() < 2);
    }

    @Test
    void capsAtTopN() {
        String text = String.join("，", "缓存穿透", "缓存击穿", "缓存雪崩", "互斥锁",
                "逻辑过期", "布隆过滤器", "消息积压", "索引失效") + "，还有 redis mysql jvm spring";
        List<String> kws = extractor.extract(text);
        assertThat(kws).hasSizeLessThanOrEqualTo(10);
    }

    @Test
    void emptyAndBlankTextReturnEmpty() {
        assertThat(extractor.extract(null)).isEmpty();
        assertThat(extractor.extract("   ")).isEmpty();
    }

    @Test
    void booleanQueryJoinsWithPlusAndCapsAt4() {
        assertThat(extractor.buildBooleanQuery(List.of("缓存", "redis")))
                .isEqualTo("+缓存 +redis");
        // 超过 4 词只取前 4，避免 BOOLEAN MODE 全 AND 查空
        assertThat(extractor.buildBooleanQuery(List.of("缓存", "redis", "索引", "事务", "锁", "队列")))
                .isEqualTo("+缓存 +redis +索引 +事务");
        assertThat(extractor.buildBooleanQuery(null)).isNull();
        assertThat(extractor.buildBooleanQuery(List.of("的"))).isNull(); // 全被过滤
    }

    @Test
    void booleanQueryStripsOperatorCharacters() {
        // 操作符字符剔除；剔除后不足 2 字的词（如 "(c)"→"c"）被丢弃
        assertThat(extractor.buildBooleanQuery(List.of("a-b", "(cd)")))
                .isEqualTo("+ab +cd");
        assertThat(extractor.buildBooleanQuery(List.of("(c)"))).isNull();
    }
}
