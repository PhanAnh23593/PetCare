package com.hit.comemyway;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.hit.comemyway.entity.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthErrorCompatibilityTest {
  @Autowired
  MockMvc mvc;

  @MockitoBean
  com.hit.comemyway.integration.IdentityClient identity;
  String expired;
  String wrongRole = "valid-wrong-role";

  @BeforeEach
  void prepare() throws Exception {
    expired = expiredToken("test-signing-secret-at-least-thirty-two-bytes-long");
    when(identity.authenticate("Bearer " + expired)).thenReturn(null);
    when(identity.authenticate("Bearer not-a-jwt")).thenReturn(null);
    when(identity.authenticate("Bearer " + wrongRole))
        .thenReturn(com.hit.comemyway.integration.UserRef.builder().id(800L).username("wrong")
            .role(Role.CLINIC).build());
  }

  boolean accepts(String service) {
    return service.equals("social");
  }

  @Test
  void validUserCanStillReadFeed() throws Exception {
    var user = com.hit.comemyway.integration.UserRef.builder().id(801L).username("reader")
        .role(Role.USER).build();
    when(identity.authenticate("Bearer valid-user")).thenReturn(user);
    when(identity.findByUsername("reader")).thenReturn(Optional.of(user));
    mvc.perform(get("/api/v1/user/locket/feed").header("Authorization", "Bearer valid-user"))
        .andExpect(status().isOk());
  }

  @Test
  void identityOutageRemains503() throws Exception {
    when(identity.authenticate("Bearer outage"))
        .thenThrow(new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
    mvc.perform(get("/api/v1/user/locket/feed").header("Authorization", "Bearer outage"))
        .andExpect(status().isServiceUnavailable());
  }

  @TestFactory
  java.util.stream.Stream<DynamicTest> oldBackendAuthenticationContract() throws Exception {
    return Files.readAllLines(Path.of("../compatibility/auth-errors.csv")).stream().skip(1)
        .filter(line -> accepts(line.split(",")[0]))
        .map(line -> DynamicTest.dynamicTest(line, () -> {
          String[] row = line.split(",");
          var request = request(org.springframework.http.HttpMethod.valueOf(row[1]), row[2]);
          String bearer = switch (row[3]) {
            case "missing" -> null;
            case "invalid" -> "not-a-jwt";
            case "expired" -> expired;
            case "wrong_role" -> wrongRole;
            default -> throw new IllegalArgumentException(row[3]);
          };
          if (bearer != null) request.header("Authorization", "Bearer " + bearer);
          mvc.perform(request).andExpect(status().is(Integer.parseInt(row[4])));
        }));
  }

  static String expiredToken(String secret) throws Exception {
    var encoder = Base64.getUrlEncoder().withoutPadding();
    String header = encoder.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
    String payload = encoder.encodeToString(
        "{\"sub\":\"compat_expired\",\"iat\":1,\"exp\":2,\"jti\":\"expired-access\",\"isRefresh\":false,\"authorities\":\"CLINIC\"}"
            .getBytes(StandardCharsets.UTF_8));
    var mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return header + "." + payload + "." + encoder
        .encodeToString(mac.doFinal((header + "." + payload).getBytes(StandardCharsets.UTF_8)));
  }
}
