package com.qwikue.app.config;

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
        title = "Qwikue Web Services API",
        version = "1.0",
        description = "Consolidated Services API for Qwikue platform",
        contact = @Contact(name = "Qwikue Engineering Team")))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Paste your access token here (without the 'Bearer ' prefix)")
public class OpenApiConfig {

    @Value("${app.base-url:}")
    private String baseUrl;

    @Value("${server.port:8080}")
    private String serverPort;

    @PostConstruct
    public void configureSpringDoc() {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(AuthenticationPrincipal.class);
    }

    @Bean
    public OpenAPI customOpenAPI() {
        List<Server> servers = new ArrayList<>();

        if (baseUrl != null && !baseUrl.isBlank()) {
            servers.add(new Server()
                    .url(baseUrl)
                    .description("Production"));
        }

        servers.add(new Server()
                .url("http://localhost:" + serverPort)
                .description("Local Development"));

        return new OpenAPI().servers(servers);
    }
}
