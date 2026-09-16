package com.hit.comemyway.integration.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

// Registered only in this Feign client's child context.
public class MediaFeignConfiguration {
  @Bean
  RequestInterceptor mediaToken(@Value("${media.internal.token}") String token) {
    if (token.isBlank()) {
      throw new IllegalArgumentException("MEDIA_INTERNAL_TOKEN must be configured");
    }
    return request -> request.header("X-Media-Token", token);
  }

  @Bean
  Retryer noUploadRetries() {
    return Retryer.NEVER_RETRY;
  }

  @Bean
  ErrorDecoder originalUploadErrors() {
    return (method, response) -> {
      if (response.status() == 400 && response.body() != null) {
        try (var input = response.body().asInputStream()) {
          var failure = new ObjectMapper().readTree(input);
          if ("INVALID_ARGUMENT".equals(failure.path("kind").asText())) {
            return new IllegalArgumentException(
                failure.path("message").isNull() ? null : failure.path("message").asText());
          }
        } catch (IOException ignored) {
          // An invalid internal response follows the existing generic error path.
        }
      }
      return new IOException("Media upload unavailable");
    };
  }
}
