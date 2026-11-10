package com.flowershop.inventory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowershop.common.security.ApiSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Lớp 1 (URL, chạy TRƯỚC khi validate body): ai được gọi nhóm API nào.
 * Lớp 2 (@PreAuthorize ở controller): kiểm tra lại permission từng API.
 * Tự kiểm tra JWT dù gateway đã kiểm tra (zero trust).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        ApiSecurity.configure(http, objectMapper);
        http.authorizeHttpRequests(auth -> auth
                // Khách xem còn hàng hay không: public, không lộ số lượng
                .requestMatchers(HttpMethod.GET, "/api/v1/inventory/availability").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/inventory/**").hasAuthority("inventory:read")
                .requestMatchers("/api/v1/inventory/**").hasAuthority("inventory:write")
                // Giữ/nhả hàng: chỉ service có inventory:reserve (order-service). Gateway không route /internal/**
                .requestMatchers("/internal/v1/reservations/**", "/internal/v1/reservations").hasAuthority("inventory:reserve")
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated());
        return http.build();
    }
}
