package com.example.interview.ai.provider;

import com.example.interview.ai.model.AiNextQuestionResult;
import com.example.interview.ai.model.AiQuestionPersonalization;
import com.example.interview.ai.model.NextQuestionContext;
import com.example.interview.ai.model.QuestionPersonalizeContext;
import com.example.interview.resume.dto.ResumeDigest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R3-3/R3-4 Mock 侧单测：题干改写模板（项目版/技术栈版/原题干兜底、questionId 保留、
 * 确定性）、PROJECT 轮项目深挖追问（引用项目名、按追问次数轮转、非 PROJECT 不受影响）、
 * AiQuestionPersonalization.isValidFor 契约。
 */
class MockPersonalizeTest {

    private final MockAiProvider provider = new MockAiProvider();

    private ResumeDigest.Skill skill(String name) {
        ResumeDigest.Skill s = new ResumeDigest.Skill();
        s.setName(name);
        return s;
    }

    private ResumeDigest.Project project(String name, List<String> tech) {
        ResumeDigest.Project p = new ResumeDigest.Project();
        p.setName(name);
        p.setTechStack(tech);
        return p;
    }

    private ResumeDigest digest(ResumeDigest.Project proj, List<ResumeDigest.Skill> skills) {
        ResumeDigest d = new ResumeDigest();
        d.setProjects(proj != null ? List.of(proj) : List.of());
        d.setSkills(skills);
        return d;
    }

    private QuestionPersonalizeContext ctx(ResumeDigest digest) {
        return QuestionPersonalizeContext.builder()
                .sessionId(1L)
                .userId(2L)
                .positionName("Java 后端开发工程师")
                .digest(digest)
                .questions(List.of(
                        QuestionPersonalizeContext.QuestionBrief.builder()
                                .questionId(101L).type("TECHNICAL").skillTags(List.of("spring"))
                                .content("缓存穿透有哪些解决方案？请说明适用场景。").build(),
                        QuestionPersonalizeContext.QuestionBrief.builder()
                                .questionId(102L).type("PROJECT").skillTags(List.of("project_design"))
                                .content("介绍一个你最有代表性的项目。").build()))
                .build();
    }

    private List<String> contents(AiQuestionPersonalization r) {
        return r.getItems().stream().map(AiQuestionPersonalization.Item::getContent).toList();
    }

    // ==================== personalizeQuestions 模板 ====================

    @Test
    void projectPrefixTemplateWithTechStack() {
        ResumeDigest d = digest(project("校园二手交易平台", List.of("Spring Boot", "MySQL", "Redis")),
                List.of(skill("Java")));
        AiQuestionPersonalization r = provider.personalizeQuestions(ctx(d));
        assertThat(contents(r)).allSatisfy(c -> c.startsWith(
                "结合你在《校园二手交易平台》项目中使用 Spring Boot、MySQL 的经历——"));
        assertThat(contents(r).get(0)).contains("缓存穿透");
        assertThat(contents(r).get(1)).contains("代表性的项目");
        // questionId 原样保留、顺序与输入一致
        assertThat(r.getItems()).extracting(AiQuestionPersonalization.Item::getQuestionId)
                .containsExactly(101L, 102L);
        // 同输入同输出（确定性）
        assertThat(contents(provider.personalizeQuestions(ctx(d)))).isEqualTo(contents(r));
        // 契约自检通过
        assertThat(r.isValidFor(List.of(101L, 102L))).isTrue();
    }

    @Test
    void techOnlyPrefixWhenNoProjects() {
        ResumeDigest d = digest(null, List.of(skill("Java"), skill("MySQL"), skill("Redis"), skill("Docker")));
        AiQuestionPersonalization r = provider.personalizeQuestions(ctx(d));
        assertThat(contents(r)).allSatisfy(c -> c.startsWith("结合你掌握 Java、MySQL、Redis 的技术背景——"));
    }

    @Test
    void originalStemWhenDigestEmpty() {
        ResumeDigest empty = new ResumeDigest();
        AiQuestionPersonalization r = provider.personalizeQuestions(ctx(empty));
        assertThat(contents(r)).containsExactly(
                "缓存穿透有哪些解决方案？请说明适用场景。", "介绍一个你最有代表性的项目。");
    }

    @Test
    void projectWithoutTechStackUsesPlainProjectPrefix() {
        ResumeDigest d = digest(project("课程设计", null), List.of(skill("Java")));
        AiQuestionPersonalization r = provider.personalizeQuestions(ctx(d));
        assertThat(contents(r)).allSatisfy(c -> c.startsWith("结合你在《课程设计》项目的经历——"));
    }

    // ==================== PROJECT 轮项目深挖追问 ====================

    private ResumeDigest deepDiveDigest() {
        return digest(project("校园二手交易平台", List.of("Redis", "Spring Boot")),
                List.of(skill("MySQL"), skill("Redis"), skill("Spring Boot")));
    }

    private NextQuestionContext followupCtx(ResumeDigest digest, String questionType, int followupCount) {
        return NextQuestionContext.builder()
                .sessionId(1L)
                .positionName("Java 后端开发工程师")
                .questionContent("介绍一个你最有代表性的项目。")
                .questionType(questionType)
                .followupTemplates(List.of("模板追问一", "模板追问二"))
                .requestedAction("FOLLOW_UP")
                .reasonCode("MISSING_POINT")
                .followupCount(followupCount)
                .resumeDigest(digest)
                .build();
    }

    @Test
    void projectFollowupUsesDeepDiveTemplatesWithProjectName() {
        ResumeDigest d = deepDiveDigest();
        for (int followupCount = 0; followupCount < 3; followupCount++) {
            AiNextQuestionResult r = provider.nextQuestion(followupCtx(d, "PROJECT", followupCount));
            assertThat(r.getContent()).as("第 %d 次追问", followupCount)
                    .contains("《校园二手交易平台》").contains("Redis");
        }
        // 轮转：三次追问文案互不相同
        var c0 = provider.nextQuestion(followupCtx(d, "PROJECT", 0)).getContent();
        var c1 = provider.nextQuestion(followupCtx(d, "PROJECT", 1)).getContent();
        var c2 = provider.nextQuestion(followupCtx(d, "PROJECT", 2)).getContent();
        assertThat(List.of(c0, c1, c2)).doesNotHaveDuplicates();
    }

    @Test
    void nonProjectOrNoDigestFollowupUnchanged() {
        // 非 PROJECT 轮：走题目模板，不注入项目名
        AiNextQuestionResult technical =
                provider.nextQuestion(followupCtx(deepDiveDigest(), "TECHNICAL", 0));
        assertThat(technical.getContent()).isEqualTo("模板追问一");
        // PROJECT 轮但无 digest：走题目模板
        AiNextQuestionResult noDigest = provider.nextQuestion(followupCtx(null, "PROJECT", 0));
        assertThat(noDigest.getContent()).isEqualTo("模板追问一");
    }

    // ==================== isValidFor 契约 ====================

    @Test
    void isValidForRejectsMismatchedOrInvalidItems() {
        AiQuestionPersonalization r = new AiQuestionPersonalization();
        r.setItems(List.of(item(1L, "结合你的项目经历——原题干内容足够十个字"), item(2L, "另一条足够长度的改写题干")));

        assertThat(r.isValidFor(List.of(1L, 2L))).isTrue();
        assertThat(r.isValidFor(List.of(1L, 3L))).isFalse();          // ID 不在输入集合
        assertThat(r.isValidFor(List.of(1L))).isFalse();              // 数量不一致
        assertThat(r.isValidFor(List.of(1L, 2L, 3L))).isFalse();      // 数量不一致
        assertThat(r.isValidFor(List.of(2L, 1L))).isTrue();           // 顺序无关

        r.setItems(List.of(item(1L, "太短"), item(2L, "足够长度的改写题干")));
        assertThat(r.isValidFor(List.of(1L, 2L))).isFalse();          // 长度 <10

        r.setItems(List.of(item(1L, "a".repeat(501)), item(2L, "足够长度的改写题干")));
        assertThat(r.isValidFor(List.of(1L, 2L))).isFalse();          // 长度 >500

        r.setItems(List.of(item(1L, null), item(2L, "足够长度的改写题干")));
        assertThat(r.isValidFor(List.of(1L, 2L))).isFalse();          // content 为空
    }

    private AiQuestionPersonalization.Item item(long id, String content) {
        AiQuestionPersonalization.Item i = new AiQuestionPersonalization.Item();
        i.setQuestionId(id);
        i.setContent(content);
        return i;
    }
}
