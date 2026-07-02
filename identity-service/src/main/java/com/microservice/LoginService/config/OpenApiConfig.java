package com.microservice.LoginService.config;

import jakarta.annotation.PostConstruct;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;

import java.util.ArrayList;
import java.util.List;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Identity Service API",
        version = "1.0",
        description = "Authentication & User Management API for the Restaurant Microservices platform",
        contact = @Contact(name = "Identity Service Team")))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Paste your access token here (without the 'Bearer ' prefix)")
public class OpenApiConfig {

    /**
     * Set per profile in application-prod.properties / application-local.properties.
     * No platform-specific env vars needed.
     */
    @Value("${app.base-url:}")
    private String baseUrl;

    @Value("${server.port:8080}")
    private String serverPort;

    /**
     * Tell SpringDoc to skip @AuthenticationPrincipal parameters.
     * Without this, SpringDoc tries to document the injected UserPrincipal
     * as a request parameter and throws a 500 on /v3/api-docs.
     */
    @PostConstruct
    public void configureSpringDoc() {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(AuthenticationPrincipal.class);
    }

    @Bean
    public OpenAPI customOpenAPI() {
        List<Server> servers = new ArrayList<>();

        // Production server URL — driven by app.base-url in the active profile
        if (baseUrl != null && !baseUrl.isBlank()) {
            servers.add(new Server()
                    .url(baseUrl)
                    .description("Production"));
        }

        // Always include localhost for local development convenience
        servers.add(new Server()
                .url("http://localhost:" + serverPort)
                .description("Local Development"));

        return new OpenAPI().servers(servers);
    }
}
