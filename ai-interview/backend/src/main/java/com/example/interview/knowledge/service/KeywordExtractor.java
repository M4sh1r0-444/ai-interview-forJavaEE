package com.example.interview.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.interview.common.util.JsonUtils;
import com.example.interview.knowledge.entity.KnowledgeChunk;
import com.example.interview.knowledge.mapper.KnowledgeChunkMapper;
import com.example.interview.position.dto.CompetencyModel;
import com.example.interview.position.entity.Position;
import com.example.interview.position.mapper.PositionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 关键词提取（计划 §6.5 词典法）：词典 = 岗位能力模型技能名/编码 + 知识库 keywords 聚合去重，
 * 启动后首次使用惰性加载进内存，管理端新增知识片段时 {@link #refresh()} 重建。
 * 匹配采用最长匹配去重 + 英文技术词正则启发，过滤停用词且仅保留词长 ≥2（配合 ngram token=2，风险 R3）。
 * 产出 TopN(10)：写入 interview_message.keywords_json，并构造 FULLTEXT BOOLEAN MODE 查询串。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeywordExtractor {

    private static final Pattern TECH_TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9+.#]{1,}");
    private static final int TOP_N = 10;
    /** 检索查询词上限：词数 > 4 时取前 4，避免 BOOLEAN MODE 全 AND 查空（计划 §6.5-4） */
    private static final int QUERY_TERM_LIMIT = 4;
    /** BOOLEAN MODE 操作符字符，进入查询串前剔除 */
    private static final Pattern BOOLEAN_MODE_CHARS = Pattern.compile("[-+<>()~*\"@]");

    private static final Set<String> STOPWORDS = Set.of(
            "这个", "那个", "什么", "怎么", "可以", "我们", "他们", "因为", "所以", "但是",
            "然后", "就是", "一个", "一些", "如果", "还有", "没有", "自己", "通过", "进行",
            "使用", "实现", "考虑", "问题", "情况", "时候", "方面", "不同", "以及", "如何",
            "the", "and", "for", "with", "that", "this", "from", "are", "was");

    private final PositionMapper positionMapper;
    private final KnowledgeChunkMapper chunkMapper;

    /** 词典快照（volatile 引用，重建时整体替换） */
    private volatile List<String> dictionary = null;

    /** 管理端新增知识片段后调用；也可在词典为空时自动触发懒加载。 */
    public synchronized void refresh() {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        // 字符串列名 QueryWrapper：不依赖 lambda 缓存，脱离 Spring 容器亦可运行（单测友好）
        for (Position p : positionMapper.selectList(new QueryWrapper<>())) {
            collectPositionTerms(p, terms);
        }
        for (KnowledgeChunk chunk : chunkMapper.selectList(
                new QueryWrapper<KnowledgeChunk>().eq("status", "ENABLED").select("keywords"))) {
            for (String kw : splitCsv(chunk.getKeywords())) {
                if (kw.length() >= 2 && !STOPWORDS.contains(kw)) {
                    terms.add(kw);
                }
            }
        }
        this.dictionary = List.copyOf(terms);
        log.info("关键词词典已加载/刷新：{} 个词条", terms.size());
    }

    /** 从回答文本提取关键词 TopN(10)：先词典最长匹配，再补英文技术词，停用词过滤。 */
    public List<String> extract(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> dict = dictionary();
        String normalized = text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);

        // 词典命中：按词长降序做最长匹配（长词命中后其子串短词不再入选）
        List<String> hitsByLength = new ArrayList<>();
        for (String term : dict) {
            String t = term.toLowerCase(Locale.ROOT);
            if (t.length() >= 2 && normalized.contains(t)) {
                hitsByLength.add(t);
            }
        }
        hitsByLength.sort((a, b) -> b.length() - a.length());
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        outer:
        for (String hit : hitsByLength) {
            for (String kept : keywords) {
                if (kept.contains(hit)) {
                    continue outer; // 已被更长命中覆盖
                }
            }
            keywords.add(hit);
            if (keywords.size() >= TOP_N) {
                return List.copyOf(keywords);
            }
        }

        // 英文技术词启发（计划 §6.5-2）：补充未入词典的技术名词
        Matcher m = TECH_TOKEN.matcher(text);
        while (m.find() && keywords.size() < TOP_N) {
            String token = m.group().toLowerCase(Locale.ROOT);
            if (token.length() >= 2 && !STOPWORDS.contains(token) && !coveredBy(keywords, token)) {
                keywords.add(token);
            }
        }
        return List.copyOf(keywords);
    }

    /** 构造 FULLTEXT BOOLEAN MODE 查询串：+词1 +词2；词数超限取前 4。空关键词返回 null。 */
    public String buildBooleanQuery(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }
        List<String> terms = keywords.size() > QUERY_TERM_LIMIT
                ? keywords.subList(0, QUERY_TERM_LIMIT) : keywords;
        StringBuilder sb = new StringBuilder();
        for (String term : terms) {
            String cleaned = BOOLEAN_MODE_CHARS.matcher(term.trim()).replaceAll("");
            if (cleaned.length() >= 2) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append('+').append(cleaned);
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private List<String> dictionary() {
        List<String> dict = this.dictionary;
        if (dict == null) {
            refresh();
            dict = this.dictionary;
        }
        return dict != null ? dict : List.of();
    }

    /** 岗位侧词条：技能全名、skill_code 及其下划线分段（如 redis_mq → redis/mq）。 */
    private void collectPositionTerms(Position p, Set<String> terms) {
        if (p.getCompetencyModelJson() == null) {
            return;
        }
        try {
            CompetencyModel model = JsonUtils.parse(p.getCompetencyModelJson(), CompetencyModel.class);
            if (model == null || model.getSkills() == null) {
                return;
            }
            for (CompetencyModel.SkillItem skill : model.getSkills()) {
                if (skill.getSkillName() != null && skill.getSkillName().length() >= 2) {
                    terms.add(skill.getSkillName());
                }
                if (skill.getSkillCode() != null) {
                    terms.add(skill.getSkillCode());
                    for (String seg : skill.getSkillCode().split("_")) {
                        if (seg.length() >= 2) {
                            terms.add(seg);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("词典加载：岗位能力模型解析失败 positionId={}: {}", p.getId(), e.getMessage());
        }
    }

    /** 逗号/中文逗号分隔值拆分（keywords、skillTags 列的存储格式）。 */
    static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : csv.split("[,，]")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                result.add(t);
            }
        }
        return result;
    }

    private boolean coveredBy(Set<String> keywords, String token) {
        for (String kept : keywords) {
            if (kept.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
