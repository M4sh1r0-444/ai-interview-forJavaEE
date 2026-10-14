package com.example.interview.interview.controller;

import com.example.interview.assessment.dto.ReportVO;
import com.example.interview.assessment.service.ReportService;
import com.example.interview.common.result.Result;
import com.example.interview.interview.dto.CreateInterviewRequest;
import com.example.interview.interview.dto.InterviewDetailVO;
import com.example.interview.interview.dto.MessageVO;
import com.example.interview.interview.dto.TextAnswerRequest;
import com.example.interview.interview.service.InterviewService;
import com.example.interview.interview.service.TurnEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 面试核心接口（计划 §5.3）。M2：创建/开始/文字回答/中止/详情/消息；
 * M3：finish（#13）/report（#17）；M4：音频回答（#12）与受保护音频回放。
 */
@Slf4j
@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;
    private final TurnEngine turnEngine;
    private final ReportService reportService;

    /** 用例 9：创建会话。 */
    @PostMapping
    public Result<InterviewDetailVO> create(@Valid @RequestBody CreateInterviewRequest req) {
        return Result.ok(interviewService.create(req));
    }

    /** 用例 10：开始面试，返回首题；同时 WS 推 QUESTION。 */
    @PostMapping("/{id}/start")
    public Result<MessageVO> start(@PathVariable Long id) {
        return Result.ok(interviewService.start(id));
    }

    /**
     * 用例 11：提交文字回答。先受理（{accepted:true}），AI 产出异步落库并经 WS 推送
     * （计划 §5.3/§4.4）；提交事务已提交后才触发异步编排，避免读到未提交数据。
     */
    @PostMapping("/{id}/messages/text")
    public Result<Map<String, Object>> submitText(@PathVariable Long id,
                                                  @Valid @RequestBody TextAnswerRequest req) {
        Map<String, Object> resp = interviewService.submitTextAnswer(id, req);
        try {
            turnEngine.processTurn(id);
        } catch (Exception e) {
            // 线程池拒绝等极端情况：会话停在 PROCESSING_AI，由定时器 TIMEOUT 兜底
            log.error("触发 AI 编排失败 session={}", id, e);
        }
        return Result.ok(resp);
    }

    /**
     * 用例 12：提交音频回答（multipart：file + roundNo + asrText + durationMs + pauseCount?）。
     * 转写由前端 Web Speech API 完成后随表单上送；受理契约与文字一致，
     * AI 产出异步落库并经 WS 推送；受理成功后另推 TRANSCRIPT 回显确认。
     */
    @PostMapping("/{id}/messages/audio")
    public Result<Map<String, Object>> submitAudio(@PathVariable Long id,
                                                   @RequestParam("file") MultipartFile file,
                                                   @RequestParam("roundNo") int roundNo,
                                                   @RequestParam("asrText") String asrText,
                                                   @RequestParam("durationMs") Integer durationMs,
                                                   @RequestParam(value = "pauseCount", required = false) Integer pauseCount) {
        Map<String, Object> resp = interviewService.submitAudioAnswer(
                id, file, roundNo, asrText, durationMs, pauseCount);
        try {
            turnEngine.processTurn(id);
        } catch (Exception e) {
            // 线程池拒绝等极端情况：会话停在 PROCESSING_AI，由定时器 TIMEOUT 兜底
            log.error("触发 AI 编排失败 session={}", id, e);
        }
        return Result.ok(resp);
    }

    /** 用例 12b：受保护音频回放（归属本人/ADMIN，计划 §6.9 可选项）。 */
    @GetMapping("/{id}/messages/{messageId}/audio")
    public ResponseEntity<FileSystemResource> audio(@PathVariable Long id,
                                                    @PathVariable Long messageId) {
        InterviewService.AudioResource resource = interviewService.loadAudio(id, messageId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, resource.contentType())
                .body(new FileSystemResource(resource.path()));
    }

    /** 用例 13：结束面试。受理即返，报告异步生成，就绪经 WS REPORT_READY 通知；重试入口同此接口。 */
    @PostMapping("/{id}/finish")
    public Result<Map<String, Object>> finish(@PathVariable Long id) {
        return Result.ok(interviewService.finish(id));
    }

    /** 用例 14：中止面试（非终态均可）。 */
    @PostMapping("/{id}/abort")
    public Result<InterviewDetailVO> abort(@PathVariable Long id) {
        return Result.ok(interviewService.abort(id));
    }

    /** 用例 15：会话详情（归属本人/ADMIN）。 */
    @GetMapping("/{id}")
    public Result<InterviewDetailVO> detail(@PathVariable Long id) {
        return Result.ok(interviewService.detail(id));
    }

    /** 用例 16：消息列表（?roundNo= 可选，按时间序）。 */
    @GetMapping("/{id}/messages")
    public Result<List<MessageVO>> messages(@PathVariable Long id,
                                            @RequestParam(required = false) Integer roundNo) {
        return Result.ok(interviewService.messages(id, roundNo));
    }

    /** 用例 17：评估报告（归属本人/ADMIN）；未生成返回 10004。 */
    @GetMapping("/{id}/report")
    public Result<ReportVO> report(@PathVariable Long id) {
        return Result.ok(reportService.getReport(id));
    }
}
