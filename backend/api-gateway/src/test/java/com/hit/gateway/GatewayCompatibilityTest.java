package com.hit.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayCompatibilityTest {
  static final LinkedBlockingQueue<Received> received = new LinkedBlockingQueue<>();
  static volatile Reply reply =
      new Reply(200, "application/json", "{}".getBytes(StandardCharsets.UTF_8), Map.of());
  static final HttpServer platform = platform();
  static final HttpClient client =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
  static final Path fixtures = Path.of("../pet-platform-service/src/test/resources/compatibility");
  @Value("${local.server.port}")
  int port;
  @Autowired
  ObjectMapper json;

  record Received(String method, String uri, Map<String, List<String>> headers, byte[] bytes) {
    String header(String name) {
      return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
          .map(e -> e.getValue().getFirst()).findFirst().orElse(null);
    }
  }

  record Reply(int status, String type, byte[] bytes, Map<String, String> headers) {}

  static HttpServer platform() {
    try {
      var stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      stub.createContext("/", exchange -> {
        received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
            new HashMap<>(exchange.getRequestHeaders()), exchange.getRequestBody().readAllBytes()));
        Reply current = reply;
        exchange.getResponseHeaders().set("Content-Type", current.type());
        current.headers().forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
        exchange.sendResponseHeaders(current.status(), current.bytes().length);
        exchange.getResponseBody().write(current.bytes());
        exchange.close();
      });
      stub.start();
      return stub;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @DynamicPropertySource
  static void upstream(DynamicPropertyRegistry registry) {
    registry.add("clinic.service.url", () -> "http://127.0.0.1:" + platform.getAddress().getPort());
    registry.add("social.service.url", () -> "http://127.0.0.1:" + platform.getAddress().getPort());
    registry.add("platform.service.url",
        () -> "http://127.0.0.1:" + platform.getAddress().getPort());
  }

  @AfterAll
  static void stop() {
    platform.stop(0);
  }

  @TestFactory
  Stream<DynamicTest> forwardsAll48OriginalRequestsAndResponses() throws Exception {
    List<JsonNode> cases = new ArrayList<>();
    json.readTree(Files.readString(fixtures.resolve("requests.json"))).forEach(cases::add);
    assertThat(cases).hasSize(48);
    return cases.stream().map(c -> DynamicTest
        .dynamicTest(c.path("method").asText() + " " + c.path("path").asText(), () -> {
          received.clear();
          JsonNode snapshot = json.readTree(
              Files.readString(fixtures.resolve("responses/" + c.path("id").asText() + ".json")));
          Map<String, String> headers = new HashMap<>();
          snapshot.path("headers").properties().forEach(e -> {
            if (!e.getValue().isNull())
              headers.put(e.getKey(), e.getValue().asText());
          });
          byte[] response = json.writeValueAsBytes(snapshot.path("body"));
          reply = new Reply(snapshot.path("httpStatus").asInt(),
              snapshot.path("contentType").asText(), response, headers);
          byte[] body = c.has("body") ? json.writeValueAsBytes(c.get("body")) : new byte[0];
          String contentType = "application/json";
          if (c.path("multipart").asBoolean()) {
            body = multipart();
            contentType = "multipart/form-data; boundary=compatibility-boundary";
          }
          var request = HttpRequest
              .newBuilder(URI.create("http://127.0.0.1:" + port + c.path("path").asText()))
              .header("Authorization", "Bearer unchanged-token")
              .header("X-Forwarded-For", "198.51.100.5, 203.0.113.4")
              .header("Content-Type", contentType)
              .method(c.path("method").asText(), HttpRequest.BodyPublishers.ofByteArray(body))
              .build();
          var actual = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
          assertThat(actual.statusCode()).isEqualTo(reply.status());
          assertThat(actual.body()).isEqualTo(response);
          assertThat(actual.headers().firstValue("Content-Type").orElseThrow())
              .isEqualTo(reply.type());
          headers
              .forEach((name, value) -> assertThat(actual.headers().firstValue(name).orElseThrow())
                  .isEqualTo(value));
          Received upstream = received.poll(2, TimeUnit.SECONDS);
          assertThat(upstream).isNotNull();
          assertThat(upstream.method()).isEqualTo(c.path("method").asText());
          assertThat(upstream.uri()).isEqualTo(c.path("path").asText());
          assertThat(upstream.bytes()).isEqualTo(body);
          assertThat(upstream.header("Content-Type")).isEqualTo(contentType);
          assertThat(upstream.header("Authorization")).isEqualTo("Bearer unchanged-token");
          assertThat(upstream.header("X-Forwarded-For")).isEqualTo("198.51.100.5, 203.0.113.4");
          assertThat(upstream.header("Host")).isEqualTo("127.0.0.1:" + port);
        }));
  }

  @Test
  void retainsErrorBodiesStatusesAndEncodedQueryParameters() throws Exception {
    for (int status : new int[] {400, 401, 403, 404, 422, 429, 500}) {
      received.clear();
      byte[] body = ("{\"statusCode\":" + status + ",\"message\":\"Original error\",\"data\":null}")
          .getBytes(StandardCharsets.UTF_8);
      reply = new Reply(status, "application/json; charset=UTF-8", body,
          Map.of("X-Frame-Options", "DENY"));
      String path = "/api/v1/user/clinics/search?keyword=pet%20care&keyword=a%2Bb&limit=10";
      var actual = client.send(
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
          HttpResponse.BodyHandlers.ofByteArray());
      assertThat(actual.statusCode()).isEqualTo(status);
      assertThat(actual.body()).isEqualTo(body);
      Received upstream = received.poll(2, TimeUnit.SECONDS);
      assertThat(upstream.uri()).isEqualTo(path);
      assertThat(upstream.header("X-Forwarded-For")).isEqualTo("127.0.0.1");
      assertThat(upstream.header("Authorization")).isNull();
    }
  }

  @Test
  void internalEndpointsCannotBeReachedThroughGateway() throws Exception {
    received.clear();
    for (String path : List.of("/internal", "/internal/media/upload", "/internal/health")) {
      var result = client.send(
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
          HttpResponse.BodyHandlers.ofByteArray());
      assertThat(result.statusCode()).isEqualTo(404);
    }
    assertThat(received).isEmpty();
  }

  private static byte[] multipart() throws IOException {
    var out = new ByteArrayOutputStream();
    out.write(
        ("--compatibility-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"pet.png\"\r\n"
            + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    out.write(new byte[] {0, 1, -1, -128, 13, 10});
    out.write("\r\n--compatibility-boundary--\r\n".getBytes(StandardCharsets.UTF_8));
    return out.toByteArray();
  }
}
