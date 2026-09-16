package com.hit.comemyway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hit.comemyway.config.FirebaseConfig;
import com.hit.comemyway.service.ImageUploadService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class MediaTransportTest {
  static final HttpServer server = server();
  static final LinkedBlockingQueue<Upload> received = new LinkedBlockingQueue<>();
  static final AtomicInteger calls = new AtomicInteger();
  static volatile int status = 200;
  static volatile String responseBody = "http://image.invalid/original.jpg";

  record Upload(String method, String path, String type, String token, byte[] bytes) {}

  @MockitoBean
  FirebaseConfig firebaseConfig;
  @Autowired
  ImageUploadService images;

  static HttpServer server() {
    try {
      var stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      stub.createContext("/", exchange -> {
        calls.incrementAndGet();
        received.add(new Upload(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
            exchange.getRequestHeaders().getFirst("Content-Type"),
            exchange.getRequestHeaders().getFirst("X-Media-Token"),
            exchange.getRequestBody().readAllBytes()));
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type",
            status == 200 ? "text/plain" : "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
      });
      stub.start();
      return stub;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("media.service.url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
  }

  @BeforeEach
  void reset() {
    calls.set(0);
    received.clear();
    status = 200;
    responseBody = "http://image.invalid/original.jpg";
  }

  @AfterAll
  static void stop() {
    server.stop(0);
  }

  @Test
  void feignSendsExactBytesAndInternalCredential() throws Exception {
    byte[] bytes = {0, 1, -1, 13, 10};
    String url = images.uploadImage(new MockMultipartFile("file", "pet.png", "image/png", bytes));
    assertThat(url).isEqualTo(responseBody);
    Upload request = received.poll(2, TimeUnit.SECONDS);
    assertThat(request).isNotNull();
    assertThat(request.method()).isEqualTo("POST");
    assertThat(request.path()).isEqualTo("/internal/media/upload");
    assertThat(request.type()).startsWith("application/octet-stream");
    assertThat(request.token()).isEqualTo("local-internal-test-token");
    assertThat(request.bytes()).isEqualTo(bytes);
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void originalIllegalArgumentMessageSurvivesTheTransport() {
    status = 400;
    responseBody = "{\"kind\":\"INVALID_ARGUMENT\",\"message\":\"Invalid image\"}";
    assertThatThrownBy(() -> images.uploadImage(new MockMultipartFile("file", new byte[] {1})))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid image");
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void unavailableMediaIsNotRetried() {
    status = 503;
    responseBody = "{}";
    assertThatThrownBy(() -> images.uploadImage(new MockMultipartFile("file", new byte[] {1})))
        .isInstanceOf(IOException.class);
    assertThat(calls.get()).isEqualTo(1);
  }
}
