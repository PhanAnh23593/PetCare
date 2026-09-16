package com.hit.comemyway.config;

import org.springframework.context.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class JsonConfig {
  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }
}
