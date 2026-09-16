package com.hit.comemyway;

import static org.assertj.core.api.Assertions.*;
import com.hit.comemyway.integration.IdentityClient;
import com.hit.comemyway.entity.Role;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class IdentityHttpContractTest {
  @Test
  void transfersCredentialsAndParsesIdentityProfileWithoutADatabase() throws Exception {
    var header = new AtomicReference<String>();
    var bearer = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/internal/identity/me", exchange -> {
      header.set(exchange.getRequestHeaders().getFirst("X-Service-Token"));
      bearer.set(exchange.getRequestHeaders().getFirst("Authorization"));
      byte[] body =
          "{\"id\":7,\"username\":\"alice\",\"role\":\"USER\",\"status\":\"ACTIVE\",\"avatar\":\"new-avatar\"}"
              .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      var client =
          new IdentityClient("http://127.0.0.1:" + server.getAddress().getPort(), "service-secret");
      var profile = client.authenticate("Bearer access");
      assertThat(profile.getId()).isEqualTo(7);
      assertThat(profile.getRole()).isEqualTo(Role.USER);
      assertThat(profile.getAvatar()).isEqualTo("new-avatar");
      assertThat(header.get()).isEqualTo("service-secret");
      assertThat(bearer.get()).isEqualTo("Bearer access");
      server.stop(0);
      assertThatThrownBy(() -> client.authenticate("Bearer access"))
          .isInstanceOf(ResponseStatusException.class)
          .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
              .isEqualTo(503));
    } finally {
      server.stop(0);
    }
  }
}
