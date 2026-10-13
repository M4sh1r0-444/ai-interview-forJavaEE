package com.example.interview.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步与调度（计划 §4.4）：AI 调用走专用线程池 aiExecutor（核心 4 最大 8），
 * 避免 HTTP 请求线程挂 30s；@EnableScheduling 供定时器/心跳使用。
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean("aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-");
        executor.initialize();
        return executor;
    }
}
