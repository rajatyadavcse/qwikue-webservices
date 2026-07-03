package com.microservice.LoginService.config;

import com.microservice.LoginService.security.JwtFilter;
import com.microservice.LoginService.security.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private JwtFilter jwtFilter;

    @Value("${app.security.public-urls:}")
    private String[] dynamicPublicUrls;

    private static final String[] PUBLIC_URLS = {
            "/auth/login",
            "/auth/refresh",
            "/auth/verify-email",
            "/auth/resend-verification-otp",
            "/auth/forgot-password",
            "/auth/reset-password-otp",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml"
    };

    /**
     * Completely bypass the Security filter chain for Swagger / OpenAPI paths.
     * Unlike permitAll(), ignoring() means no security filters run at all —
     * the safest way to expose documentation endpoints.
     */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring().requestMatchers(
                new AntPathRequestMatcher("/swagger-ui/**"),
                new AntPathRequestMatcher("/swagger-ui.html"),
                new AntPathRequestMatcher("/v3/api-docs"),
                new AntPathRequestMatcher("/v3/api-docs/**"),
                new AntPathRequestMatcher("/v3/api-docs.yaml")
        );
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(PUBLIC_URLS).permitAll();
                    if (dynamicPublicUrls != null && dynamicPublicUrls.length > 0) {
                        for (String url : dynamicPublicUrls) {
                            if (url != null && !url.trim().isEmpty()) {
                                String trimmedUrl = url.trim();
                                if (trimmedUrl.contains(":")) {
                                    int colonIndex = trimmedUrl.indexOf(":");
                                    String methodStr = trimmedUrl.substring(0, colonIndex).toUpperCase();
                                    String pathPattern = trimmedUrl.substring(colonIndex + 1);
                                    try {
                                        org.springframework.http.HttpMethod method = org.springframework.http.HttpMethod.valueOf(methodStr);
                                        auth.requestMatchers(method, pathPattern).permitAll();
                                    } catch (IllegalArgumentException e) {
                                        // Fallback if not a valid HTTP method
                                        auth.requestMatchers(trimmedUrl).permitAll();
                                    }
                                } else {
                                    auth.requestMatchers(trimmedUrl).permitAll();
                                }
                            }
                        }
                    }
                    auth.anyRequest().authenticated();
                })
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
