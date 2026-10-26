package com.example.interview.interview.service;

import com.example.interview.question.entity.Question;
import com.example.interview.resume.dto.ResumeDigest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 简历计划 §8.1 PersonalizedPaperAssemblerTest（R3-2，≥8 用例）：
 * 命中优先、PROJECT 保底 1 题、零命中回退、固定种子复现、题型比例不破坏、digest=null 零变化。
 */
class PersonalizedPaperAssemblerTest {

    private final AtomicLong idGen = new AtomicLong(1);

    /** 简历摘要：Java / Spring Boot / MySQL / Redis（命中 java_basics/spring/mysql/redis_mq 标签） */
    private ResumeDigest backendDigest() {
        ResumeDigest d = new ResumeDigest();
        d.setSkills(List.of(
                skill("Java"), skill("Spring Boot"), skill("MySQL"), skill("Redis")));
        d.setProjects(List.of(project("校园二手交易平台", List.of("Spring Boot", "MySQL", "Redis"))));
        return d;
    }

    private ResumeDigest.Skill skill(String name) {
        ResumeDigest.Skill s = new ResumeDigest.Skill();
        s.setName(name);
        return s;
    }

    private ResumeDigest.Project project(String name, List<String> tech) {
        ResumeDigest.Project p = new ResumeDigest.Project();
        p.setName(name);
        p.setTechStack(tech);
        return p;
    }

    private Question question(String type, int difficulty, List<String> skills) {
        Question q = new Question();
        q.setId(idGen.getAndIncrement());
        q.setPositionId(1L);
        q.setType(type);
        q.setContent("题目-" + type + "-" + idGen.get());
        q.setDifficulty(difficulty);
        q.setSkillTags(skills);
        q.setStatus("ENABLED");
        return q;
    }

    /** 每类型 30 题（难度 1~5 各 6 道）；hitting 为 true 的题带命中简历的技能标签。 */
    private List<Question> fullBank(boolean withHitting) {
        List<Question> bank = new ArrayList<>();
        String[] types = {"TECHNICAL", "PROJECT", "SCENARIO", "BEHAVIORAL"};
        String[] hitSkills = {"spring", "mysql", "redis_mq", "java_basics"};
        String[] missSkills = {"project_design", "communication"};
        for (String type : types) {
            for (int i = 0; i < 30; i++) {
                boolean hit = withHitting && (i % 3 == 0); // 命中题约 1/3
                String[] pool = hit ? hitSkills : missSkills;
                bank.add(question(type, (i % 5) + 1, List.of(pool[i % pool.length])));
            }
        }
        return bank;
    }

    private boolean hits(Question q) {
        List<String> hitTags = List.of("spring", "mysql", "redis_mq", "java_basics");
        return q.getSkillTags().stream().anyMatch(hitTags::contains);
    }

    @Test
    void resumeHitQuestionsPreferredWithinQuota() {
        // TECHNICAL 配额 = round(0.5*6)=3：命中题数量恰为配额时必须全选（优先级 ①②）
        PaperAssembler assembler = new PaperAssembler(new Random(42));
        List<Question> bank = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            bank.add(question("TECHNICAL", 3, List.of("spring", "mysql", "redis_mq").subList(i, i + 1)));
        }
        for (int i = 0; i < 27; i++) {
            bank.add(question("TECHNICAL", 3, List.of("project_design")));
        }
        for (int i = 0; i < 6; i++) {
            bank.add(question("PROJECT", 3, List.of("project_design")));
        }
        for (int i = 0; i < 6; i++) {
            bank.add(question("SCENARIO", 3, List.of("communication")));
        }
        for (int i = 0; i < 3; i++) {
            bank.add(question("BEHAVIORAL", 3, List.of("communication")));
        }
        List<Question> paper = assembler.assemble(bank, 3, 6, backendDigest());
        List<Question> techPicked = paper.stream()
                .filter(q -> "TECHNICAL".equals(q.getType())).toList();
        assertThat(techPicked).hasSize(3);
        assertThat(techPicked).allMatch(this::hits);
    }

    @Test
    void hitPreferredOverMereSkillCoverage() {
        // 优先级①（未覆盖+命中）高于③（未覆盖）：首选题应命中简历且引入新技能
        PaperAssembler assembler = new PaperAssembler(new Random(7));
        List<Question> bank = new ArrayList<>();
        bank.add(question("TECHNICAL", 3, List.of("project_design"))); // 未覆盖但零命中
        bank.add(question("TECHNICAL", 3, List.of("spring")));         // 未覆盖且命中
        bank.add(question("TECHNICAL", 3, List.of("project_design")));
        bank.add(question("TECHNICAL", 3, List.of("communication")));
        List<Question> paper = assembler.assemble(bank, 3, 2, backendDigest());
        assertThat(paper).anyMatch(this::hits);
    }

    @Test
    void projectHitGuaranteedEvenOutsideDifficultyBand() {
        // PROJECT 难度带（目标 1）内全是零命中题，命中题全部难度 5（带外）→ 仍保证 ≥1 道命中题入选
        PaperAssembler assembler = new PaperAssembler(new Random(11));
        List<Question> bank = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            bank.add(question("TECHNICAL", 1, List.of("project_design")));
        }
        for (int i = 0; i < 6; i++) {
            bank.add(question("PROJECT", 1, List.of("project_design"))); // 带内零命中
        }
        for (int i = 0; i < 6; i++) {
            bank.add(question("PROJECT", 5, List.of("spring")));        // 带外但命中
        }
        for (int i = 0; i < 6; i++) {
            bank.add(question("SCENARIO", 1, List.of("communication")));
        }
        for (int i = 0; i < 3; i++) {
            bank.add(question("BEHAVIORAL", 1, List.of("communication")));
        }
        List<Question> paper = assembler.assemble(bank, 1, 5, backendDigest());
        List<Question> projects = paper.stream().filter(q -> "PROJECT".equals(q.getType())).toList();
        assertThat(projects).hasSize(1);
        assertThat(projects).allMatch(this::hits);
    }

    @Test
    void zeroIntersectionFallsBackToBaselineAlgorithm() {
        // 零命中（简历技能与题库无交集）→ 回退既有算法：与三参版本同种子输出一致
        ResumeDigest unrelated = new ResumeDigest();
        unrelated.setSkills(List.of(skill("Figma"), skill("日语")));
        List<Question> bank = fullBank(false);
        List<Question> fallback = new PaperAssembler(new Random(99)).assemble(bank, 3, 6, unrelated);
        List<Question> baseline = new PaperAssembler(new Random(99)).assemble(bank, 3, 6);
        assertThat(fallback).extracting(Question::getId)
                .containsExactlyElementsOf(baseline.stream().map(Question::getId).toList());
    }

    @Test
    void nullDigestIdenticalToBaseline() {
        List<Question> bank = fullBank(true);
        List<Question> withNull = new PaperAssembler(new Random(5)).assemble(bank, 3, 6, null);
        List<Question> baseline = new PaperAssembler(new Random(5)).assemble(bank, 3, 6);
        assertThat(withNull).extracting(Question::getId)
                .containsExactlyElementsOf(baseline.stream().map(Question::getId).toList());
    }

    @Test
    void fixedSeedReproducible() {
        List<Question> bank = fullBank(true);
        List<Question> first = new PaperAssembler(new Random(2026)).assemble(bank, 3, 6, backendDigest());
        List<Question> second = new PaperAssembler(new Random(2026)).assemble(bank, 3, 6, backendDigest());
        assertThat(first).extracting(Question::getId)
                .containsExactlyElementsOf(second.stream().map(Question::getId).toList());
    }

    @Test
    void typeProportionsAndConstraintsPreservedWithDigest() {
        PaperAssembler assembler = new PaperAssembler(new Random(42));
        for (int rounds = 5; rounds <= 8; rounds++) {
            List<Question> paper = assembler.assemble(fullBank(true), 3, rounds, backendDigest());
            assertThat(paper).hasSize(rounds);
            long tech = paper.stream().filter(q -> "TECHNICAL".equals(q.getType())).count();
            long proj = paper.stream().filter(q -> "PROJECT".equals(q.getType())).count();
            long scen = paper.stream().filter(q -> "SCENARIO".equals(q.getType())).count();
            long behav = paper.stream().filter(q -> "BEHAVIORAL".equals(q.getType())).count();
            assertThat(Math.abs(tech - Math.round(0.5 * rounds))).isLessThanOrEqualTo(1);
            assertThat(Math.abs(proj - Math.round(0.2 * rounds))).isLessThanOrEqualTo(1);
            assertThat(Math.abs(scen - Math.round(0.2 * rounds))).isLessThanOrEqualTo(1);
            assertThat(Math.abs(behav - Math.round(0.1 * rounds))).isLessThanOrEqualTo(1);
            assertThat(paper.get(0).getType()).isNotEqualTo("BEHAVIORAL");
            assertThat(paper.stream().map(Question::getId).distinct()).hasSize(paper.size());
        }
    }

    @Test
    void hitRatioHigherThanBaselineOnAverage() {
        // 多种子统计：偏好组卷命中简历技能的题目数 ≥ 通用组卷（算法目标本身）
        int advantage = 0;
        for (int seed = 1; seed <= 10; seed++) {
            List<Question> bank = fullBank(true);
            List<Question> personalized =
                    new PaperAssembler(new Random(seed)).assemble(bank, 3, 5, backendDigest());
            List<Question> baseline = new PaperAssembler(new Random(seed)).assemble(bank, 3, 5);
            long pHits = personalized.stream().filter(this::hits).count();
            long bHits = baseline.stream().filter(this::hits).count();
            if (pHits >= bHits) {
                advantage++;
            }
        }
        assertThat(advantage).as("偏好组卷命中数应普遍不低于通用组卷").isGreaterThanOrEqualTo(9);
    }

    @Test
    void insufficientProjectStockStillFilledByTechnical() {
        // PROJECT 题库为空：配额缺口由 TECHNICAL 补位，个性化不破坏既有补位约束
        PaperAssembler assembler = new PaperAssembler(new Random(3));
        List<Question> bank = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            bank.add(question("TECHNICAL", 3, List.of("spring")));
        }
        for (int i = 0; i < 6; i++) {
            bank.add(question("SCENARIO", 3, List.of("communication")));
        }
        for (int i = 0; i < 3; i++) {
            bank.add(question("BEHAVIORAL", 3, List.of("communication")));
        }
        List<Question> paper = assembler.assemble(bank, 3, 5, backendDigest());
        assertThat(paper).hasSize(5);
        // PROJECT 缺口 1 由 TECHNICAL 补位：tech 配额 3 + 补 1 = 4，SCENARIO 配额 1 照常
        assertThat(paper.stream().filter(q -> "TECHNICAL".equals(q.getType())).count()).isEqualTo(4);
        assertThat(paper.stream().filter(q -> "SCENARIO".equals(q.getType())).count()).isEqualTo(1);
    }
}
