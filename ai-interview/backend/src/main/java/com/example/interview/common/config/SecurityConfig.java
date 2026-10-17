package com.example.interview.common.config;

import com.example.interview.auth.security.JwtAuthenticationFilter;
import com.example.interview.common.result.Result;
import com.example.interview.common.result.ResultCode;
import com.example.interview.common.util.JsonUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 无状态安全配置（计划 §7-3）：禁 CSRF、STATELESS；
 * 注册/登录放行；/api/admin/** 需 ADMIN；其余 authenticated。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /** HTTP CORS 白名单（FIX-04 配置化）：开发 5173；prod/Compose 需含 Nginx 80 入口两源 */
    @Value("${app.security.cors-allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private List<String> corsAllowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // WS 握手不带 Authorization 头，Handler 内 query token 自鉴权（计划 §7-3）
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) -> writeJson(res, ResultCode.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, ex) -> writeJson(res, ResultCode.FORBIDDEN)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * HTTP CORS（FIX-04 配置化）：只作用于 /api/**——WS 握手（带 Origin 的 GET）由
     * WebSocketConfig 的 allowed-origins 独立管控，不应被 HTTP CORS 过滤器拦截
     * （否则 WS 白名单放行的源会被 CorsFilter 提前 403）。开发默认 5173；prod 含 Nginx 80 入口两源。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(corsAllowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private void writeJson(jakarta.servlet.http.HttpServletResponse res, ResultCode rc) throws java.io.IOException {
        res.setStatus(rc.getHttpStatus());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(JsonUtils.toJson(Result.fail(rc, null)));
    }
}
