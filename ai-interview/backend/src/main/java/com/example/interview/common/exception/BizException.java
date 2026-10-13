package com.example.interview.common.exception;

import com.example.interview.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常，携带统一错误码。
 */
@Getter
public class BizException extends RuntimeException {

    private final ResultCode resultCode;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    /** 允许覆盖默认文案（如带字段名的参数错误信息）。 */
    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }
}
