package com.example.interview.resume.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 结构化解析结果（简历方案 §3.2，resume.profile_json 的落库结构）。
 * 解析输入为 PII 脱敏后文本：不含任何联系方式字段（手机/邮箱/身份证不入 basicInfo）；
 * sectionLines 仅供打分与证据句提取使用，@JsonIgnore 不落库。
 * 各键按简历实际内容可能缺席（NON_NULL）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResumeProfile {

    /** 姓名 / 求职意向（无联系方式键） */
    private Map<String, String> basicInfo;
    private Education education;
    /** 命中的技能（内置词典 ∪ 岗位能力模型技能名） */
    private List<SkillItem> skills;
    /** 项目板块切分出的项目 */
    private List<ProjectItem> projects;
    /** 实习 / 工作 / 实践经历 */
    private List<ExperienceItem> experiences;
    /** 荣誉奖项 / 证书条目 */
    private List<String> honors;

    /** 板块 key（BASIC/EDUCATION/SKILL/PROJECT/EXPERIENCE/HONOR/SELF/RAW）→ 内容行（不含标题行） */
    @JsonIgnore
    private Map<String, List<String>> sectionLines;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SkillItem {
        /** 技能名（词典原词或岗位能力模型技能名） */
        private String name;
        /** 精通 / 熟悉 / 掌握 / 了解；未出现分层词时缺席 */
        private String level;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProjectItem {
        private String name;
        /** 项目文本命中的技能（与 skills 交集） */
        private List<String> techStack;
        /** 亮点句（量化句优先，最多 2 句） */
        private List<String> highlights;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExperienceItem {
        /** 机构 / 公司 */
        private String org;
        /** 角色 / 岗位 */
        private String role;
        /** 时长（月），日期区间可解析时存在 */
        private Integer months;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Education {
        /** 博士 / 硕士 / 本科 / 大专 */
        private String degree;
        private String major;
        private String school;
    }
}
