package com.assetshield.property.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI propertyServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("AssetShield GH — Property Service")
                .description("Properties, evidence assets with photo hash verification, "
                        + "receipts, household sharing and the marketplace opt-in flag.")
                .version("v1"));
    }
}
