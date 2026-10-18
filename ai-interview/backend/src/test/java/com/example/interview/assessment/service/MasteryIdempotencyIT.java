package com.example.interview.assessment.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.assessment.entity.EvaluationReport;
import com.example.interview.assessment.mapper.EvaluationReportMapper;
import com.example.interview.common.util.JsonUtils;
import com.example.interview.growth.engine.MasteryUpdater;
import com.example.interview.growth.mapper.AbilityRecordMapper;
import com.example.interview.interview.entity.InterviewMessage;
import com.example.interview.interview.entity.InterviewSession;
import com.example.interview.interview.mapper.InterviewMessageMapper;
import com.example.interview.interview.mapper.InterviewSessionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.function.Executable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FIX-12 掌握度真正幂等：V7 uk_ability_source_skill + 逐条捕获 DuplicateKeyException，
 * 并发 applyForSession / 并发报告生成（evaluation_report.session_id UNIQUE）均只落一套记录。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MasteryIdempotencyIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MasteryUpdater masteryUpdater;
    @Autowired
    private AbilityRecordMapper abilityRecordMapper;
    @Autowired
    private InterviewSessionMapper sessionMapper;
    @Autowired
    private InterviewMessageMapper messageMapper;
    @Autowired
    private EvaluationReportMapper reportMapper;
    @Autowired
    private ReportService reportService;

    private static final Map<String, Double> SKILL_SCORES = Map.of(
            "java_basics", 77.5, "spring", 64.0, "mysql", 81.0);

    /** 注册并返回 userId（E2E_ 前缀，结束后随能力记录一起清理）。 */
    private long registerUser(String mark) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(Map.of(
                                "username", "E2E_mastery_" + mark, "password", "Idem@123456",
                                "realName", "幂等用例", "major", "软件工程"))))
                .andExpect(status().isOk()).andReturn();
        Map<?, ?> data = JsonUtils.parse(r.getResponse().getContentAsString(), Map.class);
        Map<?, ?> user = (Map<?, ?>) ((Map<?, ?>) data).get("data");
        return ((Number) user.get("id")).longValue();
    }

    private long countAbility(long sessionId, String skill) {
        Long c = abilityRecordMapper.selectCount(Wrappers.<com.example.interview.growth.entity.AbilityRecord>lambdaQuery()
                .eq(com.example.interview.growth.entity.AbilityRecord::getSourceSessionId, sessionId)
                .eq(com.example.interview.growth.entity.AbilityRecord::getSkillCode, skill));
        return c != null ? c : 0;
    }

    @Test
    void concurrentApplyForSessionWritesExactlyOneRecordPerSkill() throws Exception {
        long userId = registerUser(String.valueOf(System.nanoTime() % 100000));
        long sessionId = 900_000_000L + System.nanoTime() % 10_000_000; // 无 FK 的负例会话号即可
        try {
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CyclicBarrier barrier = new CyclicBarrier(2);
            Callable<Void> task = () -> {
                barrier.await();
                masteryUpdater.applyForSession(userId, 1L, sessionId, SKILL_SCORES);
                return null;
            };
            List<Future<Void>> futures;
            try {
                futures = pool.invokeAll(List.of(task, task));
            } finally {
                pool.shutdown();
            }
            // 双方均不得抛异常（唯一键冲突由败者逐条跳过，不再让事务整体失败）
            for (Future<Void> f : futures) {
                assertDoesNotThrow((Executable) f::get);
            }
            for (String skill : SKILL_SCORES.keySet()) {
                assertThat(countAbility(sessionId, skill))
                        .as("skill=%s 应恰好一条", skill).isEqualTo(1);
            }
            // 顺序重入同样幂等
            masteryUpdater.applyForSession(userId, 1L, sessionId, SKILL_SCORES);
            for (String skill : SKILL_SCORES.keySet()) {
                assertThat(countAbility(sessionId, skill)).isEqualTo(1);
            }
        } finally {
            abilityRecordMapper.delete(Wrappers.<com.example.interview.growth.entity.AbilityRecord>lambdaQuery()
                    .eq(com.example.interview.growth.entity.AbilityRecord::getSourceSessionId, sessionId));
        }
    }

    @Test
    void concurrentReportGenerationKeepsSingleReportAndMasterySet() throws Exception {
        long userId = registerUser(String.valueOf(System.nanoTime() % 100000));
        // 直接造一个 FINISHED 会话 + 一轮问答（questionId=1 种子题带技能标签），避免走异步时序
        InterviewSession s = new InterviewSession();
        s.setUserId(userId);
        s.setPositionId(1L);
        s.setMode("TEXT");
        s.setDifficulty(3);
        s.setPlannedRounds(1);
        s.setPlannedDurationMinutes(15);
        s.setCurrentRound(1);
        s.setStatus("FINISHED");
        s.setStartedAt(LocalDateTime.now().minusMinutes(10));
        s.setFinishedAt(LocalDateTime.now());
        sessionMapper.insert(s);

        InterviewMessage q = new InterviewMessage();
        q.setSessionId(s.getId());
        q.setRoundNo(0);
        q.setSpeaker("AI");
        q.setContentText("主问题：Spring Bean 的生命周期是怎样的？");
        q.setQuestionId(1L);
        messageMapper.insert(q);
        InterviewMessage a = new InterviewMessage();
        a.setSessionId(s.getId());
        a.setRoundNo(0);
        a.setSpeaker("USER");
        a.setInputType("TEXT");
        a.setContentText("Spring Bean 生命周期包括实例化、属性注入、初始化前置处理、"
                + "afterPropertiesSet、自定义 init 方法、销毁回调等阶段，"
                + "容器关闭时按依赖关系逆序销毁 Bean。");
        messageMapper.insert(a);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Void> generate = () -> {
            barrier.await();
            reportService.generate(s.getId());
            return null;
        };
        List<Future<Void>> futures;
        try {
            futures = pool.invokeAll(List.of(generate, generate));
        } finally {
            pool.shutdown();
        }
        try {
            for (Future<Void> f : futures) {
                // 败者走 DuplicateKeyException 分支：加载已有报告+推 REPORT_READY，不抛错
                assertDoesNotThrow((Executable) f::get);
            }
            Long reports = reportMapper.selectCount(Wrappers.<EvaluationReport>lambdaQuery()
                    .eq(EvaluationReport::getSessionId, s.getId()));
            assertThat(reports).as("并发生成应恰好一份报告").isEqualTo(1);
            // 掌握度：questionId=1 的技能标签恰好各一条
            List<com.example.interview.growth.entity.AbilityRecord> records =
                    abilityRecordMapper.selectList(Wrappers.<com.example.interview.growth.entity.AbilityRecord>lambdaQuery()
                            .eq(com.example.interview.growth.entity.AbilityRecord::getSourceSessionId, s.getId()));
            assertThat(records).isNotEmpty();
            assertThat(records.stream().map(r -> r.getSkillCode()).distinct().count())
                    .isEqualTo(records.size());
        } finally {
            abilityRecordMapper.delete(Wrappers.<com.example.interview.growth.entity.AbilityRecord>lambdaQuery()
                    .eq(com.example.interview.growth.entity.AbilityRecord::getSourceSessionId, s.getId()));
            reportMapper.delete(Wrappers.<EvaluationReport>lambdaQuery()
                    .eq(EvaluationReport::getSessionId, s.getId()));
            messageMapper.delete(Wrappers.<InterviewMessage>lambdaQuery()
                    .eq(InterviewMessage::getSessionId, s.getId()));
            sessionMapper.deleteById(s.getId());
        }
    }
}
