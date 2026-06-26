package com.bookingsystem.config;

import java.time.Duration;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configures the RestTemplate used by LlmClient to call the LLM provider.
 *
 * Both connect and read timeouts are set to 5 seconds. This is the actual
 * mechanism that guarantees a slow or hanging LLM API can never block a
 * request for more than 5 seconds — without this, RestTemplate's default
 * timeout is effectively "wait forever," which would be exactly the kind
 * of risk this project is designed to avoid.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }
}
