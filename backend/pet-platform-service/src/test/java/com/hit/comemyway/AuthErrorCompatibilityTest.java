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
  com.hit.comemyway.config.FirebaseConfig firebase;
  @Autowired
  com.hit.comemyway.repository.UserRepository users;
  @Autowired
  com.hit.comemyway.security.JwtService jwt;
  @org.springframework.beans.factory.annotation.Value("${jwt.secret}")
  String secret;
  String expired;
  String wrongRole;

  @BeforeEach
  void prepare() throws Exception {
    expired = expiredToken(secret);
    for (String name : List.of("compat_expired", "compat_wrong")) {
      if (users.findByUsername(name).isEmpty()) {
        users.save(User.builder().username(name).email(name + "@example.invalid").password("unused")
            .homeAddress("").role(Role.ADMIN).status(AccountStatus.ACTIVE).build());
      }
    }
    wrongRole = jwt.generateToken(users.findByUsername("compat_wrong").orElseThrow(), false);
  }

  boolean accepts(String service) {
    return true;
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
