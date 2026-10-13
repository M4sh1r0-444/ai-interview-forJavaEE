package com.example.interview.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.interview.common.result.Result;
import com.example.interview.question.dto.QuestionAdminVO;
import com.example.interview.question.dto.QuestionUpsertRequest;
import com.example.interview.question.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端题库 CRUD（方案 §10.5，计划 §5.5-21~24）。全部 ROLE_ADMIN。
 */
@RestController
@RequestMapping("/api/admin/questions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminQuestionController {

    private final QuestionService questionService;

    /** 用例 21：列表。 */
    @GetMapping
    public Result<Page<QuestionAdminVO>> list(
            @RequestParam(required = false) Long positionId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(questionService.adminPage(positionId, type, difficulty, page, size));
    }

    /** 用例 22：新增。 */
    @PostMapping
    public Result<QuestionAdminVO> create(@Valid @RequestBody QuestionUpsertRequest req) {
        return Result.ok(questionService.create(req));
    }

    /** 用例 23：修改（version 乐观锁）。 */
    @PutMapping("/{id}")
    public Result<QuestionAdminVO> update(@PathVariable Long id,
                                          @Valid @RequestBody QuestionUpsertRequest req) {
        return Result.ok(questionService.update(id, req));
    }

    /** 用例 24：逻辑删。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return Result.ok();
    }
}
