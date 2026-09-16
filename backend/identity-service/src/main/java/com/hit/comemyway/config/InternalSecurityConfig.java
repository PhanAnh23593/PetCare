package com.hit.comemyway.config;

import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class InternalSecurityConfig {
  // Controller validates service credentials; never route /internal through the gateway.
  @Bean
  @Order(1)
  SecurityFilterChain internal(HttpSecurity http) throws Exception {
    return http.securityMatcher("/internal/**").csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
  }
}
