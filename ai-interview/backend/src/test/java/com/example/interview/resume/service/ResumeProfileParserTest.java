package com.example.interview.resume.service;

import com.example.interview.common.util.JsonUtils;
import com.example.interview.position.entity.Position;
import com.example.interview.resume.ResumeFixtures;
import com.example.interview.resume.dto.ResumeDigest;
import com.example.interview.resume.dto.ResumeProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 简历计划 §8.1 ResumeProfileParserTest（R2-1，≥10 用例）：分段（含修饰/无标题容错）、
 * 技能识别（词典+岗位、最长匹配、分层词）、项目切分（techStack/量化亮点）、
 * PII 三类脱敏与边界、toDigest 契约（无联系方式）。纯函数直测，不起容器。
 */
class ResumeProfileParserTest {

    private final ResumeProfileParser parser = new ResumeProfileParser();

    private static Position javaPosition() {
        Position p = new Position();
        p.setId(1L);
        p.setName("Java 后端开发工程师");
        p.setCompetencyModelJson("""
                {"skills":[
                  {"skillCode":"java_basics","skillName":"Java基础与面向对象","weight":0.25},
                  {"skillCode":"spring","skillName":"Spring生态","weight":0.2},
                  {"skillCode":"redis_mq","skillName":"Redis与消息队列","weight":0.15},
                  {"skillCode":"mysql","skillName":"MySQL数据库","weight":0.2},
                  {"skillCode":"communication","skillName":"沟通表达","weight":0.1}]}
                """);
        return p;
    }

    private ResumeProfile parseFixture() {
        String masked = parser.maskPii(ResumeFixtures.CHINESE_RESUME_MD);
        return parser.parse(masked, javaPosition());
    }

    // ==================== PII 脱敏 ====================

    @Test
    void masksPhoneEmailAndIdCard() {
        String masked = parser.maskPii("手机：13812345678，邮箱：zhangsan@example.com，身份证：110101200001011234");
        assertThat(masked).contains("138****78").doesNotContain("13812345678");
        assertThat(masked).contains("z***@example.com").doesNotContain("zhangsan@");
        assertThat(masked).contains("110**********34").doesNotContain("110101200001011234");
    }

    @Test
    void doesNotMaskDigitsInsideLongerNumbers() {
        // 13 位数字串中的 11 位子串不是手机号（前后数字 lookaround）
        assertThat(parser.maskPii("订单号 9138123456789")).isEqualTo("订单号 9138123456789");
    }

    @Test
    void maskIsIdempotentAndSafeForCleanText() {
        String clean = "熟悉 Java 与 MySQL，无任何联系方式";
        assertThat(parser.maskPii(clean)).isEqualTo(clean);
        String once = parser.maskPii("联系 13812345678");
        assertThat(parser.maskPii(once)).isEqualTo(once);
    }

    // ==================== 分段 ====================

    @Test
    void splitsSectionsByDictionariesWithDecorations() {
        Map<String, List<String>> sections = parseFixture().getSectionLines();
        assertThat(sections).containsKeys("BASIC", "EDUCATION", "SKILL", "PROJECT", "EXPERIENCE", "HONOR");
        assertThat(sections.get("EDUCATION")).isNotEmpty();
        // 行级扫描同时兼容 #/【】/加粗 修饰
        Map<String, List<String>> decorated = parser.parse("""
                姓名：李四
                【教育背景】
                本科 计算机科学与技术
                **项目经历**
                项目名称：XX 系统
                """, null).getSectionLines();
        assertThat(decorated).containsKeys("BASIC", "EDUCATION", "PROJECT");
    }

    @Test
    void fallsBackToRawSectionWhenNoHeaders() {
        ResumeProfile profile = parser.parse("这个人写简历完全不用标题，只有一大段内容，" +
                "熟悉 Java Spring Boot MySQL Redis，做过校园二手交易平台项目，压测峰值 500 QPS。", null);
        assertThat(profile.getSectionLines()).containsOnlyKeys("RAW");
    }

    // ==================== 技能识别 ====================

    @Test
    void recognizesSkillsWithLevelsAndLongestMatch() {
        ResumeProfile profile = parseFixture();
        List<String> names = profile.getSkills().stream().map(ResumeProfile.SkillItem::getName).toList();
        assertThat(names).contains("Java", "Spring Boot", "MySQL", "Redis", "Vue3", "Docker", "JVM");
        // 最长匹配：命中 MyBatis-Plus 后不再记 MyBatis；命中 Spring Boot 不单记 Spring
        assertThat(names).contains("MyBatis-Plus").doesNotContain("MyBatis", "Spring");
        Map<String, String> levelByName = new java.util.HashMap<>();
        profile.getSkills().forEach(s -> levelByName.put(s.getName(), s.getLevel()));
        assertThat(levelByName).containsEntry("Spring Boot", "熟悉").containsEntry("Redis", "了解");
    }

    @Test
    void recognizesPositionSkillsBeyondDictionary() {
        // 岗位技能名"沟通表达"不在内置词典，出现于文本时也应被识别
        ResumeProfile profile = parser.parse("教育背景\n专业技能\n熟悉 Java，具备良好的沟通表达能力", javaPosition());
        assertThat(profile.getSkills().stream().map(ResumeProfile.SkillItem::getName))
                .contains("Java", "沟通表达");
    }

    // ==================== 项目切分 ====================

    @Test
    void splitsProjectsWithTechStackAndQuantifiedHighlights() {
        ResumeProfile profile = parseFixture();
        assertThat(profile.getProjects()).hasSize(1);
        ResumeProfile.ProjectItem p = profile.getProjects().get(0);
        assertThat(p.getName()).contains("校园二手交易平台");
        assertThat(p.getTechStack()).contains("Spring Boot", "MySQL", "Redis", "Vue3");
        // 亮点句优先取量化句
        assertThat(p.getHighlights()).isNotEmpty();
        assertThat(p.getHighlights().get(0)).contains("QPS");
    }

    @Test
    void bookTitlePatternAlsoStartsProject() {
        ResumeProfile profile = parser.parse("""
                项目经历
                《高并发秒杀系统》
                负责 Redis 预减库存设计，压测峰值 8000 QPS
                """, null);
        assertThat(profile.getProjects()).hasSize(1);
        assertThat(profile.getProjects().get(0).getName()).isEqualTo("《高并发秒杀系统》");
    }

    // ==================== 教育 / 经历 / 基础信息 ====================

    @Test
    void extractsEducationDegreeMajorAndSchool() {
        ResumeProfile.Education edu = parseFixture().getEducation();
        assertThat(edu.getDegree()).isEqualTo("本科");
        assertThat(edu.getMajor()).contains("计算机");
        assertThat(edu.getSchool()).contains("大学");
    }

    @Test
    void extractsExperienceOrgRoleAndMonths() {
        List<ResumeProfile.ExperienceItem> exp = parseFixture().getExperiences();
        assertThat(exp).hasSize(1);
        assertThat(exp.get(0).getOrg()).isEqualTo("某互联网公司");
        assertThat(exp.get(0).getRole()).isEqualTo("后端开发实习生");
        assertThat(exp.get(0).getMonths()).isEqualTo(2);
        // 描述性正文行不构成第二条经历
        assertThat(parser.parse("实习经历\n参与运营后台开发，完成 12 个接口\n2024.01 - 2024.06  A 公司  后端实习生",
                null).getExperiences()).hasSize(1);
    }

    @Test
    void extractsBasicInfoWithoutContactKeys() {
        Map<String, String> basic = parseFixture().getBasicInfo();
        assertThat(basic).containsEntry("姓名", "张三");
        assertThat(basic).containsKey("求职意向");
        assertThat(basic.get("求职意向")).contains("Java 后端");
        assertThat(basic).doesNotContainKeys("手机", "邮箱", "联系方式");
    }

    // ==================== digest ====================

    @Test
    void digestKeepsContractFieldsAndDropsContact() {
        ResumeProfile profile = parseFixture();
        ResumeDigest digest = parser.toDigest(profile);
        assertThat(digest.getSkills()).isNotEmpty();
        assertThat(digest.getSkills().stream().filter(s -> "Spring Boot".equals(s.getName())).findFirst())
                .hasValueSatisfying(s -> assertThat(s.getCategory()).isEqualTo("BACKEND"));
        assertThat(digest.getProjects()).hasSize(1);
        assertThat(digest.getEducation().getDegree()).isEqualTo("本科");
        // 契约红线：digest 序列化结果不含任何 PII（输入文本已脱敏 + 结构本身无联系方式字段）
        String json = JsonUtils.toJson(digest);
        assertThat(json).doesNotContain("13812345678", "zhangsan@");
    }

    @Test
    void emptyAndNullInputsTolerated() {
        assertThat(parser.maskPii(null)).isEmpty();
        ResumeProfile empty = parser.parse("", null);
        assertThat(empty.getSkills()).isNullOrEmpty();
        assertThat(parser.toDigest(null).getSkills()).isEmpty();
    }
}
