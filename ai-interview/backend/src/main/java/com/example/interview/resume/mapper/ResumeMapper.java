package com.example.interview.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.interview.resume.entity.Resume;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ResumeMapper extends BaseMapper<Resume> {

    /**
     * 条件更新状态（乐观锁，简历计划 §6.2 markStatus）：影响行数=0 表示状态已被并发改变。
     * 目标为 ANALYZING 时清空上次失败原因；目标为 FAILED 时写入本次原因。
     */
    @Update("""
            UPDATE resume
               SET status = #{to},
                   fail_reason = CASE
                                   WHEN #{to} = 'ANALYZING' THEN NULL
                                   WHEN #{to} = 'FAILED'    THEN #{failReason}
                                   ELSE fail_reason
                                 END
             WHERE id = #{id} AND status = #{from} AND deleted = 0
            """)
    int casStatus(@Param("id") Long id, @Param("from") String from, @Param("to") String to,
                  @Param("failReason") String failReason);
}
