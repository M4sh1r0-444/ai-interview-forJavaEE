package com.example.interview.aiconfig.service;

import com.example.interview.aiconfig.dto.AiConfigVO;
import com.example.interview.aiconfig.dto.SaveAiConfigRequest;
import com.example.interview.aiconfig.entity.UserAiConfig;
import com.example.interview.aiconfig.mapper.UserAiConfigMapper;
import com.example.interview.ai.model.AiEndpoint;
import com.example.interview.common.exception.BizException;
import com.example.interview.common.util.SecretCipher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V9 UserAiConfigService 单测：掩码回显、upsert 语义（空 Key 保留）、
 * URL 规范化与 10011、resolveEndpoint 的三种回退、幂等删除。
 * 连通性测试的 HTTP 路径由 UserAiConfigIT 以不可达地址覆盖。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserAiConfigServiceTest {

    @Mock
    private UserAiConfigMapper mapper;
    @Mock
    private SecretCipher cipher;
    @Mock
    private WebClient aiWebClient;

    @InjectMocks
    private UserAiConfigService service;

    private UserAiConfig row(Long userId, String cipherText) {
        UserAiConfig c = new UserAiConfig();
        c.setId(1L);
        c.setUserId(userId);
        c.setApiUrl("https://api.example.com/v1");
        c.setApiKeyCipher(cipherText);
        c.setModel("qwen-plus");
        return c;
    }

    private SaveAiConfigRequest req(String url, String key, String model) {
        SaveAiConfigRequest r = new SaveAiConfigRequest();
        r.setApiUrl(url);
        r.setApiKey(key);
        r.setModel(model);
        return r;
    }

    // ==================== get：掩码回显 ====================

    @Test
    void getMasksLongKeyAndFullyMasksShortKey() {
        when(mapper.selectOne(any())).thenReturn(row(9L, "v1:xxx"));
        when(cipher.decrypt("v1:xxx")).thenReturn("sk-1234567890abcdef");

        AiConfigVO vo = service.get(9L);
        assertThat(vo.getHasApiKey()).isTrue();
        assertThat(vo.getApiKeyMasked()).isEqualTo("sk-1****cdef");
        assertThat(vo.getApiUrl()).isEqualTo("https://api.example.com/v1");
        assertThat(vo.getModel()).isEqualTo("qwen-plus");

        when(cipher.decrypt("v1:xxx")).thenReturn("short8");
        assertThat(service.get(9L).getApiKeyMasked()).isEqualTo("****");
    }

    @Test
    void getReturnsNullMaskWhenDecryptFails() {
        when(mapper.selectOne(any())).thenReturn(row(9L, "v1:xxx"));
        when(cipher.decrypt("v1:xxx")).thenReturn(null);   // 密钥轮换

        AiConfigVO vo = service.get(9L);
        assertThat(vo.getHasApiKey()).isTrue();
        assertThat(vo.getApiKeyMasked()).isNull();
    }

    // ==================== save：upsert 与校验 ====================

    @Test
    void firstSaveRequiresApiKey() {
        when(mapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.save(9L, req("https://a.com/v1", " ", "m")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("API Key");
        verify(mapper, never()).insert(any(UserAiConfig.class));
    }

    @Test
    void firstSaveInsertsEncryptedKey() {
        when(mapper.selectOne(any())).thenReturn(null);
        when(cipher.encrypt("sk-new")).thenReturn("v1:enc");
        when(cipher.decrypt("v1:enc")).thenReturn("sk-new12345678");
        when(mapper.insert(any(UserAiConfig.class))).thenReturn(1);

        service.save(9L, req(" https://a.com/v1/ ", "sk-new", "qwen-plus"));
        ArgumentCaptor<UserAiConfig> cap = ArgumentCaptor.forClass(UserAiConfig.class);
        verify(mapper).insert(cap.capture());
        assertThat(cap.getValue().getApiUrl()).isEqualTo("https://a.com/v1");  // 去空白与尾斜杠
        assertThat(cap.getValue().getApiKeyCipher()).isEqualTo("v1:enc");
        assertThat(cap.getValue().getModel()).isEqualTo("qwen-plus");
    }

    @Test
    void updateWithBlankKeyKeepsOldCipher() {
        UserAiConfig existing = row(9L, "v1:old");
        when(mapper.selectOne(any())).thenReturn(existing);
        when(cipher.decrypt("v1:old")).thenReturn("sk-old12345678");

        service.save(9L, req("https://b.com/v1", "", "deepseek-chat"));
        ArgumentCaptor<UserAiConfig> cap = ArgumentCaptor.forClass(UserAiConfig.class);
        verify(mapper).updateById(cap.capture());
        assertThat(cap.getValue().getApiKeyCipher()).isEqualTo("v1:old");      // 未换 Key
        assertThat(cap.getValue().getApiUrl()).isEqualTo("https://b.com/v1");
        assertThat(cap.getValue().getModel()).isEqualTo("deepseek-chat");
    }

    @Test
    void updateWithNewKeyEncryptsAgain() {
        when(mapper.selectOne(any())).thenReturn(row(9L, "v1:old"));
        when(cipher.encrypt("sk-new2")).thenReturn("v1:new");
        when(cipher.decrypt("v1:new")).thenReturn("sk-new2123456");

        service.save(9L, req("https://b.com/v1", "sk-new2", "m1"));
        verify(mapper).updateById(any(UserAiConfig.class));
        ArgumentCaptor<UserAiConfig> cap = ArgumentCaptor.forClass(UserAiConfig.class);
        verify(mapper).updateById(cap.capture());
        assertThat(cap.getValue().getApiKeyCipher()).isEqualTo("v1:new");
    }

    @Test
    void rejectsInvalidUrlsWithAiConfigInvalid() {
        when(mapper.selectOne(any())).thenReturn(null);
        for (String bad : new String[]{"ftp://a.com/v1", "notaurl", "https://"}) {
            assertThatThrownBy(() -> service.save(9L, req(bad, "sk-k", "m")))
                    .as(bad)
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("接口地址");
        }
        verify(mapper, never()).insert(any(UserAiConfig.class));
    }

    // ==================== resolveEndpoint：路由解析 ====================

    @Test
    void resolveEndpointReturnsNullWithoutRowOrOnDecryptFailure() {
        when(mapper.selectOne(any())).thenReturn(null);
        assertThat(service.resolveEndpoint(9L)).isNull();
        assertThat(service.resolveEndpoint(null)).isNull();

        when(mapper.selectOne(any())).thenReturn(row(9L, "v1:broken"));
        when(cipher.decrypt("v1:broken")).thenReturn(null);
        assertThat(service.resolveEndpoint(9L)).isNull();
    }

    @Test
    void resolveEndpointBuildsEndpointFromRow() {
        when(mapper.selectOne(any())).thenReturn(row(9L, "v1:ok"));
        when(cipher.decrypt("v1:ok")).thenReturn("sk-real");

        AiEndpoint endpoint = service.resolveEndpoint(9L);
        assertThat(endpoint).isNotNull();
        assertThat(endpoint.apiUrl()).isEqualTo("https://api.example.com/v1");
        assertThat(endpoint.apiKey()).isEqualTo("sk-real");
        assertThat(endpoint.model()).isEqualTo("qwen-plus");
    }

    // ==================== delete：幂等 ====================

    @Test
    void deleteIsIdempotent() {
        when(mapper.selectOne(any())).thenReturn(null);
        service.delete(9L);
        verify(mapper, never()).deleteById(any(Long.class));

        when(mapper.selectOne(any())).thenReturn(row(9L, "v1:x"));
        service.delete(9L);
        verify(mapper).deleteById(1L);
    }
}
