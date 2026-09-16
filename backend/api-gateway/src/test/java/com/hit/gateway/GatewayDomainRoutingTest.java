package com.hit.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayDomainRoutingTest {
  static HttpServer stub(String name) {
    try {
      var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", exchange -> {
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
      });
      server.start();
      return server;
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  static final HttpServer identity = stub("identity");
  static final HttpServer clinic = stub("clinic");
  static final HttpServer social = stub("social");
  @Value("${local.server.port}")
  int port;

  @DynamicPropertySource
  static void urls(DynamicPropertyRegistry props) {
    props.add("platform.service.url", () -> "http://127.0.0.1:" + identity.getAddress().getPort());
    props.add("clinic.service.url", () -> "http://127.0.0.1:" + clinic.getAddress().getPort());
    props.add("social.service.url", () -> "http://127.0.0.1:" + social.getAddress().getPort());
  }

  @AfterAll
  static void stop() {
    identity.stop(0);
    clinic.stop(0);
    social.stop(0);
  }

  @ParameterizedTest
  @CsvSource({"/api/v1/auth/login,identity", "/api/v1/clinic/change-password,identity",
      "/api/v1/user/locket-link,identity", "/api/v1/media/upload,identity",
      "/api/v1/user/appointments,clinic", "/api/v1/clinic/appointments/1/confirm,clinic",
      "/api/v1/public/clinics/search,clinic", "/api/v1/public/suggestions/location,clinic",
      "/api/v1/chat,clinic", "/api/v1/user/locket/feed,social", "/v3/api-docs/clinic,clinic",
      "/v3/api-docs/social,social"})
  void sendsRequestToTheOwningProcess(String path, String owner) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
    var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo(owner);
  }
}
