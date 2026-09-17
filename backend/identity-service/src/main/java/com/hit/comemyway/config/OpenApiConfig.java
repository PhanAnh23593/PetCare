package com.hit.comemyway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI customOpenAPI(
      @Value("${PUBLIC_BASE_URL:https://petcare23593.duckdns.org}") String publicBaseUrl) {

    return new OpenAPI().servers(List.of(new Server().url(publicBaseUrl).description("Public API")))
        .info(new Info().title("Come My Way API Documentation").version("1.0.0")
            .description("Tài liệu hệ thống API tinh gọn cho dự án Come My Way"))
        .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
        .components(new Components().addSecuritySchemes("BearerAuth",
            new SecurityScheme().name("BearerAuth").type(SecurityScheme.Type.HTTP).scheme("bearer")
                .bearerFormat("JWT")));
  }
}
