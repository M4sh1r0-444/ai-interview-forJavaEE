package com.example.interview.common.result;

import com.example.interview.common.util.TraceIdFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.slf4j.MDC;

/**
 * 统一响应结构：{code, message, data, traceId}（方案 §10.6）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = ResultCode.SUCCESS.getCode();
        r.message = ResultCode.SUCCESS.getMessage();
        r.data = data;
        r.traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ResultCode rc, String message) {
        Result<T> r = new Result<>();
        r.code = rc.getCode();
        r.message = message != null ? message : rc.getMessage();
        r.traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
        return r;
    }
}
