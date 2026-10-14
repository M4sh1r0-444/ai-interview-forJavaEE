package com.example.interview.common.exception;

import com.example.interview.common.result.Result;
import com.example.interview.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理（计划 §4.3）：BizException→业务码；参数校验→40001；
 * AccessDenied→40301；其余→50001 且不向外泄漏堆栈。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        log.info("业务异常 code={} message={}", e.getResultCode().getCode(), e.getMessage());
        return ResponseEntity.status(e.getResultCode().getHttpStatus())
                .body(Result.fail(e.getResultCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe != null ? fe.getField() + ": " + fe.getDefaultMessage()
                : ResultCode.PARAM_INVALID.getMessage();
        return ResponseEntity.status(ResultCode.PARAM_INVALID.getHttpStatus())
                .body(Result.fail(ResultCode.PARAM_INVALID, msg));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(ResultCode.PARAM_INVALID.getHttpStatus())
                .body(Result.fail(ResultCode.PARAM_INVALID, "请求体格式错误"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(ResultCode.FORBIDDEN.getHttpStatus())
                .body(Result.fail(ResultCode.FORBIDDEN, null));
    }

    /** 音频上传超过 multipart 上限（10MB，计划 §7-5）→ 40001。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUpload(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(ResultCode.PARAM_INVALID.getHttpStatus())
                .body(Result.fail(ResultCode.PARAM_INVALID, "上传文件超过大小限制"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(ResultCode.INTERNAL_ERROR.getHttpStatus())
                .body(Result.fail(ResultCode.INTERNAL_ERROR, null));
    }
}
