package com.example.interview.aiconfig.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.aiconfig.dto.AiConfigTestVO;
import com.example.interview.aiconfig.dto.AiConfigVO;
import com.example.interview.aiconfig.dto.SaveAiConfigRequest;
import com.example.interview.aiconfig.entity.UserAiConfig;
import com.example.interview.aiconfig.mapper.UserAiConfigMapper;
import com.example.interview.ai.model.AiEndpoint;
import com.example.interview.common.exception.BizException;
import com.example.interview.common.result.ResultCode;
import com.example.interview.common.util.SecretCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 用户级 AI 服务配置（V9，个人中心「AI 服务配置」子页面）。每用户至多一条
 * （uk_user_ai_config），存在即生效：RoutingAiProvider 经 {@link #resolveEndpoint}
 * 取端点，无配置或解密失败返回 null → 走平台默认。api_key 只存 AES-GCM 密文，
 * 出参仅回掩码；测试连通性用一条 max_tokens=16 的极小请求，8s 超时，不落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAiConfigService {

    /** 连通性测试总超时（覆盖 WebClient 自身的 30s 响应超时） */
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(8);
    private static final String TEST_PROMPT = "连通性测试，请只回复 ok";

    private final UserAiConfigMapper mapper;
    private final SecretCipher cipher;
    private final WebClient aiWebClient;

    /** 我的配置回显；无配置返回 null。 */
    public AiConfigVO get(Long userId) {
        UserAiConfig c = findByUserId(userId);
        if (c == null) {
            return null;
        }
        return AiConfigVO.from(c, cipher.decrypt(c.getApiKeyCipher()));
    }

    /** 保存/更新（upsert）。apiKey 留空 = 保留旧密文；首次保存必须填写。 */
    @Transactional
    public AiConfigVO save(Long userId, SaveAiConfigRequest req) {
        String apiUrl = normalizeUrl(req.getApiUrl());
        String model = req.getModel().trim();
        UserAiConfig c = findByUserId(userId);
        if (c == null) {
            if (isBlank(req.getApiKey())) {
                throw new BizException(ResultCode.AI_CONFIG_INVALID, "首次保存需填写 API Key");
            }
            c = new UserAiConfig();
            c.setUserId(userId);
            c.setApiUrl(apiUrl);
            c.setModel(model);
            c.setApiKeyCipher(cipher.encrypt(req.getApiKey().trim()));
            mapper.insert(c);
            log.info("用户 AI 配置已创建 userId={}", userId);
        } else {
            c.setApiUrl(apiUrl);
            c.setModel(model);
            if (!isBlank(req.getApiKey())) {
                c.setApiKeyCipher(cipher.encrypt(req.getApiKey().trim()));
            }
            mapper.updateById(c);
            log.info("用户 AI 配置已更新 userId={}（apiKey={}）", userId,
                    isBlank(req.getApiKey()) ? "保留" : "更换");
        }
        return get(userId);
    }

    /** 清除配置（回到平台默认；无配置时幂等成功）。 */
    @Transactional
    public void delete(Long userId) {
        UserAiConfig c = findByUserId(userId);
        if (c != null) {
            mapper.deleteById(c.getId());
            log.info("用户 AI 配置已清除 userId={}", userId);
        }
    }

    /**
     * 面试链路取端点：无配置 / 解密失败（密钥轮换）均返回 null，
     * 由 RoutingAiProvider 回退平台默认，不阻断面试。
     */
    public AiEndpoint resolveEndpoint(Long userId) {
        if (userId == null) {
            return null;
        }
        UserAiConfig c = findByUserId(userId);
        if (c == null) {
            return null;
        }
        String key = cipher.decrypt(c.getApiKeyCipher());
        if (key == null) {
            log.warn("用户 {} 的 AI 配置密文解密失败，回退平台默认", userId);
            return null;
        }
        return new AiEndpoint(c.getApiUrl(), key, c.getModel());
    }

    /**
     * 连通性测试：用表单值（apiKey 留空则用已保存值）向 {apiUrl}/chat/completions
     * 发一条极小请求。任何失败都以 ok=false + 原因摘要返回，不抛业务异常。
     */
    public AiConfigTestVO test(Long userId, SaveAiConfigRequest req) {
        String apiUrl = normalizeUrl(req.getApiUrl());
        String model = req.getModel().trim();
        String apiKey = resolveTestKey(userId, req.getApiKey());
        long start = System.nanoTime();
        try {
            aiWebClient.post()
                    .uri(apiUrl + "/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "model", model,
                            "messages", java.util.List.of(Map.of("role", "user", "content", TEST_PROMPT)),
                            "temperature", 0,
                            "max_tokens", 16))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(TEST_TIMEOUT);
            long ms = (System.nanoTime() - start) / 1_000_000;
            return AiConfigTestVO.builder().ok(true).message("连通正常").latencyMs(ms).build();
        } catch (Exception e) {
            return AiConfigTestVO.builder().ok(false).message(describeFailure(e)).build();
        }
    }

    /** 测试用 Key：表单优先，留空回退已保存配置（无保存则视为配置不合法）。 */
    private String resolveTestKey(Long userId, String formKey) {
        if (!isBlank(formKey)) {
            return formKey.trim();
        }
        AiEndpoint saved = resolveEndpoint(userId);
        if (saved == null) {
            throw new BizException(ResultCode.AI_CONFIG_INVALID, "未填写 API Key 且无已保存配置");
        }
        return saved.apiKey();
    }

    /** 失败原因摘要（可直接展示给用户；不携带 Key）。 */
    private String describeFailure(Exception e) {
        if (e instanceof WebClientResponseException w) {
            return switch (w.getStatusCode().value()) {
                case 401, 403 -> "API Key 无效或无权限（HTTP " + w.getStatusCode().value() + "）";
                case 404 -> "接口地址不正确（HTTP 404，请确认是否为 OpenAI 兼容根地址）";
                default -> "服务返回 HTTP " + w.getStatusCode().value();
            };
        }
        if (e.getCause() instanceof TimeoutException || e instanceof TimeoutException) {
            return "请求超时（" + TEST_TIMEOUT.toSeconds() + "s），请检查网络或接口地址";
        }
        String msg = e.getMessage();
        if (msg != null && msg.length() > 200) {
            msg = msg.substring(0, 200) + "…";
        }
        return msg != null ? "连接失败：" + msg : "连接失败";
    }

    /** 校验并规范化接口地址：http/https + 含主机名，去尾部斜杠（避免拼接出 //）。 */
    private String normalizeUrl(String raw) {
        String url = raw == null ? "" : raw.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new BizException(ResultCode.AI_CONFIG_INVALID, "接口地址格式不正确");
        }
        String scheme = uri.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new BizException(ResultCode.AI_CONFIG_INVALID, "接口地址必须以 http(s):// 开头");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BizException(ResultCode.AI_CONFIG_INVALID, "接口地址缺少主机名");
        }
        return url;
    }

    private UserAiConfig findByUserId(Long userId) {
        return mapper.selectOne(Wrappers.<UserAiConfig>lambdaQuery()
                .eq(UserAiConfig::getUserId, userId));
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
