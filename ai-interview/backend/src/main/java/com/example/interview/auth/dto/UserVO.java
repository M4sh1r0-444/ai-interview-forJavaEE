package com.example.interview.auth.dto;

import com.example.interview.auth.entity.SysUser;
import lombok.Data;

/** 脱敏视图：不含 passwordHash（计划 §4.3 auth）。 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String realName;
    private String role;
    private String major;
    private Integer status;

    public static UserVO from(SysUser u) {
        UserVO vo = new UserVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setRealName(u.getRealName());
        vo.setRole(u.getRole());
        vo.setMajor(u.getMajor());
        vo.setStatus(u.getStatus());
        return vo;
    }
}
