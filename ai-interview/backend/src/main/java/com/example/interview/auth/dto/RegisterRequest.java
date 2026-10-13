package com.example.interview.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 50, message = "用户名长度需在 4~50 之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6~32 之间")
    private String password;

    @Size(max = 50, message = "真实姓名不能超过 50 字")
    private String realName;

    @Size(max = 100, message = "专业不能超过 100 字")
    private String major;
}
