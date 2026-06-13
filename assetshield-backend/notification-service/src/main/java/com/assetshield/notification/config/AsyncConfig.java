package com.assetshield.notification.config;

import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    /** Ghana wall-clock for season derivation and delivery-day decisions. */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Africa/Accra"));
    }

    /** Dispatch executor: callers never block on FCM (pool of 2). */
    @Bean(name = "dispatchExecutor")
    public Executor dispatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notif-dispatch-");
        executor.initialize();
        return executor;
    }
}
