package com.kitchen.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${app.base-url:}")
    private String baseUrl;

    @Value("${RAILWAY_PUBLIC_DOMAIN:}")
    private String railwayDomain;

    @Value("${server.port:8084}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        List<Server> servers = new ArrayList<>();

        // Priority 1: Explicit base URL from app.base-url config
        if (baseUrl != null && !baseUrl.isBlank()) {
            servers.add(new Server().url(baseUrl).description("Production"));
        }
        // Priority 2: Auto-detect from Railway's built-in RAILWAY_PUBLIC_DOMAIN env var
        else if (railwayDomain != null && !railwayDomain.isBlank()) {
            servers.add(new Server().url("https://" + railwayDomain).description("Production"));
        }

        // Always add localhost for local development
        servers.add(new Server().url("http://localhost:" + serverPort).description("Local Development"));

        return new OpenAPI()
                .info(new Info()
                        .title("Order Service API")
                        .description("RESTful API for managing orders in the kitchen-ordering system. " +
                                "Supports order creation, status tracking, and kitchen dashboard feed.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Order Service Team")
                                .email("support@order-service.com")))
                .servers(servers);
    }
}
