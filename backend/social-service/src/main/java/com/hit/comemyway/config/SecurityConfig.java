package com.hit.comemyway.config;

import com.hit.comemyway.integration.IdentityClient;
import com.hit.comemyway.security.RemoteAuthenticationFilter;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
  @Bean
  SecurityFilterChain security(HttpSecurity http, IdentityClient identity) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a.requestMatchers("/internal/**").denyAll()
            .requestMatchers("/api/v1/public/**", "/v3/api-docs/**", "/swagger-ui/**",
                "/swagger-ui.html")
            .permitAll().requestMatchers("/api/v1/user/**").hasAuthority("USER")
            .requestMatchers("/api/v1/clinic/**").hasAuthority("CLINIC")
            .requestMatchers("/api/v1/admin/**").hasAuthority("ADMIN").anyRequest().authenticated())
        .exceptionHandling(e -> e.authenticationEntryPoint((q, r, x) -> r.sendError(401)))
        .addFilterBefore(new RemoteAuthenticationFilter(identity),
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
