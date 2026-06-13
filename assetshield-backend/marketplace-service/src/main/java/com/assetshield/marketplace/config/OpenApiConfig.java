package com.assetshield.marketplace.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI marketplaceServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("AssetShield GH — Marketplace Service")
                .description("Payments (Paystack/MoMo) with webhook settlement. "
                        + "Agents, leads and subscriptions arrive Day 5.")
                .version("v1"));
    }
}
