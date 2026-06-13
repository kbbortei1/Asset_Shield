package com.assetshield.damage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Dedicated executor for dossier PDF generation (heap is 256 MB — keep it small). */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("dossierExecutor")
    public ThreadPoolTaskExecutor dossierExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("dossier-");
        executor.initialize();
        return executor;
    }
}
