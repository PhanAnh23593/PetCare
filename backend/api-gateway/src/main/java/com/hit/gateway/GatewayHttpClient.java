package com.hit.gateway;

import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class GatewayHttpClient {
  @Bean
  ClientHttpRequestFactory gatewayRequestFactory() {
    // Host preservation is required for existing redirects and API documentation URLs.
    // Never replay a mutation or consume the platform's redirect on behalf of the client.
    return new HttpComponentsClientHttpRequestFactory(
        HttpClients.custom().disableAutomaticRetries().disableRedirectHandling()
            .disableCookieManagement().addRequestInterceptorLast((request, entity, context) -> {
              if (RequestContextHolder
                  .getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                var original = attributes.getRequest();
                String query = original.getQueryString();
                request.setPath(original.getRequestURI() + (query == null ? "" : "?" + query));
                String contentType = original.getHeader("Content-Type");
                if (contentType != null) {
                  request.setHeader("Content-Type", contentType);
                } else {
                  request.removeHeaders("Content-Type");
                }
                // The original limiter trusts the full header, including an empty value.
                String forwarded = original.getHeader("X-Forwarded-For");
                request.setHeader("X-Forwarded-For",
                    forwarded == null ? original.getRemoteAddr() : forwarded);
              }
            }).build());
  }
}
