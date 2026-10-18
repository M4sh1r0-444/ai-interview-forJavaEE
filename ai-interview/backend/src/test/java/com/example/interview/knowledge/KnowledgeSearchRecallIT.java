package com.example.interview.knowledge;

import com.example.interview.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FIX-15 知识检索召回：严格（前 2 高置信词 AND）0 命中时回退宽松（前 4 词 OR），
 * 「相关片段 + 额外不命中词」仍能召回；岗位隔离与无结果不报错不回归。
 */
class KnowledgeSearchRecallIT extends KnowledgeSearchSupport {

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "Admin@123456";

    /** 造一个只含"探针词"的片段进词典（模拟验收探针：词典多出目标片段没有的词）。 */
    private void createProbeChunk(String marker) throws Exception {
        String token = login(ADMIN, ADMIN_PASSWORD);
        mockMvc.perform(post("/api/admin/knowledge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of(
                                "positionId", 1,
                                "title", "召回回退探针片段",
                                "content", "本片段只讨论数据库事务隔离级别与 MVCC 的关系，"
                                        + "用于把探针词加入检索词典，不包含任何缓存击穿类考点内容。",
                                "keywords", marker + ",事务隔离,MVCC",
                                "skillTags", "mysql"))))
                .andExpect(status().isOk());
    }

    @Test
    void relevantChunkWithExtraUnmatchedTermStillRecalled() throws Exception {
        String marker = "验收探针" + System.nanoTime() % 100000;
        createProbeChunk(marker);
        try {
            // 回答关键词：探针词（最长）+ 热点key 进词典前 2 → 严格 AND 0 命中
            //（种子片段无探针词、探针片段无热点key）；回退宽松 OR 后命中缓存三兄弟片段
            String answer = "先说" + marker + "的背景。缓存击穿是热点key过期瞬间大量请求直达数据库，"
                    + "我用互斥锁保证只有一个线程回源重建，并配逻辑过期让旧值先行兜底，"
                    + "再对 TTL 加随机抖动错开过期时间，多级缓存与限流熔断兜底。";
            // 佐证回退路径确实触发：前 2 个高置信词的严格 AND 查询本身 0 命中
            List<String> keywords = keywordExtractor.extract(answer);
            String strict = keywordExtractor.buildBooleanQuery(keywords);
            org.assertj.core.api.Assertions.assertThat(strict).isEqualTo("+" + marker + " +热点key");
            assertThat(search(1L, strict)).isEmpty();

            List<String> chunks = searchService.searchForAnswer(1L, answer);
            assertThat(chunks).isNotEmpty();
            assertThat(String.join("\n", chunks)).contains("缓存");

            // 对照：不含额外词的回答走严格查询同样命中（无回退回归）
            List<String> strictHit = searchService.searchForAnswer(1L,
                    "缓存击穿我用互斥锁回源重建，并配逻辑过期兜底，TTL 加随机抖动。");
            assertThat(strictHit).isNotEmpty();
        } finally {
            // 探针片段带唯一 marker，不影响其它用例断言；测试库残留由 cleanup 脚本统一清理
        }
    }

    @Test
    void positionIsolationAndEmptyResultNotBroken() {
        // 岗位隔离：Java 岗知识不会出现在前端岗检索
        List<String> frontend = searchService.searchForAnswer(2L,
                "缓存击穿我用互斥锁回源重建，并配逻辑过期兜底。");
        assertThat(frontend).isEmpty();
        // 无有效关键词/无结果：返回空列表不抛错（不阻断面试）
        assertThat(searchService.searchForAnswer(1L, "   ")).isEmpty();
        assertThat(searchService.search(1L, "+量子纠缠 +薛定谔")).isEmpty();
    }
}
