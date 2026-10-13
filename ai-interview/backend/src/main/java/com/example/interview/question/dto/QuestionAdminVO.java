package com.example.interview.question.dto;

import com.example.interview.question.entity.Question;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 管理端题目视图（计划 §4.3）。 */
@Data
public class QuestionAdminVO {

    private Long id;
    private Long positionId;
    private String type;
    private String content;
    private String background;
    private Integer difficulty;
    private List<String> skillTags;
    private String referenceAnswer;
    private List<String> keyPoints;
    private List<String> deductionPoints;
    private List<String> followupTemplates;
    private String status;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static QuestionAdminVO from(Question q) {
        QuestionAdminVO vo = new QuestionAdminVO();
        vo.setId(q.getId());
        vo.setPositionId(q.getPositionId());
        vo.setType(q.getType());
        vo.setContent(q.getContent());
        vo.setBackground(q.getBackground());
        vo.setDifficulty(q.getDifficulty());
        vo.setSkillTags(q.getSkillTags());
        vo.setReferenceAnswer(q.getReferenceAnswer());
        vo.setKeyPoints(q.getKeyPoints());
        vo.setDeductionPoints(q.getDeductionPoints());
        vo.setFollowupTemplates(q.getFollowupTemplates());
        vo.setStatus(q.getStatus());
        vo.setVersion(q.getVersion());
        vo.setCreatedAt(q.getCreatedAt());
        vo.setUpdatedAt(q.getUpdatedAt());
        return vo;
    }
}
