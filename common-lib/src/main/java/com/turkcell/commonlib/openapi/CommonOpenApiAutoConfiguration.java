package com.turkcell.commonlib.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
public class CommonOpenApiAutoConfiguration {

    private static final String BEARER_JWT = "bearer-jwt";

    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    public OpenAPI telcoOpenApi(@Value("${spring.application.name:telco-service}") String applicationName) {
        return new OpenAPI()
                .info(new Info()
                        .title(toDisplayName(applicationName) + " API")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_JWT, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_JWT));
    }

    private static String toDisplayName(String applicationName) {
        String[] parts = applicationName.split("[-_ ]+");
        StringBuilder displayName = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!displayName.isEmpty()) {
                displayName.append(' ');
            }
            displayName.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                displayName.append(part.substring(1));
            }
        }
        return displayName.isEmpty() ? "Telco Service" : displayName.toString();
    }
}
