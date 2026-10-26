package com.example.interview.resume.service;

import com.example.interview.common.config.ResumeProperties;
import com.example.interview.common.exception.BizException;
import com.example.interview.common.result.ResultCode;
import com.example.interview.position.mapper.PositionMapper;
import com.example.interview.resume.mapper.ResumeAnalysisMapper;
import com.example.interview.resume.mapper.ResumeMapper;
import com.example.interview.resume.storage.ResumeStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 简历计划 §8.1 ResumeQuotaTest（R1-5）：保留上限 / 每日上限边界、校验顺序、
 * 配额可配置。两次 COUNT 依次为"保留计数"与"当日计数"（均已由 @TableLogic 排除逻辑删除）。
 */
@ExtendWith(MockitoExtension.class)
class ResumeQuotaTest {

    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private ResumeAnalysisMapper analysisMapper;
    @Mock
    private PositionMapper positionMapper;
    @Mock
    private ResumeStorageService storage;
    @Mock
    private ResumeTextExtractor extractor;
    @Mock
    private ResumeAnalysisService analysisService;

    private ResumeProperties properties;
    private ResumeService service;

    @BeforeEach
    void setUp() {
        properties = new ResumeProperties();
        properties.setMaxCountPerUser(20);
        properties.setDailyUploadLimit(10);
        service = new ResumeService(resumeMapper, analysisMapper, positionMapper, storage,
                extractor, properties, analysisService);
    }

    @Test
    void belowBothLimitsPasses() {
        when(resumeMapper.selectCount(any())).thenReturn(19L, 9L);
        assertThatCode(() -> service.checkQuota(1L)).doesNotThrowAnyException();
        verify(resumeMapper, times(2)).selectCount(any());
    }

    @Test
    void retainedAtLimitRejectedBeforeDailyCheck() {
        when(resumeMapper.selectCount(any())).thenReturn(20L);
        assertQuotaExceeded(() -> service.checkQuota(1L), "保留数量");
        // 保留上限先判，命中后不再查当日计数
        verify(resumeMapper, times(1)).selectCount(any());
    }

    @Test
    void dailyAtLimitRejected() {
        when(resumeMapper.selectCount(any())).thenReturn(5L, 10L);
        assertQuotaExceeded(() -> service.checkQuota(1L), "今日上传");
    }

    @Test
    void dailyJustBelowLimitPasses() {
        when(resumeMapper.selectCount(any())).thenReturn(19L, 9L);
        assertThatCode(() -> service.checkQuota(1L)).doesNotThrowAnyException();
    }

    @Test
    void limitsAreConfigurable() {
        properties.setMaxCountPerUser(3);
        properties.setDailyUploadLimit(2);
        when(resumeMapper.selectCount(any())).thenReturn(2L, 2L);
        assertQuotaExceeded(() -> service.checkQuota(7L), "2 次");

        when(resumeMapper.selectCount(any())).thenReturn(3L);
        assertQuotaExceeded(() -> service.checkQuota(7L), "3 份");
    }

    private static void assertQuotaExceeded(Runnable action, String messagePart) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BizException.class)
                .satisfies(e -> {
                    BizException biz = (BizException) e;
                    assertThat(biz.getResultCode()).isEqualTo(ResultCode.RESUME_QUOTA_EXCEEDED);
                    assertThat(biz.getMessage()).contains(messagePart);
                });
    }
}
