package com.example.interview.admin.controller;

import com.example.interview.common.result.Result;
import com.example.interview.knowledge.dto.KnowledgeUpsertRequest;
import com.example.interview.knowledge.entity.KnowledgeChunk;
import com.example.interview.knowledge.service.KnowledgeAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端知识库（计划 §5.5-25/26）。全部 ROLE_ADMIN。
 */
@RestController
@RequestMapping("/api/admin/knowledge")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminKnowledgeController {

    private final KnowledgeAdminService knowledgeAdminService;

    /** 用例 25：新增知识片段（立即可被检索，词典同步刷新）。 */
    @PostMapping
    public Result<KnowledgeChunk> create(@Valid @RequestBody KnowledgeUpsertRequest req) {
        return Result.ok(knowledgeAdminService.create(req));
    }

    /** 用例 26：重建 FULLTEXT 索引（OPTIMIZE TABLE）。 */
    @PostMapping("/rebuild")
    public Result<Map<String, Object>> rebuild() {
        knowledgeAdminService.rebuild();
        return Result.ok(Map.of("rebuilt", true));
    }
}
