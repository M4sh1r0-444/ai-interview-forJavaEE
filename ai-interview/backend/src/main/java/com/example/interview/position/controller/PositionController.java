package com.example.interview.position.controller;

import com.example.interview.common.result.Result;
import com.example.interview.position.dto.PositionVO;
import com.example.interview.position.dto.SkillVO;
import com.example.interview.position.service.PositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/positions")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    /** 用例 6：岗位列表（enabled only）。 */
    @GetMapping
    public Result<List<PositionVO>> list() {
        return Result.ok(positionService.listEnabled());
    }

    /** 用例 7：岗位详情。 */
    @GetMapping("/{id}")
    public Result<PositionVO> detail(@PathVariable Long id) {
        return Result.ok(positionService.detail(id));
    }

    /** 用例 8：技能清单。 */
    @GetMapping("/{id}/skills")
    public Result<List<SkillVO>> skills(@PathVariable Long id) {
        return Result.ok(positionService.skills(id));
    }
}
