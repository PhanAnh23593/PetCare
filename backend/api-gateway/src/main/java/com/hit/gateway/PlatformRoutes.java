package com.hit.gateway;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.preserveHostHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class PlatformRoutes {
  @Bean
  RouterFunction<ServerResponse> platform(@Value("${platform.service.url}") String identityUrl,
      @Value("${clinic.service.url}") String clinicUrl,
      @Value("${social.service.url}") String socialUrl) {
    return route("domain-api").route(request -> true, http())
        .before(request -> uri(switch (owner(request.path())) {
          case "clinic" -> clinicUrl;
          case "social" -> socialUrl;
          default -> identityUrl;
        }).apply(request))
        .before(preserveHostHeader()).before(request -> {
          if (request.headers().firstHeader("X-Forwarded-For") == null) {
            return ServerRequest.from(request)
                .header("X-Forwarded-For", request.servletRequest().getRemoteAddr()).build();
          }
          return request;
        }).filter((request, next) -> {
          String path = request.path();
          if (path.equals("/internal") || path.startsWith("/internal/")) {
            return ServerResponse.notFound().build();
          }
          return next.handle(request);
        }).build();
  }

  static String owner(String path) {
    if (path.equals("/api/v1/clinic/change-password"))
      return "identity";
    if (path.startsWith("/api/v1/clinic/") || path.equals("/api/v1/user/appointments")
        || path.startsWith("/api/v1/user/appointments/")
        || path.startsWith("/api/v1/public/clinics/")
        || path.equals("/api/v1/public/suggestions/location") || path.equals("/api/v1/chat")
        || path.equals("/v3/api-docs/clinic"))
      return "clinic";
    if (path.startsWith("/api/v1/user/locket/") || path.equals("/v3/api-docs/social"))
      return "social";
    return "identity";
  }
}
