package com.example.interview.resume.service;

import com.example.interview.resume.dto.ResumeDigest;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简历技能 ↔ 题库技能标签/岗位技能名 匹配器（简历计划 §6.5/§6.8，R3）。
 * 无分词器下的可解释近似（口径对齐 MockAiProvider.skillTagHit / anySkillCovers）：
 * 归一化互为包含（下划线词干，"redis_mq"→"redis" 命中 "Redis"）或英文主词前缀
 * （"JVM" 命中 "concurrency_jvm"，"Spring Boot" 命中 "Spring生态"）。纯静态工具，
 * 组卷偏好（PaperAssembler）、报告对比（ReportService）、focus-skills 三处共用。
 */
public final class ResumeSkillMatcher {

    private ResumeSkillMatcher() {
    }

    /** 首段英文/数字主词（≥3 位才算有效词干，防 go/js 误配） */
    private static final Pattern ASCII_HEAD = Pattern.compile("[a-z0-9+#]{3,}");

    /** 匹配用归一化：去空白/下划线/连字符、小写（口径同 ResumeProfileParser.normalizeForMatch + 词干分隔） */
    public static String normalize(String s) {
        return s == null ? "" : s.replaceAll("[\\s_\\-]+", "").toLowerCase(Locale.ROOT);
    }

    public static String asciiHead(String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return "";
        }
        Matcher m = ASCII_HEAD.matcher(normalized);
        return m.find() ? m.group() : "";
    }

    /** digest 技能名 → 归一化集合（保序去空；无技能返回空集） */
    public static Set<String> normalizedSkillNames(ResumeDigest digest) {
        Set<String> names = new LinkedHashSet<>();
        if (digest == null || digest.getSkills() == null) {
            return names;
        }
        for (ResumeDigest.Skill s : digest.getSkills()) {
            if (s != null && s.getName() != null && !s.getName().isBlank()) {
                String n = normalize(s.getName());
                if (!n.isEmpty()) {
                    names.add(n);
                }
            }
        }
        return names;
    }

    /** 题库技能标签（如 redis_mq）是否命中某个简历技能（已归一化，如 redis） */
    public static boolean tagMatches(String tag, String normalizedResumeSkill) {
        if (tag == null || normalizedResumeSkill == null || normalizedResumeSkill.isEmpty()) {
            return false;
        }
        String normTag = normalize(tag);
        String stem = tag.indexOf('_') >= 0 ? normalize(tag.substring(0, tag.indexOf('_'))) : normTag;
        if (stem.length() >= 2 && normalizedResumeSkill.contains(stem)) {
            return true;
        }
        String head = asciiHead(normalizedResumeSkill);
        return head.length() >= 3 && normTag.contains(head);
    }

    /** 题库技能标签组是否与简历技能集合有交集（组卷带内优先级判定） */
    public static boolean tagHitsAny(Collection<String> skillTags, Collection<String> normalizedResumeSkills) {
        if (skillTags == null || normalizedResumeSkills == null || normalizedResumeSkills.isEmpty()) {
            return false;
        }
        for (String tag : skillTags) {
            for (String rs : normalizedResumeSkills) {
                if (tagMatches(tag, rs)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 技能名 ↔ 简历技能名匹配（focus-skills 与报告对比用，两侧均接受原始名）：
     * 归一化互为包含，或英文主词前缀互配（"Java基础与面向对象" ↔ "Java"、"Spring生态" ↔ "Spring Boot"）。
     */
    public static boolean nameMatches(String positionSkillName, String resumeSkillName) {
        String np = normalize(positionSkillName);
        String nr = normalize(resumeSkillName);
        if (np.length() < 2 || nr.isEmpty()) {
            return false;
        }
        if (np.contains(nr) || nr.contains(np)) {
            return true;
        }
        String hp = asciiHead(np);
        String hr = asciiHead(nr);
        return hp.length() >= 3 && hr.length() >= 3 && (hp.startsWith(hr) || hr.startsWith(hp));
    }
}
