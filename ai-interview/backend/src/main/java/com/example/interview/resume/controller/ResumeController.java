package com.example.interview.resume.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.interview.common.result.Result;
import com.example.interview.resume.dto.ResumeDetailVO;
import com.example.interview.resume.dto.ResumeItemVO;
import com.example.interview.resume.dto.UploadResumeVO;
import com.example.interview.resume.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 简历管理接口（简历协议 §3，用例 R-01~R-08）。R1 交付上传/列表/详情/默认/删除；
 * R2 补 analysis/reanalyze，R3 补 focus-skills。鉴权走 SecurityConfig 默认 authenticated()，
 * STUDENT/ADMIN 均按本人数据处理。
 */
@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    /** R-01：上传简历（multipart：file 必填 + positionId 可选），同步完成校验/落盘/抽取。 */
    @PostMapping
    public Result<UploadResumeVO> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "positionId", required = false) Long positionId) {
        return Result.ok(resumeService.upload(file, positionId));
    }

    /** R-02：我的简历列表（分页，createdAt 倒序；page ≥1、size 1~50，越界 40002）。 */
    @GetMapping
    public Result<Page<ResumeItemVO>> page(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return Result.ok(resumeService.page(page, size));
    }

    /** R-03：简历详情（轮询端点，ANALYZED 后含 analysis / digest / profile）。 */
    @GetMapping("/{id}")
    public Result<ResumeDetailVO> detail(@PathVariable Long id) {
        return Result.ok(resumeService.detail(id));
    }

    /** R-06：设为默认简历（仅 ANALYZED；每用户至多一份，原默认自动取消）。 */
    @PutMapping("/{id}/active")
    public Result<Map<String, Object>> setActive(@PathVariable Long id) {
        return Result.ok(resumeService.setActive(id));
    }

    /** R-07：逻辑删除（列表不可见、不可再被新面试引用；历史会话不受影响）。 */
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        return Result.ok(resumeService.delete(id));
    }
}
