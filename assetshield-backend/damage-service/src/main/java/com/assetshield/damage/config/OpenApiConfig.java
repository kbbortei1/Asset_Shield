package com.assetshield.damage.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI damageServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("AssetShield GH — Damage Service")
                .description("Damage reports, photo evidence with hash verification, "
                        + "GPS before/after pairing and loss calculation.")
                .version("v1"));
    }
}
