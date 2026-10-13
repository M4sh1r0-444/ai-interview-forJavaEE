package com.example.interview.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 静态取当前登录用户（计划 §4.3 auth/security）。 */
public final class CurrentUser {

    private CurrentUser() {
    }

    private static Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    public static Long currentUserIdOrNull() {
        Authentication auth = authentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return user.getUid();
        }
        return null;
    }

    public static Long currentUserId() {
        Long uid = currentUserIdOrNull();
        if (uid == null) {
            throw new IllegalStateException("当前线程无登录上下文");
        }
        return uid;
    }

    public static String currentRoleOrNull() {
        Authentication auth = authentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return user.getRole();
        }
        return null;
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(currentRoleOrNull());
    }
}
