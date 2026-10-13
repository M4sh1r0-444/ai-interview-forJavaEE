package com.example.interview.common.log;

import com.example.interview.auth.security.CurrentUser;
import com.example.interview.common.result.Result;
import com.example.interview.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/**
 * 管理端操作审计（计划 §7-10）：admin 包 Controller 自动记录
 * 谁/何时/接口/参数摘要 → 独立 audit 日志（SLF4J，不建表，严守 9 表约束）。
 */
@Slf4j(topic = "AUDIT")
@Aspect
@Component
public class AuditLogAspect {

    private static final Collection<String> SENSITIVE_KEYS = java.util.Set.of(
            "password", "token", "apikey", "api_key", "authorization", "secret");

    @Around("execution(* com.example.interview.admin.controller..*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        String method = pjp.getSignature().toShortString();
        Long uid = CurrentUser.currentUserIdOrNull();
        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            log.info("user={} op={} args={} error={} cost={}ms",
                    uid, method, summarizeArgs(pjp.getArgs()), t.getMessage(),
                    System.currentTimeMillis() - start);
            throw t;
        }
        Object code = null;
        if (result instanceof Result<?> r) {
            code = r.getCode();
        }
        log.info("user={} op={} args={} result={} cost={}ms",
                uid, method, summarizeArgs(pjp.getArgs()), code,
                System.currentTimeMillis() - start);
        return result;
    }

    private String summarizeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (Object arg : args) {
            if (sb.length() > 1) {
                sb.append(",");
            }
            if (arg == null) {
                sb.append("null");
            } else if (isSimple(arg)) {
                sb.append(arg);
            } else {
                sb.append(mask(JsonUtils.toJson(arg)));
            }
        }
        return sb.append("]").toString();
    }

    private boolean isSimple(Object o) {
        return o instanceof String || o instanceof Number || o instanceof Boolean
                || o instanceof Enum || o instanceof java.time.temporal.Temporal;
    }

    /** 将敏感字段的值替换为 ***。 */
    private String mask(String json) {
        String out = json;
        for (String key : SENSITIVE_KEYS) {
            out = out.replaceAll("(?i)(\"%s\"\\s*:\\s*)\"[^\"]*\"".formatted(key), "$1\"***\"");
        }
        return out;
    }
}
