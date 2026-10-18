package com.example.interview.common.exception;

import com.example.interview.common.result.Result;
import com.example.interview.common.result.ResultCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理（计划 §4.3）：BizException→业务码；参数校验→40001；
 * AccessDenied→40301；协议级错误（FIX-11）→400/404/405/415 对应 code；
 * 其余→50001 且不向外泄漏堆栈。
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

    /** @Validated 控制器方法参数约束（Spring 6.1+ 内建方法校验，如分页 @Min/@Max）。 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleMethodValidation(HandlerMethodValidationException e) {
        return ResponseEntity.status(ResultCode.PARAM_INVALID.getHttpStatus())
                .body(Result.fail(ResultCode.PARAM_INVALID, "请求参数校验失败：page 取值 1 起、size 取值 1~100"));
    }

    /** @Validated 服务方法约束（AOP 代理路径）→ 400/40001。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
        return ResponseEntity.status(ResultCode.PARAM_INVALID.getHttpStatus())
                .body(Result.fail(ResultCode.PARAM_INVALID, "请求参数校验失败"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(ResultCode.PARAM_INVALID.getHttpStatus())
                .body(Result.fail(ResultCode.PARAM_INVALID, "请求体格式错误"));
    }

    /** 路径/查询参数类型不匹配（如 /api/interviews/abc）→ 400/40001。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(ResultCode.PARAM_INVALID.getHttpStatus())
                .body(Result.fail(ResultCode.PARAM_INVALID, "参数类型错误：" + e.getName()));
    }

    /** 缺必填请求参数 → 400/40001。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.status(ResultCode.PARAM_INVALID.getHttpStatus())
                .body(Result.fail(ResultCode.PARAM_INVALID, "缺少必填参数：" + e.getParameterName()));
    }

    /** multipart 请求缺必需部分（如音频 file）→ 400/40001。 */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Result<Void>> handleMissingPart(MissingServletRequestPartException e) {
        return ResponseEntity.status(ResultCode.PARAM_INVALID.getHttpStatus())
                .body(Result.fail(ResultCode.PARAM_INVALID, "缺少必需的文件部分：" + e.getRequestPartName()));
    }

    /** multipart 请求体本身非法（未按 multipart 编码等）→ 400/40001。 */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Result<Void>> handleMultipart(MultipartException e) {
        return ResponseEntity.status(ResultCode.PARAM_INVALID.getHttpStatus())
                .body(Result.fail(ResultCode.PARAM_INVALID, "multipart 请求格式错误"));
    }

    /** 请求方法不支持 → 405/40501。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(ResultCode.METHOD_NOT_ALLOWED.getHttpStatus())
                .body(Result.fail(ResultCode.METHOD_NOT_ALLOWED, null));
    }

    /** 静态资源/未知路径（Spring 6.1 NoResourceFoundException）→ 404/40400 JSON。
     * 注意与 NOT_FOUND_OR_FORBIDDEN(40401, HTTP 200) 区分：后者用于业务资源归属隐藏。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(ResultCode.NOT_FOUND.getHttpStatus())
                .body(Result.fail(ResultCode.NOT_FOUND, null));
    }

    /** Content-Type 不支持 → 415/41501。 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(ResultCode.UNSUPPORTED_MEDIA_TYPE.getHttpStatus())
                .body(Result.fail(ResultCode.UNSUPPORTED_MEDIA_TYPE, null));
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
