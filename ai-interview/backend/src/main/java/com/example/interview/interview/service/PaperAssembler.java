package com.example.interview.interview.service;

import com.example.interview.question.entity.Question;
import com.example.interview.resume.dto.ResumeDigest;
import com.example.interview.resume.service.ResumeSkillMatcher;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 组卷算法（计划 §6.3），纯逻辑无依赖，便于单测注入固定随机种子。
 *
 * <pre>
 * 1. 比例分配：technical=round(0.5N) project=round(0.2N) scenario=round(0.2N) behavioral=N-前三者
 * 2. 每类型候选按 |difficulty-目标| 升序取前 3 倍配额作为难度带
 * 3. 带内随机抽样，贪心优先选含未覆盖技能的题
 * 4. 顺序随机打乱，行为题不放第一题
 * 5. 某类型题库存量不足 → 用 TECHNICAL 补足（WARN，不中断面试）
 * </pre>
 *
 * <p>R3-2 简历偏好重载（简历计划 §6.5）：题型配额/难度带/行为题约束全部复用，仅替换
 * "带内挑选"的比较器——命中简历技能（skill_tags ∩ digest.skills）优先；PROJECT 类型
 * 存在命中题时保证 ≥1 道入选；零命中回退既有算法（INFO 一次）。评分基准（question_id
 * 与 key_points）不受影响。</p>
 */
@Slf4j
public class PaperAssembler {

    private static final String TYPE_TECHNICAL = "TECHNICAL";
    private static final String TYPE_BEHAVIORAL = "BEHAVIORAL";
    private static final String TYPE_PROJECT = "PROJECT";

    private final Random random;

    public PaperAssembler() {
        this(new Random());
    }

    /** 测试可注入固定种子保证可复现。 */
    public PaperAssembler(Random random) {
        this.random = random;
    }

    /**
     * @param enabledQuestions 该岗位全部 ENABLED 题目
     * @param targetDifficulty 目标难度 1~5
     * @param rounds           主问题数 5~8
     * @return 组好的主问题序列（可能因题库不足少于 rounds，由调用方决定是否调整 plannedRounds）
     */
    public List<Question> assemble(Collection<Question> enabledQuestions, int targetDifficulty, int rounds) {
        return assembleInternal(enabledQuestions, targetDifficulty, rounds, null);
    }

    /**
     * R3-2 简历偏好组卷：digest 为 null 委托既有三参方法（行为零变化）；
     * 零命中（题库无任何题目命中简历技能）同样回退既有算法并记 INFO。
     */
    public List<Question> assemble(Collection<Question> enabledQuestions, int targetDifficulty,
                                   int rounds, ResumeDigest digest) {
        if (digest == null) {
            return assemble(enabledQuestions, targetDifficulty, rounds);
        }
        Set<String> resumeSkills = ResumeSkillMatcher.normalizedSkillNames(digest);
        boolean anyHit = enabledQuestions.stream()
                .anyMatch(q -> ResumeSkillMatcher.tagHitsAny(q.getSkillTags(), resumeSkills));
        if (!anyHit) {
            log.info("简历技能与岗位题库零交集，回退通用组卷算法 skills={}", resumeSkills.size());
            return assemble(enabledQuestions, targetDifficulty, rounds);
        }
        return assembleInternal(enabledQuestions, targetDifficulty, rounds, resumeSkills);
    }

    private List<Question> assembleInternal(Collection<Question> enabledQuestions, int targetDifficulty,
                                            int rounds, Set<String> resumeSkills) {
        Map<String, List<Question>> byType = new LinkedHashMap<>();
        for (Question q : enabledQuestions) {
            byType.computeIfAbsent(q.getType(), k -> new ArrayList<>()).add(q);
        }

        int technical = Math.round(0.5f * rounds);
        int project = Math.round(0.2f * rounds);
        int scenario = Math.round(0.2f * rounds);
        int behavioral = Math.max(0, rounds - technical - project - scenario);

        Set<Long> pickedIds = new HashSet<>();
        Set<String> coveredSkills = new HashSet<>();
        List<Question> paper = new ArrayList<>();
        paper.addAll(pickByType(byType.get(TYPE_TECHNICAL), technical, targetDifficulty, pickedIds, coveredSkills, resumeSkills, false));
        paper.addAll(pickByType(byType.get(TYPE_PROJECT), project, targetDifficulty, pickedIds, coveredSkills, resumeSkills, true));
        paper.addAll(pickByType(byType.get("SCENARIO"), scenario, targetDifficulty, pickedIds, coveredSkills, resumeSkills, false));
        paper.addAll(pickByType(byType.get(TYPE_BEHAVIORAL), behavioral, targetDifficulty, pickedIds, coveredSkills, resumeSkills, false));

        // 某类型存量不足 → TECHNICAL 补足（计划 §6.3 约束）
        if (paper.size() < rounds) {
            int deficit = rounds - paper.size();
            List<Question> spare = new ArrayList<>(byType.getOrDefault(TYPE_TECHNICAL, List.of()));
            spare.removeIf(q -> pickedIds.contains(q.getId()));
            Collections.shuffle(spare, random);
            List<Question> fill = spare.subList(0, Math.min(deficit, spare.size()));
            paper.addAll(fill);
            log.warn("组卷类型配额不足，用 TECHNICAL 补位 position 题库={} 缺口={} 补了={}",
                    byType.keySet(), deficit, fill.size());
        }

        Collections.shuffle(paper, random);
        // 行为题不放第一题
        if (!paper.isEmpty() && TYPE_BEHAVIORAL.equals(paper.get(0).getType())) {
            for (int i = 1; i < paper.size(); i++) {
                if (!TYPE_BEHAVIORAL.equals(paper.get(i).getType())) {
                    Collections.swap(paper, 0, i);
                    break;
                }
            }
        }
        return paper;
    }

    /** 难度带内随机抽样 + 贪心技能覆盖；resumeSkills 非空时按 §6.5 带内优先级挑选。 */
    private List<Question> pickByType(List<Question> candidates, int quota, int targetDifficulty,
                                      Set<Long> pickedIds, Set<String> coveredSkills, Set<String> resumeSkills,
                                      boolean guaranteeProjectHit) {
        List<Question> picked = new ArrayList<>();
        if (quota <= 0 || candidates == null || candidates.isEmpty()) {
            return picked;
        }
        List<Question> pool = new ArrayList<>(candidates);
        pool.removeIf(q -> pickedIds.contains(q.getId()));
        pool.sort(Comparator.comparingInt(q -> Math.abs(defaultDiff(q) - targetDifficulty)));
        // 难度带：|难度差|最小的前 3 倍配额
        int bandSize = Math.min(3 * quota, pool.size());
        List<Question> band = new ArrayList<>(pool.subList(0, bandSize));
        // R3-2：PROJECT 存在命中题时保证 ≥1 道入选——命中题可能落在难度带外，换入带尾
        if (guaranteeProjectHit && resumeSkills != null && !resumeSkills.isEmpty()) {
            ensureHitInBand(band, pool, pickedIds, resumeSkills);
        }
        Collections.shuffle(band, random);

        while (picked.size() < quota && !band.isEmpty()) {
            Question choice = pickFromBand(band, coveredSkills, resumeSkills);
            band.remove(choice);
            pickedIds.add(choice.getId());
            if (choice.getSkillTags() != null) {
                coveredSkills.addAll(choice.getSkillTags());
            }
            picked.add(choice);
        }
        return picked;
    }

    /**
     * 带内优先级（简历计划 §6.5，高→低）：
     * ① 覆盖未覆盖技能 且 命中简历技能；② 命中简历技能；③ 覆盖未覆盖技能（现状）；④ 随机（现状）。
     */
    private Question pickFromBand(List<Question> band, Set<String> coveredSkills, Set<String> resumeSkills) {
        if (resumeSkills == null || resumeSkills.isEmpty()) {
            return band.stream()
                    .filter(q -> introducesNewSkill(q, coveredSkills))
                    .findFirst()
                    .orElse(band.get(0));
        }
        return band.stream()
                .filter(q -> introducesNewSkill(q, coveredSkills)
                        && ResumeSkillMatcher.tagHitsAny(q.getSkillTags(), resumeSkills))
                .findFirst()
                .or(() -> band.stream()
                        .filter(q -> ResumeSkillMatcher.tagHitsAny(q.getSkillTags(), resumeSkills))
                        .findFirst())
                .or(() -> band.stream()
                        .filter(q -> introducesNewSkill(q, coveredSkills))
                        .findFirst())
                .orElse(band.get(0));
    }

    /** 命中题全在难度带外时，把首道命中题换入带尾（保证 PROJECT ≥1 命中题入选）。 */
    private void ensureHitInBand(List<Question> band, List<Question> pool,
                                 Set<Long> pickedIds, Set<String> resumeSkills) {
        if (band.stream().anyMatch(q -> ResumeSkillMatcher.tagHitsAny(q.getSkillTags(), resumeSkills))) {
            return;
        }
        pool.stream()
                .filter(q -> !pickedIds.contains(q.getId()) && !band.contains(q)
                        && ResumeSkillMatcher.tagHitsAny(q.getSkillTags(), resumeSkills))
                .findFirst()
                .ifPresent(q -> {
                    if (band.isEmpty()) {
                        band.add(q);
                    } else {
                        band.set(band.size() - 1, q);
                    }
                });
    }

    private boolean introducesNewSkill(Question q, Set<String> coveredSkills) {
        return q.getSkillTags() != null
                && q.getSkillTags().stream().anyMatch(s -> !coveredSkills.contains(s));
    }

    private int defaultDiff(Question q) {
        return q.getDifficulty() != null ? q.getDifficulty() : 3;
    }
}
