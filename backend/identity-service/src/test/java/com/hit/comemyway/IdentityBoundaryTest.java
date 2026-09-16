package com.hit.comemyway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.hit.comemyway.entity.*;
import com.hit.comemyway.repository.UserRepository;
import com.hit.comemyway.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.persistence.EntityManagerFactory;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityBoundaryTest {
  @org.springframework.test.context.bean.override.mockito.MockitoBean(name = "redisTemplate")
  org.springframework.data.redis.core.RedisTemplate<String, String> redis;
  @Autowired
  com.hit.comemyway.service.AuthService auth;
  @Autowired
  com.fasterxml.jackson.databind.ObjectMapper json;
  @Autowired
  org.springframework.security.crypto.password.PasswordEncoder passwords;

  @Test
  @SuppressWarnings("unchecked")
  void verifiedRegistrationCreatesAnAccountInTheNewDatabaseAndCanLogin() throws Exception {
    var values =
        org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
    org.mockito.Mockito.when(redis.opsForValue()).thenReturn(values);
    var request = new com.hit.comemyway.dto.request.RegisterRequest("newuser", "Password123!",
        "Password123!", "newuser@example.invalid");
    org.mockito.Mockito.when(values.get("REGISTRATION_OTP:" + request.email()))
        .thenReturn("123456");
    org.mockito.Mockito.when(values.get("REGISTRATION_DATA:" + request.email()))
        .thenReturn(json.writeValueAsString(request));
    auth.verifyRegister(
        new com.hit.comemyway.dto.request.VerifyOtpRequest(request.email(), "123456"));
    var created = users.findByUsername("newuser").orElseThrow();
    assertThat(created.getHomeAddress()).isEmpty();
    assertThat(passwords.matches(request.password(), created.getPassword())).isTrue();
    var login =
        auth.login(new com.hit.comemyway.dto.request.LoginRequest("newuser", request.password()));
    mvc.perform(get("/internal/identity/me").header("X-Service-Token", "test-social-token")
        .header("Authorization", "Bearer " + login.accessToken())).andExpect(status().isOk());
  }

  @Test
  void swaggerIncludesIdentityRoutesOnly() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/user/appointments']").doesNotExist())
        .andExpect(jsonPath("$.paths['/internal/identity/me']").doesNotExist());
  }

  @Autowired
  MockMvc mvc;
  @Autowired
  UserRepository users;
  @Autowired
  JwtService jwt;
  @Autowired
  EntityManagerFactory entities;

  User user(Role role, AccountStatus status) {
    String name = "account" + java.util.UUID.randomUUID();
    return users.save(User.builder().username(name).email(name + "@example.invalid")
        .password("hash").homeAddress("").role(role).status(status).build());
  }

  @Test
  void ownsOnlyAccountsAndRevocations() {
    assertThat(entities.getMetamodel().getEntities()).extracting(e -> e.getName())
        .containsExactlyInAnyOrder("User", "InvalidatedToken");
  }

  @Test
  void introspectionRejectsMissingCredentialsRefreshAndRevokedAccess() throws Exception {
    var user = user(Role.USER, AccountStatus.ACTIVE);
    String access = jwt.generateToken(user, false);
    mvc.perform(get("/internal/identity/me").header("Authorization", "Bearer " + access))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/internal/identity/me").header("X-Service-Token", "test-social-token")
        .header("Authorization", "Bearer " + access)).andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(user.getId()))
        .andExpect(jsonPath("$.password").doesNotExist());
    mvc.perform(get("/internal/identity/me").header("X-Service-Token", "test-social-token")
        .header("Authorization", "Bearer " + jwt.generateToken(user, true)))
        .andExpect(status().isUnauthorized());
    jwt.invalidateToken(access);
    mvc.perform(get("/internal/identity/me").header("X-Service-Token", "test-social-token")
        .header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
  }

  @Test
  void onlyClinicCanActivateAndDeliveryIsIdempotent() throws Exception {
    var user = user(Role.CLINIC, AccountStatus.PENDING_PROFILE);
    String path = "/internal/identity/clinics/" + user.getId() + "/activate";
    mvc.perform(post(path).header("X-Service-Token", "test-social-token"))
        .andExpect(status().isForbidden());
    for (int i = 0; i < 2; i++)
      mvc.perform(post(path).header("X-Service-Token", "test-clinic-token"))
          .andExpect(status().isOk());
    assertThat(users.findById(user.getId()).orElseThrow().getStatus())
        .isEqualTo(AccountStatus.ACTIVE);
  }
}
