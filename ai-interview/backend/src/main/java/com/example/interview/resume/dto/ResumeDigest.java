package com.example.interview.resume.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 个性化摘要（简历方案 §3.2 digest_json，F2 全链路数据契约，Mock 与真实 Provider 共用、
 * 由规则解析器确定性产出）。仅保留 skills/projects/experiences/education，
 * 不含任何联系方式。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResumeDigest {

    private List<Skill> skills;
    private List<Project> projects;
    private List<Experience> experiences;
    private Education education;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Skill {
        private String name;
        /** BACKEND / FRONTEND / DATABASE / MIDDLEWARE / DEVOPS / LANGUAGE / COMMON */
        private String category;
        /** 精通 / 熟悉 / 掌握 / 了解 */
        private String level;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Project {
        private String name;
        private List<String> techStack;
        private List<String> highlights;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Experience {
        private String org;
        private String role;
        private Integer months;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Education {
        private String degree;
        private String major;
    }
}
