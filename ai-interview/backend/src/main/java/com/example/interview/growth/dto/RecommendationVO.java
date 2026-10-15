package com.example.interview.growth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 个性化推荐视图（计划 §5.4 #19 + §6.7 输出契约）：
 * {@code [{skillCode, priority, weaknessReasons[], resources[], practices[], retryQuestions[], nextInterviewSuggested}]}。
 * retryQuestions 为 §6.7 错题重练（M5-5.3 增列，见进度 D23）；整体经 Redis
 * 缓存往返序列化，需保留无参构造。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecommendationVO {

    private String skillCode;
    private String skillName;
    /** 优先级 = (100-掌握度)×技能权重（计划 §6.7），降序输出 */
    private Double priority;
    /** 短板理由：WeaknessDetector 四规则命中项；无命中时为相对提升类说明 */
    private List<String> weaknessReasons;
    /** 学习资源：ARTICLE / QUESTION（计划 §6.7） */
    private List<ResourceVO> resources;
    /** 练习项：PRACTICE / PROJECT */
    private List<ResourceVO> practices;
    /** 错题重练：最近一场面试该技能 missingPoints 命中的题目 */
    private List<RetryQuestionVO> retryQuestions;
    /** 建议下一次面试配置（岗位、难度、重点覆盖技能） */
    private String nextInterviewSuggested;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceVO {
        private Long id;
        private String title;
        /** ARTICLE / QUESTION / PRACTICE / PROJECT */
        private String resourceType;
        private String contentUrl;
        private Integer difficulty;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetryQuestionVO {
        private Long questionId;
        private String questionContent;
        /** 该题未覆盖的失分点 */
        private List<String> missingPoints;
    }
}
