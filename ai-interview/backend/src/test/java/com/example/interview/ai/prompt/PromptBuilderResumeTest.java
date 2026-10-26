package com.example.interview.ai.prompt;

import com.example.interview.ai.model.NextQuestionContext;
import com.example.interview.ai.model.QuestionPersonalizeContext;
import com.example.interview.resume.dto.ResumeDigest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R3-4 Prompt 断言：个性化会话 nextQuestion Prompt 含"候选人简历要点"段（技术栈+
 * 项目名+深挖提示句）；digest 为 null 时不追加（与基线逐字节一致）；R3-3 改写 Prompt
 * 含岗位/题目 ID 与强约束 JSON 说明。
 */
class PromptBuilderResumeTest {

    private final PromptBuilder builder = new PromptBuilder();

    private ResumeDigest digest() {
        ResumeDigest d = new ResumeDigest();
        d.setSkills(List.of(skill("Spring Boot", "熟悉"), skill("MySQL", "熟悉"), skill("Redis", "了解")));
        ResumeDigest.Project p = new ResumeDigest.Project();
        p.setName("校园二手交易平台");
        p.setTechStack(List.of("Spring Boot", "MySQL", "Redis"));
        p.setHighlights(List.of("压测峰值 500 QPS"));
        d.setProjects(List.of(p));
        return d;
    }

    private ResumeDigest.Skill skill(String name, String level) {
        ResumeDigest.Skill s = new ResumeDigest.Skill();
        s.setName(name);
        s.setLevel(level);
        return s;
    }

    private NextQuestionContext.NextQuestionContextBuilder baseCtx() {
        return NextQuestionContext.builder()
                .sessionId(1L)
                .positionName("Java 后端开发工程师")
                .positionSkills(List.of("Java基础", "Spring生态"))
                .difficulty(3)
                .roundNo(0)
                .plannedRounds(5)
                .questionId(101L)
                .questionContent("缓存穿透有哪些解决方案？")
                .questionType("TECHNICAL")
                .followupCount(0)
                .requestedAction("FOLLOW_UP")
                .instruction("请就缺失内容追问");
    }

    @Test
    void nextQuestionPromptContainsResumeSectionWhenDigestPresent() {
        String prompt = builder.buildNextQuestionPrompt(baseCtx().resumeDigest(digest()).build());
        assertThat(prompt).contains("【候选人简历要点】");
        assertThat(prompt).contains("Spring Boot").contains("MySQL");
        assertThat(prompt).contains("《校园二手交易平台》").contains("500 QPS");
        assertThat(prompt).contains("追问可结合其项目深挖实现细节、技术选型原因、遇到的困难");
    }

    @Test
    void nextQuestionPromptUnchangedWhenDigestNull() {
        // digest 为 null：不出现简历段（追加逻辑唯一变更点，条件不命中即与基线逐字节一致）
        String prompt = builder.buildNextQuestionPrompt(baseCtx().resumeDigest(null).build());
        assertThat(prompt).doesNotContain("【候选人简历要点】");
        assertThat(prompt).doesNotContain("简历要点").doesNotContain("校园二手交易平台");
        // 与"未设置 digest 字段"的构建完全一致（字段缺省即 null）
        String unset = builder.buildNextQuestionPrompt(baseCtx().build());
        assertThat(prompt).isEqualTo(unset);
    }

    @Test
    void personalizePromptContainsPositionQuestionsAndSchema() {
        QuestionPersonalizeContext ctx = QuestionPersonalizeContext.builder()
                .sessionId(1L)
                .positionName("Java 后端开发工程师")
                .digest(digest())
                .questions(List.of(
                        QuestionPersonalizeContext.QuestionBrief.builder()
                                .questionId(101L).type("TECHNICAL").skillTags(List.of("redis_mq"))
                                .content("缓存穿透有哪些解决方案？").build(),
                        QuestionPersonalizeContext.QuestionBrief.builder()
                                .questionId(102L).type("PROJECT").skillTags(List.of("project_design"))
                                .content("介绍一个你最有代表性的项目。").build()))
                .build();
        String prompt = builder.buildPersonalizePrompt(ctx);
        assertThat(prompt).contains("Java 后端开发工程师");
        assertThat(prompt).contains("ID 101").contains("ID 102");
        assertThat(prompt).contains("缓存穿透").contains("代表性的项目");
        assertThat(prompt).contains("校园二手交易平台").contains("Spring Boot");
        assertThat(prompt).contains("\"items\":[{\"questionId\":题库ID,\"content\":\"改写后的完整题干\"}]");
        assertThat(prompt).contains("10~500 字").contains("questionId 完全一致");
    }
}
