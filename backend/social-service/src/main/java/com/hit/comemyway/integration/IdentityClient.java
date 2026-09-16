package com.hit.comemyway.integration;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** HTTP contract only; this service has no access to the identity database. */
@Component
public class IdentityClient {
  private final RestClient http;

  public IdentityClient(@Value("${identity.service.url}") String url,
      @Value("${identity.internal.token}") String token) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(3000);
    factory.setReadTimeout(5000);
    http = RestClient.builder().baseUrl(url).requestFactory(factory)
        .defaultHeader("X-Service-Token", token).build();
  }

  public UserRef authenticate(String bearer) {
    try {
      return http.get().uri("/internal/identity/me").header("Authorization", bearer).retrieve()
          .body(UserRef.class);
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403)
        return null;
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identity unavailable");
    } catch (RestClientException ex) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identity unavailable");
    }
  }

  public Optional<UserRef> findById(Long id) {
    return lookup("id", id.toString());
  }

  public Optional<UserRef> findByUsername(String name) {
    return lookup("username", name);
  }

  public Optional<UserRef> findBylocketCode(String code) {
    return lookup("code", code);
  }

  private Optional<UserRef> lookup(String key, String value) {
    try {
      return Optional.ofNullable(http.get()
          .uri(b -> b.path("/internal/identity/users").queryParam(key, "{value}").build(value))
          .retrieve().body(UserRef.class));
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 404)
        return Optional.empty();
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identity unavailable");
    } catch (RestClientException ex) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identity unavailable");
    }
  }

  public void activateClinic(Long id) {
    http.post().uri("/internal/identity/clinics/{id}/activate", id).retrieve().toBodilessEntity();
  }
}
