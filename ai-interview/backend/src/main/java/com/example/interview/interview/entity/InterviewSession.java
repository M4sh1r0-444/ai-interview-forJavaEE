package com.example.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 表 4：面试会话。status 为唯一事实源，转换走条件更新乐观锁（计划 §6.1）。
 */
@Data
@TableName("interview_session")
public class InterviewSession {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long positionId;
    /** TEXT / AUDIO */
    private String mode;
    private Integer difficulty;
    /** 计划主问题数 5~8 */
    private Integer plannedRounds;
    /** 用户选择的面试时长（分钟，V7/FIX-08 持久化；历史行默认 30） */
    private Integer plannedDurationMinutes;
    /** 当前主问题序号（0-based，追问不 +1）；FINISHED 后 = plannedRounds */
    private Integer currentRound;
    /** 见 InterviewStatus */
    private String status;
    /** 结束后由报告流程回填（M3） */
    private BigDecimal totalScore;
    /** 个性化面试来源简历（V8，简历计划 R3-1）；空=普通面试，基线行为不变 */
    private Long resumeId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
