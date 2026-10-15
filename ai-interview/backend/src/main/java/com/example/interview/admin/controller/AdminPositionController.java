package com.example.interview.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.interview.admin.dto.PositionAdminVO;
import com.example.interview.admin.dto.PositionUpsertRequest;
import com.example.interview.admin.dto.WeightsUpdateRequest;
import com.example.interview.admin.service.AdminPositionService;
import com.example.interview.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端岗位与能力模型维护（计划 §5.5 #28★/#30★）。岗位被题库/历史
 * 引用，不提供删除。全部 ROLE_ADMIN。
 */
@RestController
@RequestMapping("/api/admin/positions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPositionController {

    private final AdminPositionService adminPositionService;

    /** 用例 #28★：岗位列表（含停用）。 */
    @GetMapping
    public Result<Page<PositionAdminVO>> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return Result.ok(adminPositionService.page(page, size));
    }

    /** 用例 #28★：岗位详情（含解析后的技能与评分权重）。 */
    @GetMapping("/{id}")
    public Result<PositionAdminVO> detail(@PathVariable Long id) {
        return Result.ok(adminPositionService.detail(id));
    }

    /** 用例 #28★：新增岗位（code 唯一；技能权重和=1）。 */
    @PostMapping
    public Result<PositionAdminVO> create(@Valid @RequestBody PositionUpsertRequest req) {
        return Result.ok(adminPositionService.create(req));
    }

    /** 用例 #28★：修改岗位（部分更新；skills 非空整体替换能力模型技能）。 */
    @PutMapping("/{id}")
    public Result<PositionAdminVO> update(@PathVariable Long id,
                                          @Valid @RequestBody PositionUpsertRequest req) {
        return Result.ok(adminPositionService.update(id, req));
    }

    /** 用例 #30★：评分权重调整（写 competency_model_json.scoreWeights）。 */
    @PutMapping("/{id}/weights")
    public Result<PositionAdminVO> updateWeights(@PathVariable Long id,
                                                 @Valid @RequestBody WeightsUpdateRequest req) {
        return Result.ok(adminPositionService.updateWeights(id, req));
    }
}
