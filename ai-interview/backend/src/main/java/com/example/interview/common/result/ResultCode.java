package com.example.interview.common.result;

import lombok.Getter;

/**
 * 统一错误码（计划附录A）。业务失败统一 HTTP 200 + code 区分，
 * 仅协议级错误（401/403/400/429/500）使用对应 HTTP 状态码。
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "成功", 200),
    USERNAME_EXISTS(10001, "用户名已存在", 200),
    USERNAME_OR_PASSWORD_ERROR(10002, "用户名或密码错误", 200),
    ACCOUNT_DISABLED(10003, "账号已禁用", 200),
    REPORT_NOT_READY(10004, "报告尚未生成", 200),
    STATUS_CONFLICT(10005, "面试状态冲突", 200),
    PARAM_INVALID(40001, "参数校验失败", 400),
    PARAM_OUT_OF_RANGE(40002, "参数超出允许范围", 400),
    VERSION_CONFLICT(40003, "数据已被修改，请刷新后重试", 200),
    NOT_FOUND(40400, "请求的资源不存在", 404),
    NOT_FOUND_OR_FORBIDDEN(40401, "资源不存在或无权访问", 200),
    UNAUTHORIZED(40101, "未登录或登录已失效", 401),
    FORBIDDEN(40301, "无权限执行该操作", 403),
    METHOD_NOT_ALLOWED(40501, "请求方法不支持", 405),
    UNSUPPORTED_MEDIA_TYPE(41501, "Content-Type 不支持", 415),
    TOO_MANY_REQUESTS(42901, "请求过于频繁，请稍后再试", 429),
    INTERNAL_ERROR(50001, "服务器内部错误", 500);

    private final int code;
    private final String message;
    private final int httpStatus;

    ResultCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
