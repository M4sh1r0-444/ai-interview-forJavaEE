package com.example.interview.interview.service;

import com.example.interview.question.entity.Question;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 计划 §8.1 PaperAssemblerTest：rounds=5~8 比例 50/20/20/10（±1 容差）；
 * 难度均值与目标差 ≤1；行为题不在首位；题库不足时 TECHNICAL 补位。
 */
class PaperAssemblerTest {

    private final PaperAssembler assembler = new PaperAssembler(new Random(42));
    private final AtomicLong idGen = new AtomicLong(1);

    /** 每类型 30 题（难度 1~5 各 6 道，技能交错），等价种子库量级。 */
    private List<Question> fullBank() {
        List<Question> bank = new ArrayList<>();
        String[] types = {"TECHNICAL", "PROJECT", "SCENARIO", "BEHAVIORAL"};
        String[] skills = {"java_basics", "spring", "mysql", "redis_mq", "project_design", "communication", "concurrency_jvm"};
        for (String type : types) {
            for (int i = 0; i < 30; i++) {
                bank.add(question(type, (i % 5) + 1,
                        List.of(skills[i % skills.length])));
            }
        }
        return bank;
    }

    @Test
    void proportionsAndDifficultyForAllRounds() {
        for (int rounds = 5; rounds <= 8; rounds++) {
            List<Question> paper = assembler.assemble(fullBank(), 3, rounds);
            assertThat(paper).hasSize(rounds);

            long tech = count(paper, "TECHNICAL");
            long proj = count(paper, "PROJECT");
            long scen = count(paper, "SCENARIO");
            long behav = count(paper, "BEHAVIORAL");
            // 比例 50/20/20/10，±1 容差（计划 §8.1）
            assertThat(Math.abs(tech - Math.round(0.5 * rounds))).isLessThanOrEqualTo(1);
            assertThat(Math.abs(proj - Math.round(0.2 * rounds))).isLessThanOrEqualTo(1);
            assertThat(Math.abs(scen - Math.round(0.2 * rounds))).isLessThanOrEqualTo(1);
            assertThat(Math.abs(behav - Math.round(0.1 * rounds))).isLessThanOrEqualTo(1);

            // 难度均值与目标差 ≤1（难度带保证：带内题目 |diff-3|≤1）
            double avg = paper.stream().mapToInt(Question::getDifficulty).average().orElse(3);
            assertThat(Math.abs(avg - 3)).isLessThanOrEqualTo(1.0);

            // 行为题不放第一题
            assertThat(paper.get(0).getType()).isNotEqualTo("BEHAVIORAL");

            // 无重复题目
            assertThat(paper.stream().map(Question::getId).distinct()).hasSize(paper.size());
        }
    }

    @Test
    void insufficientStockFilledByTechnical() {
        // 只有 TECHNICAL 题库：全部配额由 TECHNICAL 补位
        List<Question> techOnly = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            techOnly.add(question("TECHNICAL", 3, List.of("java_basics")));
        }
        List<Question> paper = assembler.assemble(techOnly, 3, 5);
        assertThat(paper).hasSize(5);
        assertThat(paper).allMatch(q -> "TECHNICAL".equals(q.getType()));
    }

    @Test
    void partialShortageFilledFromTechnical() {
        // PROJECT 只有 1 道、SCENARIO 0 道、BEHAVIORAL 3 道 → 缺口由 TECHNICAL 补
        List<Question> bank = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            bank.add(question("TECHNICAL", 3, List.of("java_basics")));
        }
        bank.add(question("PROJECT", 3, List.of("project_design")));
        for (int i = 0; i < 3; i++) {
            bank.add(question("BEHAVIORAL", 2, List.of("communication")));
        }
        List<Question> paper = assembler.assemble(bank, 3, 5);
        assertThat(paper).hasSize(5);
        assertThat(count(paper, "SCENARIO")).isZero();
        // tech 配额 3 + scenario/project 缺口补位
        assertThat(count(paper, "TECHNICAL")).isGreaterThanOrEqualTo(3);
    }

    @Test
    void skillCoverageGreedyPrefersUncoveredSkills() {
        // 全库覆盖技能足够时，抽样应尽量覆盖不同技能
        List<Question> paper = assembler.assemble(fullBank(), 3, 8);
        long distinctSkills = paper.stream()
                .flatMap(q -> q.getSkillTags().stream()).distinct().count();
        assertThat(distinctSkills).isGreaterThanOrEqualTo(5);
    }

    private long count(List<Question> paper, String type) {
        return paper.stream().filter(q -> type.equals(q.getType())).count();
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
}
