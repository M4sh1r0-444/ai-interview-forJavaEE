package com.example.interview.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 仅允许修改 realName / major，不允许改 username / role（计划 §5.1-5）。 */
@Data
public class UserUpdateRequest {

    @Size(max = 50, message = "真实姓名不能超过 50 字")
    private String realName;

    @Size(max = 100, message = "专业不能超过 100 字")
    private String major;
}
