package com.example.interview.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.interview.admin.dto.ResourceUpsertRequest;
import com.example.interview.admin.service.AdminResourceService;
import com.example.interview.common.result.Result;
import com.example.interview.growth.entity.LearningResource;
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
 * 管理端学习资源 CRUD（计划 §5.5 #29★）。全部 ROLE_ADMIN。
 */
@RestController
@RequestMapping("/api/admin/resources")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminResourceController {

    private final AdminResourceService adminResourceService;

    /** 用例 #29★：列表（含已下架，id 倒序）。 */
    @GetMapping
    public Result<Page<LearningResource>> list(
            @RequestParam(required = false) Long positionId,
            @RequestParam(required = false) String skillCode,
            @RequestParam(required = false) String resourceType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(adminResourceService.page(positionId, skillCode, resourceType, page, size));
    }

    /** 用例 #29★：新增（skillCode 须对齐岗位能力模型）。 */
    @PostMapping
    public Result<LearningResource> create(@Valid @RequestBody ResourceUpsertRequest req) {
        return Result.ok(adminResourceService.create(req));
    }

    /** 用例 #29★：修改（全量替换）。 */
    @PutMapping("/{id}")
    public Result<LearningResource> update(@PathVariable Long id,
                                           @Valid @RequestBody ResourceUpsertRequest req) {
        return Result.ok(adminResourceService.update(id, req));
    }

    /** 用例 #29★：下架（enabled→0，推荐匹配即刻排除）。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminResourceService.delete(id);
        return Result.ok();
    }
}
