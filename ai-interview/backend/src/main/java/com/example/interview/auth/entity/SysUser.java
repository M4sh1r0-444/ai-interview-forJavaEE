package com.example.interview.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表 1：sys_user（方案 §9.1）。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String realName;
    /** STUDENT / ADMIN */
    private String role;
    private String major;
    /** 1 启用 0 禁用 */
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
