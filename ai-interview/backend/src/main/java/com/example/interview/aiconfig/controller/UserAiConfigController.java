package com.example.interview.aiconfig.controller;

import com.example.interview.aiconfig.dto.AiConfigTestVO;
import com.example.interview.aiconfig.dto.AiConfigVO;
import com.example.interview.aiconfig.dto.SaveAiConfigRequest;
import com.example.interview.aiconfig.service.UserAiConfigService;
import com.example.interview.auth.security.CurrentUser;
import com.example.interview.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 个人中心「AI 服务配置」接口（V9）。保存后即时生效：该用户的面试 AI 调用
 * 优先走其个人端点。鉴权走 SecurityConfig 默认 authenticated()，仅操作本人数据。
 */
@RestController
@RequestMapping("/api/users/me/ai-config")
@RequiredArgsConstructor
public class UserAiConfigController {

    private final UserAiConfigService service;

    /** 查询我的配置（无配置 data=null；api_key 只回掩码）。 */
    @GetMapping
    public Result<AiConfigVO> get() {
        return Result.ok(service.get(CurrentUser.currentUserId()));
    }

    /** 保存/更新（apiKey 留空=保留已保存值；首次保存必须填写）。 */
    @PutMapping
    public Result<AiConfigVO> save(@Valid @RequestBody SaveAiConfigRequest req) {
        return Result.ok(service.save(CurrentUser.currentUserId(), req));
    }

    /** 清除配置，回到平台默认（幂等）。 */
    @DeleteMapping
    public Result<Map<String, Object>> delete() {
        service.delete(CurrentUser.currentUserId());
        return Result.ok(Map.of("deleted", true));
    }

    /** 连通性测试（apiKey 留空则用已保存值；结果不落库）。 */
    @PostMapping("/test")
    public Result<AiConfigTestVO> test(@Valid @RequestBody SaveAiConfigRequest req) {
        return Result.ok(service.test(CurrentUser.currentUserId(), req));
    }
}
