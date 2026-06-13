package com.assetshield.notification.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("AssetShield GH — Notification & Tips Service")
                .description("FCM push dispatch, Ghana-specific AI safety tips, "
                        + "scheduled reminders and the in-app notification history.")
                .version("v1"));
    }
}
