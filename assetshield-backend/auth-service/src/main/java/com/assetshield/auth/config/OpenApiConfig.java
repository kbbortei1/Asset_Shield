package com.assetshield.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI authServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("AssetShield GH — Auth Service")
                .description("Registration, OTP verification, login, JWT issuance, "
                        + "refresh rotation and admin management.")
                .version("v1"));
    }
}
