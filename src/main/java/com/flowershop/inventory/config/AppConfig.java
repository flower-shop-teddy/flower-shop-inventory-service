package com.flowershop.inventory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowershop.common.client.ServiceClientFactory;
import com.flowershop.common.security.JwtAuditorAware;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class AppConfig {

    /** created_by / updated_by = sub của người (hoặc service) đang gọi API, không có thì "system". */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return new JwtAuditorAware();
    }

    /** Lấy giờ qua Clock thay vì Instant.now(): test có thể cố định thời gian. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** @LoadBalanced: URL dạng http://catalog-service được tra trên Eureka. */
    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public ServiceClientFactory serviceClientFactory(RestClient.Builder loadBalancedRestClientBuilder,
                                                     ObjectMapper objectMapper) {
        return new ServiceClientFactory(loadBalancedRestClientBuilder, objectMapper);
    }
}
