package com.example.interview.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {

    private String token;
    /** 过期时间（epoch 毫秒） */
    private Long expiresAt;
    private UserVO user;
}
