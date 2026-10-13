package com.example.interview.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.interview.interview.entity.InterviewSession;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface InterviewSessionMapper extends BaseMapper<InterviewSession> {

    /**
     * 条件更新状态（乐观锁，计划 §6.1）：影响行数=0 表示当前状态已变化（并发）。
     * 目标为 RUNNING 时补 started_at；目标为终态时补 finished_at。
     */
    @Update("""
            UPDATE interview_session
               SET status = #{to},
                   started_at  = IF(#{to} = 'RUNNING', NOW(), started_at),
                   finished_at = IF(#{to} IN ('FINISHED', 'ABORTED', 'TIMEOUT'), NOW(), finished_at)
             WHERE id = #{id} AND status = #{from}
            """)
    int casStatus(@Param("id") Long id, @Param("from") String from, @Param("to") String to);
}
