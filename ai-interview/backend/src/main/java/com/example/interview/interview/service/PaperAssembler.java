package com.example.interview.interview.service;

import com.example.interview.question.entity.Question;
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
 */
@Slf4j
public class PaperAssembler {

    private static final String TYPE_TECHNICAL = "TECHNICAL";
    private static final String TYPE_BEHAVIORAL = "BEHAVIORAL";

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
        paper.addAll(pickByType(byType.get(TYPE_TECHNICAL), technical, targetDifficulty, pickedIds, coveredSkills));
        paper.addAll(pickByType(byType.get("PROJECT"), project, targetDifficulty, pickedIds, coveredSkills));
        paper.addAll(pickByType(byType.get("SCENARIO"), scenario, targetDifficulty, pickedIds, coveredSkills));
        paper.addAll(pickByType(byType.get(TYPE_BEHAVIORAL), behavioral, targetDifficulty, pickedIds, coveredSkills));

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

    /** 难度带内随机抽样 + 贪心技能覆盖。 */
    private List<Question> pickByType(List<Question> candidates, int quota, int targetDifficulty,
                                      Set<Long> pickedIds, Set<String> coveredSkills) {
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
        Collections.shuffle(band, random);

        while (picked.size() < quota && !band.isEmpty()) {
            // 优先选引入未覆盖技能的题
            Question choice = band.stream()
                    .filter(q -> q.getSkillTags() != null && q.getSkillTags().stream().anyMatch(s -> !coveredSkills.contains(s)))
                    .findFirst()
                    .orElse(band.get(0));
            band.remove(choice);
            pickedIds.add(choice.getId());
            if (choice.getSkillTags() != null) {
                coveredSkills.addAll(choice.getSkillTags());
            }
            picked.add(choice);
        }
        return picked;
    }

    private int defaultDiff(Question q) {
        return q.getDifficulty() != null ? q.getDifficulty() : 3;
    }
}
