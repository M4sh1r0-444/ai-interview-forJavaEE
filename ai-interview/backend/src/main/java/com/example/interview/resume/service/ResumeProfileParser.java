package com.example.interview.resume.service;

import com.example.interview.common.util.JsonUtils;
import com.example.interview.position.dto.CompetencyModel;
import com.example.interview.position.entity.Position;
import com.example.interview.resume.dto.ResumeDigest;
import com.example.interview.resume.dto.ResumeProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构化解析与 PII 脱敏（简历方案 §3.2 / 计划 §6.4，R2-1）。纯规则、确定性：
 * 同一输入永远产出同一 profile，单测直接喂字符串，不起 Spring 依赖。
 *
 * <p>脱敏（{@link #maskPii}）先于解析与送模型执行；解析入口 {@link #parse} 只接收脱敏后文本。
 * 技能识别 = 内置通用词典 ∪ 岗位能力模型技能名（KeywordExtractor 词典面向"回答 TopN 关键词 +
 * FULLTEXT 查询"，与简历技能识别诉求不同，此处不注入，见进度记录决策）。
 * 全无标题命中时整体作为 RAW 板块，交由评分与 AI 容错。</p>
 */
@Slf4j
@Component
public class ResumeProfileParser {

    // ==================== 板块词典（允许 #/【】/—/序号 修饰） ====================

    /** 板块 key → 标题同义词（LinkedHashMap 保证 BASIC→SELF 的优先匹配顺序） */
    private static final Map<String, List<String>> SECTION_TITLES = new LinkedHashMap<>();

    static {
        SECTION_TITLES.put("EDUCATION", List.of("教育背景", "教育经历", "教育信息"));
        SECTION_TITLES.put("SKILL", List.of("专业技能", "技术栈", "技能特长", "技能清单"));
        SECTION_TITLES.put("PROJECT", List.of("项目经历", "项目经验", "项目与实践"));
        SECTION_TITLES.put("EXPERIENCE", List.of("实习经历", "工作经历", "实践经历"));
        SECTION_TITLES.put("HONOR", List.of("荣誉奖项", "获奖情况", "荣誉", "奖项", "证书"));
        SECTION_TITLES.put("SELF", List.of("自我评价", "个人评价"));
    }

    // ==================== PII 正则 ====================

    /** 11 位手机号（前后非数字，防 18 位身份证子串误命中） */
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    /** 18 位身份证（末位可 X） */
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");

    // ==================== 技能词典（内置通用软件研发词典，含分类） ====================

    /** name:category；匹配为文本包含（忽略大小写、去空白），长词优先去短词子串 */
    private record DictSkill(String name, String category) {
        DictSkill(String csv) {
            this(csv.substring(0, csv.indexOf(':')), csv.substring(csv.indexOf(':') + 1));
        }
    }

    private static final List<DictSkill> SKILL_DICTIONARY = List.of(
            // LANGUAGE
            new DictSkill("Java:LANGUAGE"), new DictSkill("Golang:LANGUAGE"),
            new DictSkill("Python:LANGUAGE"), new DictSkill("C++:LANGUAGE"),
            new DictSkill("JavaScript:LANGUAGE"), new DictSkill("TypeScript:LANGUAGE"),
            new DictSkill("SQL:LANGUAGE"), new DictSkill("JVM:LANGUAGE"),
            // BACKEND
            new DictSkill("Spring Boot:BACKEND"), new DictSkill("Spring Cloud:BACKEND"),
            new DictSkill("Spring MVC:BACKEND"), new DictSkill("Spring:BACKEND"),
            new DictSkill("MyBatis-Plus:BACKEND"), new DictSkill("MyBatis:BACKEND"),
            new DictSkill("Dubbo:BACKEND"), new DictSkill("Netty:BACKEND"),
            // FRONTEND
            new DictSkill("Vue3:FRONTEND"), new DictSkill("Vue:FRONTEND"),
            new DictSkill("React:FRONTEND"), new DictSkill("Node.js:FRONTEND"),
            new DictSkill("HTML:FRONTEND"), new DictSkill("CSS:FRONTEND"),
            new DictSkill("Vite:FRONTEND"), new DictSkill("Webpack:FRONTEND"),
            // DATABASE
            new DictSkill("MySQL:DATABASE"), new DictSkill("PostgreSQL:DATABASE"),
            new DictSkill("Oracle:DATABASE"), new DictSkill("Redis:DATABASE"),
            new DictSkill("MongoDB:DATABASE"), new DictSkill("Elasticsearch:DATABASE"),
            // MIDDLEWARE
            new DictSkill("Kafka:MIDDLEWARE"), new DictSkill("RabbitMQ:MIDDLEWARE"),
            new DictSkill("RocketMQ:MIDDLEWARE"), new DictSkill("Zookeeper:MIDDLEWARE"),
            new DictSkill("Nacos:MIDDLEWARE"),
            // DEVOPS
            new DictSkill("Linux:DEVOPS"), new DictSkill("Docker:DEVOPS"),
            new DictSkill("Kubernetes:DEVOPS"), new DictSkill("Jenkins:DEVOPS"),
            new DictSkill("Git:DEVOPS"), new DictSkill("Maven:DEVOPS"), new DictSkill("Nginx:DEVOPS"),
            // COMMON（中文概念词）
            new DictSkill("并发编程:COMMON"), new DictSkill("多线程:COMMON"),
            new DictSkill("分布式:COMMON"), new DictSkill("微服务:COMMON"),
            new DictSkill("缓存:COMMON"), new DictSkill("消息队列:COMMON"),
            new DictSkill("数据结构:COMMON"), new DictSkill("算法:COMMON"),
            new DictSkill("计算机网络:COMMON"), new DictSkill("操作系统:COMMON"));

    /** 分层词 → level（距离技能名最近者优先） */
    private static final Map<String, String> LEVEL_WORDS = Map.of(
            "精通", "精通", "熟练", "熟悉", "熟悉", "熟悉", "掌握", "掌握", "了解", "了解");

    private static final Pattern NAME_LINE = Pattern.compile("姓名[:：]\\s*([^\\s，,；;]{2,10})");
    private static final Pattern OBJECTIVE_LINE =
            Pattern.compile("(?:求职意向|意向岗位|目标岗位|应聘岗位)[:：]\\s*([^\\n，,；;]{2,40})");
    private static final Pattern DEGREE_WORDS = Pattern.compile("博士|硕士|研究生|本科|大专|专科");
    private static final Pattern MAJOR_WORDS = Pattern.compile(
            "[\\u4e00-\\u9fa5]{2,12}(?:科学|工程|技术|教育|管理)(?:与[\\u4e00-\\u9fa5]{2,8})?(?:专业)?");
    private static final Pattern SCHOOL_WORDS =
            Pattern.compile("[\\u4e00-\\u9fa5A-Za-z]{2,20}(?:大学|学院|University)");
    private static final Pattern DATE_RANGE = Pattern.compile(
            "(\\d{4})[./年\\-](\\d{1,2})\\s*[-–~至到]\\s*(?:(\\d{4})[./年\\-](\\d{1,2})|至今|现在)");
    private static final Pattern ROLE_WORDS = Pattern.compile(
            "实习生|工程师|架构师|开发|前端|后端|测试|运维|算法|产品|运营|分析|设计|经理|助理|讲师");
    private static final Pattern PROJECT_HEAD = Pattern.compile(
            "^(?:[-*•·]\\s*)?(?:项目名[称]?|项目标题|Project)\\s*[:：]\\s*(.+)$");
    private static final Pattern BOOK_TITLE = Pattern.compile("^《[^》]{1,40}》");
    /** 量化句：数字 +（百分比/性能单位/量词） */
    private static final Pattern QUANTIFIED = Pattern.compile(
            "\\d+(?:\\.\\d+)?\\s*(?:%|％|QPS|qps|万|千|亿|倍|ms|s|G|GB|M|MB|T|TB|w|k|W|K|个|名|次|条|题|项|张|场|人|日|月|年|行|台|类)");
    private static final Pattern ENGLISH_TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9+#.]{1,}");

    // ==================== PII 脱敏 ====================

    /**
     * 手机/邮箱/身份证脱敏（正则与示例见方案 §3.2 表）：
     * 手机 1[3-9]\d{9} → 前3+****+后2；邮箱 → 首字符+***+@域名；身份证 18 位 → 前3+**********+后2。
     * 先身份证后手机（lookaround 已互斥，双保险）；幂等（已脱敏文本不再变化）。
     */
    public String maskPii(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String s = ID_CARD.matcher(text).replaceAll(m -> {
            String g = m.group();
            return g.substring(0, 3) + "**********" + g.substring(16);
        });
        s = PHONE.matcher(s).replaceAll(m -> {
            String g = m.group();
            return g.substring(0, 3) + "****" + g.substring(9);
        });
        return EMAIL.matcher(s).replaceAll(m -> {
            String g = m.group();
            int at = g.indexOf('@');
            return g.charAt(0) + "***" + g.substring(at);
        });
    }

    // ==================== 结构化解析 ====================

    /**
     * 解析脱敏后文本 → 结构化 profile。position 为空时技能识别仅用内置词典（通用分析）。
     */
    public ResumeProfile parse(String maskedText, Position position) {
        ResumeProfile profile = new ResumeProfile();
        if (maskedText == null || maskedText.isBlank()) {
            profile.setSectionLines(Map.of());
            return profile;
        }
        Map<String, List<String>> sections = splitSections(maskedText);
        profile.setSectionLines(sections);

        profile.setBasicInfo(extractBasicInfo(sections));
        profile.setEducation(extractEducation(sections));
        profile.setSkills(recognizeSkills(maskedText, position));
        profile.setProjects(splitProjects(sections, profile.getSkills()));
        profile.setExperiences(extractExperiences(sections));
        profile.setHonors(extractHonors(sections));
        return profile;
    }

    /** digest（简历方案 §3.2 契约）：profile → skills/projects/experiences/education，无联系方式；空 profile 产出空集合。 */
    public ResumeDigest toDigest(ResumeProfile profile) {
        ResumeDigest digest = new ResumeDigest();
        digest.setSkills(new ArrayList<>());
        digest.setProjects(new ArrayList<>());
        digest.setExperiences(new ArrayList<>());
        if (profile == null) {
            return digest;
        }
        List<ResumeDigest.Skill> skills = new ArrayList<>();
        Map<String, String> categoryByName = categoryIndex();
        if (profile.getSkills() != null) {
            for (ResumeProfile.SkillItem s : profile.getSkills()) {
                ResumeDigest.Skill d = new ResumeDigest.Skill();
                d.setName(s.getName());
                d.setCategory(categoryByName.getOrDefault(normalizeForMatch(s.getName()), "COMMON"));
                d.setLevel(s.getLevel());
                skills.add(d);
            }
        }
        digest.setSkills(skills);

        List<ResumeDigest.Project> projects = new ArrayList<>();
        if (profile.getProjects() != null) {
            for (ResumeProfile.ProjectItem p : profile.getProjects()) {
                ResumeDigest.Project d = new ResumeDigest.Project();
                d.setName(p.getName());
                d.setTechStack(p.getTechStack());
                d.setHighlights(p.getHighlights());
                projects.add(d);
            }
        }
        digest.setProjects(projects);

        List<ResumeDigest.Experience> experiences = new ArrayList<>();
        if (profile.getExperiences() != null) {
            for (ResumeProfile.ExperienceItem e : profile.getExperiences()) {
                ResumeDigest.Experience d = new ResumeDigest.Experience();
                d.setOrg(e.getOrg());
                d.setRole(e.getRole());
                d.setMonths(e.getMonths());
                experiences.add(d);
            }
        }
        digest.setExperiences(experiences);

        if (profile.getEducation() != null) {
            ResumeDigest.Education d = new ResumeDigest.Education();
            d.setDegree(profile.getEducation().getDegree());
            d.setMajor(profile.getEducation().getMajor());
            digest.setEducation(d);
        }
        return digest;
    }

    // ==================== 分段 ====================

    /**
     * 行级扫描按标题词典切板块（计划 §6.4-1）。标题行允许 `#`/`【】`/`—`/`·`/序号 修饰；
     * 首个标题前的内容归 BASIC；全无命中 → 全文为 RAW。
     */
    private Map<String, List<String>> splitSections(String text) {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        String current = "BASIC";
        boolean sawHeader = false;
        for (String raw : text.split("\n", -1)) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            String key = sectionKeyOf(line);
            if (key != null) {
                current = key;
                sawHeader = true;
                sections.putIfAbsent(current, new ArrayList<>());
                continue;
            }
            sections.computeIfAbsent(current, k -> new ArrayList<>()).add(line);
        }
        if (!sawHeader) {
            Map<String, List<String>> rawOnly = new LinkedHashMap<>();
            rawOnly.put("RAW", sections.getOrDefault("BASIC", List.of()));
            return rawOnly;
        }
        if (sections.get("BASIC") != null && sections.get("BASIC").isEmpty()) {
            sections.remove("BASIC"); // 首标题前无内容（如简历以"教育背景"开头）
        }
        return sections;
    }

    /** 剥离修饰后是否恰好为板块标题（长度限制防正文中含关键词的普通句误切）。 */
    private String sectionKeyOf(String line) {
        String s = line.replaceFirst("^#{1,6}\\s*", "")
                .replaceFirst("^[\\*·•—\\-]+\\s*", "")
                .replaceFirst("[\\*·•—\\-]+$", "")
                .replaceFirst("^[（(【\\[]", "")
                .replaceFirst("[）)】\\]]$", "")
                .replaceFirst("^[一二三四五六七八九十0-9]+[、.．]\\s*", "")
                .replaceFirst("[:：]\\s*$", "")
                .strip();
        if (s.length() > 8) {
            return null;
        }
        for (Map.Entry<String, List<String>> e : SECTION_TITLES.entrySet()) {
            if (e.getValue().contains(s)) {
                return e.getKey();
            }
        }
        return null;
    }

    // ==================== 基础信息 ====================

    /** 姓名（"姓名：X" 或 BASIC 区短首行）与求职意向；不含联系方式键（方案 §3.2）。 */
    private Map<String, String> extractBasicInfo(Map<String, List<String>> sections) {
        Map<String, String> basic = new LinkedHashMap<>();
        List<String> lines = sections.getOrDefault("RAW", sections.getOrDefault("BASIC", List.of()));
        Matcher name = NAME_LINE.matcher(String.join("\n", lines));
        if (name.find()) {
            basic.put("姓名", name.group(1));
        } else if (!lines.isEmpty()) {
            String first = lines.get(0).replaceFirst("^#{1,6}\\s*", "").strip();
            if (first.length() >= 2 && first.length() <= 8 && !first.matches(".*[\\d@：:].*")) {
                basic.put("姓名", first);
            }
        }
        Matcher objective = OBJECTIVE_LINE.matcher(String.join("\n", lines));
        if (objective.find()) {
            basic.put("求职意向", objective.group(1));
        }
        return basic.isEmpty() ? null : basic;
    }

    // ==================== 教育 ====================

    private ResumeProfile.Education extractEducation(Map<String, List<String>> sections) {
        String text = String.join("\n",
                sections.getOrDefault("EDUCATION", sections.getOrDefault("RAW", List.of())));
        if (text.isBlank()) {
            text = String.join("\n", sections.getOrDefault("BASIC", List.of()));
        }
        if (text.isBlank()) {
            return null;
        }
        ResumeProfile.Education edu = new ResumeProfile.Education();
        Matcher degree = DEGREE_WORDS.matcher(text);
        if (degree.find()) {
            String d = degree.group();
            edu.setDegree("研究生".equals(d) ? "硕士" : d);
        }
        Matcher major = MAJOR_WORDS.matcher(text);
        if (major.find()) {
            edu.setMajor(major.group());
        }
        Matcher school = SCHOOL_WORDS.matcher(text);
        if (school.find()) {
            edu.setSchool(school.group());
        }
        return edu.getDegree() == null && edu.getMajor() == null && edu.getSchool() == null
                ? null : edu;
    }

    // ==================== 技能识别 ====================

    /**
     * 内置词典 ∪ 岗位能力模型技能名 → 文本包含匹配（去空白小写），长词优先去子串重合短词
     * （如命中 Spring Boot 则不再记 Spring）；level 取技能名前 8 字符内最近的分层词。
     */
    private List<ResumeProfile.SkillItem> recognizeSkills(String text, Position position) {
        Map<String, String> candidates = new LinkedHashMap<>(); // 归一化名 → 展示名
        for (DictSkill entry : SKILL_DICTIONARY) {
            candidates.putIfAbsent(normalizeForMatch(entry.name()), entry.name());
        }
        for (String name : positionSkillNames(position)) {
            if (name != null && name.length() >= 2) {
                candidates.putIfAbsent(normalizeForMatch(name), name);
            }
        }
        String normalized = normalizeForMatch(text);

        // 命中 → 按词长降序做最长匹配去重
        List<String> hits = new ArrayList<>();
        for (Map.Entry<String, String> c : candidates.entrySet()) {
            if (normalized.contains(c.getKey())) {
                hits.add(c.getKey());
            }
        }
        hits.sort(Comparator.comparingInt(String::length).reversed());
        LinkedHashSet<String> kept = new LinkedHashSet<>();
        outer:
        for (String hit : hits) {
            for (String k : kept) {
                if (k.contains(hit)) {
                    continue outer;
                }
            }
            kept.add(hit);
        }

        List<ResumeProfile.SkillItem> skills = new ArrayList<>();
        for (String key : kept) {
            ResumeProfile.SkillItem item = new ResumeProfile.SkillItem();
            item.setName(candidates.get(key));
            String level = levelOf(text, candidates.get(key));
            if (level != null) {
                item.setLevel(level);
            }
            skills.add(item);
        }
        return skills;
    }

    /** 技能名首次出现位置之前 8 字符内的分层词（最近者优先）；无则 null。 */
    private String levelOf(String text, String skillName) {
        Matcher m = Pattern.compile(Pattern.quote(skillName), Pattern.CASE_INSENSITIVE).matcher(text);
        if (!m.find()) {
            return null;
        }
        int from = Math.max(0, m.start() - 8);
        String window = text.substring(from, m.start());
        int bestPos = -1;
        String best = null;
        for (String word : LEVEL_WORDS.keySet()) {
            int pos = window.lastIndexOf(word);
            if (pos >= 0 && pos > bestPos) {
                bestPos = pos;
                best = word;
            }
        }
        return best != null ? LEVEL_WORDS.get(best) : null;
    }

    private List<String> positionSkillNames(Position position) {
        if (position == null || position.getCompetencyModelJson() == null) {
            return List.of();
        }
        try {
            CompetencyModel model = JsonUtils.parse(position.getCompetencyModelJson(), CompetencyModel.class);
            if (model == null || model.getSkills() == null) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            for (CompetencyModel.SkillItem s : model.getSkills()) {
                if (s.getSkillName() != null) {
                    names.add(s.getSkillName());
                }
            }
            return names;
        } catch (Exception e) {
            log.warn("岗位能力模型解析失败，按通用词典识别技能 positionId={}：{}", position.getId(), e.getMessage());
            return List.of();
        }
    }

    // ==================== 项目切分 ====================

    /**
     * 项目板块内按行首"项目名(称)：/《…》"分段（计划 §6.4-3）；每段抽 techStack（与已识别
     * 技能交集）与亮点句（量化句优先，最多 2 句，截断 80 字）。
     */
    private List<ResumeProfile.ProjectItem> splitProjects(Map<String, List<String>> sections,
                                                          List<ResumeProfile.SkillItem> skills) {
        List<String> lines = sections.get("PROJECT");
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<List<String>> blocks = new ArrayList<>();
        for (String line : lines) {
            boolean isHead = PROJECT_HEAD.matcher(line).matches() || BOOK_TITLE.matcher(line).find();
            if (isHead || blocks.isEmpty()) {
                blocks.add(new ArrayList<>());
            }
            blocks.get(blocks.size() - 1).add(line);
        }
        List<ResumeProfile.ProjectItem> projects = new ArrayList<>();
        for (List<String> block : blocks) {
            String joined = String.join("\n", block);
            String name = null;
            Matcher head = PROJECT_HEAD.matcher(block.get(0));
            if (head.matches()) {
                name = head.group(1).strip();
            } else {
                Matcher book = BOOK_TITLE.matcher(block.get(0));
                if (book.find()) {
                    name = book.group();
                }
            }
            projects.add(projectOf(name, joined, skills));
        }
        return projects;
    }

    private ResumeProfile.ProjectItem projectOf(String name, String blockText,
                                                List<ResumeProfile.SkillItem> skills) {
        ResumeProfile.ProjectItem p = new ResumeProfile.ProjectItem();
        p.setName(name != null ? truncate(name, 40) : truncate(firstSentence(blockText), 40));
        List<String> techStack = new ArrayList<>();
        if (skills != null) {
            String normalized = normalizeForMatch(blockText);
            for (ResumeProfile.SkillItem s : skills) {
                if (normalized.contains(normalizeForMatch(s.getName()))) {
                    techStack.add(s.getName());
                }
            }
        }
        if (!techStack.isEmpty()) {
            p.setTechStack(techStack);
        }
        List<String> highlights = new ArrayList<>();
        for (String sentence : splitSentences(blockText)) {
            if (highlights.size() >= 2) {
                break;
            }
            if (isQuantified(sentence)) {
                highlights.add(truncate(sentence.strip(), 80));
            }
        }
        if (highlights.isEmpty()) {
            String first = firstSentence(blockText);
            if (first != null && !first.isBlank()) {
                highlights.add(truncate(first.strip(), 80));
            }
        }
        if (!highlights.isEmpty()) {
            p.setHighlights(highlights);
        }
        return p;
    }

    // ==================== 经历 ====================

    /**
     * 经历板块条目提取：仅含日期区间的行视为经历条目（典型"时间 机构 角色"表头行），
     * 描述性正文行（参与/负责/完成开头或列表项）跳过；月份由日期区间折算，
     * 其余按 2+ 空格分段取机构与角色。
     */
    private List<ResumeProfile.ExperienceItem> extractExperiences(Map<String, List<String>> sections) {
        List<String> lines = sections.get("EXPERIENCE");
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<ResumeProfile.ExperienceItem> experiences = new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith("技术栈") || PROJECT_HEAD.matcher(line).matches()) {
                continue; // 无经历板块时误入的项目行
            }
            Integer months = monthsOf(line);
            if (months == null) {
                continue; // 无日期区间的描述行不是独立经历条目
            }
            ResumeProfile.ExperienceItem item = new ResumeProfile.ExperienceItem();
            item.setMonths(months);
            String withoutDate = DATE_RANGE.matcher(line).replaceAll(" ").strip();
            String[] parts = withoutDate.split("\\s{2,}");
            String org = null;
            String role = null;
            for (String part : parts) {
                String t = part.strip();
                if (t.isEmpty() || "至今".equals(t)) {
                    continue;
                }
                if (role == null && ROLE_WORDS.matcher(t).find()) {
                    role = t;
                } else if (org == null) {
                    org = t;
                }
            }
            if (org == null && role == null) {
                continue; // 纯日期行等无信息行
            }
            item.setOrg(org != null ? truncate(org, 40) : null);
            item.setRole(role != null ? truncate(role, 40) : null);
            experiences.add(item);
        }
        return experiences;
    }

    /** 日期区间 → 月数（"至今"按当前时间折算，取整月）。 */
    private Integer monthsOf(String line) {
        Matcher m = DATE_RANGE.matcher(line);
        if (!m.find()) {
            return null;
        }
        int y1 = Integer.parseInt(m.group(1));
        int mo1 = Integer.parseInt(m.group(2));
        int y2;
        int mo2;
        if (m.group(3) != null) {
            y2 = Integer.parseInt(m.group(3));
            mo2 = Integer.parseInt(m.group(4));
        } else {
            java.time.LocalDate now = java.time.LocalDate.now();
            y2 = now.getYear();
            mo2 = now.getMonthValue();
        }
        return Math.max(0, (y2 - y1) * 12 + (mo2 - mo1));
    }

    // ==================== 荣誉 ====================

    private List<String> extractHonors(Map<String, List<String>> sections) {
        List<String> lines = sections.get("HONOR");
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        return lines.stream()
                .map(l -> l.replaceFirst("^[-*•·]\\s*", "").strip())
                .filter(l -> !l.isEmpty())
                .map(l -> truncate(l, 60))
                .toList();
    }

    // ==================== 供打分复用的文本统计（Mock 规则同口径） ====================

    /** 句子切分（。！？!?；;\n），保留原文（不 trim 尾分隔符）。 */
    public static List<String> splitSentences(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> sentences = new ArrayList<>();
        for (String s : text.split("[。！？!?；;\n]+")) {
            if (!s.isBlank()) {
                sentences.add(s);
            }
        }
        return sentences;
    }

    /** 量化句：含"数字 + 百分比/性能单位/量词"形态（方案 §4.3：含数字+% 的句子）。 */
    public static boolean isQuantified(String sentence) {
        return sentence != null && QUANTIFIED.matcher(sentence).find();
    }

    /** 首句（无句读分隔时取首行前 80 字）。 */
    public static String firstSentence(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        List<String> sentences = splitSentences(text);
        return sentences.isEmpty() ? text.strip() : sentences.get(0);
    }

    // ==================== 工具 ====================

    /** 匹配用归一化：去空白、小写（中文包含匹配与英文大小写统一；Mock 打分同口径复用）。 */
    public static String normalizeForMatch(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /** name → category 索引（digest 分类用；岗位技能名不在词典内记 COMMON）。 */
    private Map<String, String> categoryIndex() {
        Map<String, String> index = new LinkedHashMap<>();
        for (DictSkill entry : SKILL_DICTIONARY) {
            index.putIfAbsent(normalizeForMatch(entry.name()), entry.category());
        }
        return index;
    }

    public static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
