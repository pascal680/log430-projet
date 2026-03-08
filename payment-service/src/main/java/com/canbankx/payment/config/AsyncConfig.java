package com.canbankx.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables Spring @Async and configures a dedicated thread pool for
 * fire-and-forget email confirmations so payment request threads are
 * never blocked by mail I/O.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(3);      // caps concurrent email HTTP calls at 3
        executor.setQueueCapacity(500);  // back-pressure queue for bursts
        executor.setThreadNamePrefix("email-async-");
        executor.setKeepAliveSeconds(60);
        executor.initialize();
        return executor;
    }
}
