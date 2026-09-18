package com.kitchen.order.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiLoggingFilter.class);

    private static final String MDC_HTTP_METHOD = "httpMethod";
    private static final String MDC_REQUEST_URI = "requestUri";
    private static final String MDC_IDEMPOTENCY_KEY = "idempotencyKey";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullPath = (queryString != null && !queryString.isBlank()) ? uri + "?" + queryString : uri;
        String idempotencyKey = request.getHeader("X-Idempotency-Key");

        MDC.put(MDC_HTTP_METHOD, method);
        MDC.put(MDC_REQUEST_URI, fullPath);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            MDC.put(MDC_IDEMPOTENCY_KEY, idempotencyKey.trim());
        }

        log.info("[API_REQUEST] {} {} | IdempotencyKey: {}", method, fullPath, 
                (idempotencyKey != null ? idempotencyKey.trim() : "NONE"));

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();
            log.info("[API_RESPONSE] {} {} - Status: {} ({} ms)", method, fullPath, status, duration);

            MDC.remove(MDC_HTTP_METHOD);
            MDC.remove(MDC_REQUEST_URI);
            MDC.remove(MDC_IDEMPOTENCY_KEY);
        }
    }
}
