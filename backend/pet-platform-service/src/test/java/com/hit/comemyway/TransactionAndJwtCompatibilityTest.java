package com.hit.comemyway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hit.comemyway.config.FirebaseConfig;
import com.hit.comemyway.dto.request.CompleteClinicProfileRequest;
import com.hit.comemyway.entity.AccountStatus;
import com.hit.comemyway.entity.Role;
import com.hit.comemyway.entity.User;
import com.hit.comemyway.repository.ClinicRepository;
import com.hit.comemyway.repository.UserRepository;
import com.hit.comemyway.security.JwtService;
import com.hit.comemyway.service.ClinicService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionAndJwtCompatibilityTest {
  @MockitoBean
  FirebaseConfig firebaseConfig;
  @Autowired
  UserRepository users;
  @Autowired
  ClinicRepository clinics;
  @Autowired
  ClinicService clinicService;
  @Autowired
  JwtService jwt;
  @Autowired
  JdbcTemplate jdbc;
  @Value("${jwt.secret}")
  String signingKey;
  @Autowired
  org.springframework.test.web.servlet.MockMvc mvc;
  @Autowired
  org.springframework.security.crypto.password.PasswordEncoder passwords;
  @MockitoBean(name = "redisTemplate")
  org.springframework.data.redis.core.RedisTemplate<String, String> redis;

  @Test
  @SuppressWarnings("unchecked")
  void completeProfileSuccessThenImmediateLoginObservesActiveInOldBackend() throws Exception {
    var values =
        org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
    org.mockito.Mockito.when(redis.opsForValue()).thenReturn(values);
    User user = clinicAccount();
    user.setPassword(passwords.encode("Password123!"));
    users.save(user);
    SecurityContextHolder.clearContext();
    String body = """
        {"name":"Clinic","address":"Address","mapLink":"!3d21.0!4d105.0","phone":"0912345678",
         "description":"test","thumbnailUrl":"image","openTime":"08:00",
         "closeTime":"18:00","services":["Consultation"]}
        """;
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .post("/api/v1/clinic/complete-profile")
        .header("Authorization", "Bearer " + jwt.generateToken(user, false))
        .contentType("application/json").content(body)).andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .post("/api/v1/auth/login").contentType("application/json")
        .content("{\"username\":\"" + user.getUsername() + "\",\"password\":\"Password123!\"}"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
            .jsonPath("$.data.accountStatus").value("ACTIVE"));
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void clinicActivationCascadeAndDirtyCheckingStillCommitTogether() {
    User user = clinicAccount();
    var created = clinicService.completeClinicProfile(profile("Original", List.of("Old service")));
    assertThat(users.findById(user.getId()).orElseThrow().getStatus())
        .isEqualTo(AccountStatus.ACTIVE);
    Long clinicId = clinics.findByUsername(user.getUsername()).orElseThrow().getId();
    assertThat(jdbc.queryForObject("select count(*) from services where clinic_id = ?", Long.class,
        clinicId)).isEqualTo(1L);
    clinicService.updateClinicProfile(profile("Updated", List.of("New service", "Second service")));
    assertThat(clinics.findById(clinicId).orElseThrow().getName()).isEqualTo("Updated");
    assertThat(jdbc.queryForList("select name from services where clinic_id = ? order by name",
        String.class, clinicId)).containsExactly("New service", "Second service");
  }

  @Test
  void aServiceInsertFailureRollsBackClinicAndAccountActivation() {
    User user = clinicAccount();
    // Fail during cascade persistence, after the original service sets the user ACTIVE.
    assertThatThrownBy(
        () -> clinicService.completeClinicProfile(profile("Rollback", List.of("x".repeat(256)))))
        .isInstanceOf(RuntimeException.class);
    assertThat(users.findById(user.getId()).orElseThrow().getStatus())
        .isEqualTo(AccountStatus.PENDING_PROFILE);
    assertThat(clinics.findByUsername(user.getUsername())).isEmpty();
  }

  @Test
  void jwtClaimsSignatureExpiryAndRevocationKeepTheOriginalContract() throws Exception {
    User user = clinicAccount();
    String access = jwt.generateToken(user, false);
    String refresh = jwt.generateToken(user, true);
    SignedJWT parsedAccess = SignedJWT.parse(access);
    var accessClaims = parsedAccess.getJWTClaimsSet();
    var refreshClaims = SignedJWT.parse(refresh).getJWTClaimsSet();
    assertThat(parsedAccess.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
    assertThat(parsedAccess.verify(new MACVerifier(signingKey.getBytes()))).isTrue();
    assertThat(accessClaims.getClaims().keySet()).containsExactlyInAnyOrder("sub", "iat", "exp",
        "jti", "isRefresh", "authorities", "userId");
    assertThat(accessClaims.getSubject()).isEqualTo(user.getUsername());
    assertThat(accessClaims.getStringClaim("authorities")).isEqualTo("CLINIC");
    assertThat(accessClaims.getLongClaim("userId")).isEqualTo(user.getId());
    assertThat(accessClaims.getExpirationTime().getTime() - accessClaims.getIssueTime().getTime())
        .isEqualTo(1800000L);
    assertThat(refreshClaims.getClaims().keySet()).containsExactlyInAnyOrder("sub", "iat", "exp",
        "jti", "isRefresh");
    assertThat(refreshClaims.getExpirationTime().getTime() - refreshClaims.getIssueTime().getTime())
        .isEqualTo(604800000L);
    var details = org.springframework.security.core.userdetails.User
        .withUsername(user.getUsername()).password("unused").roles("CLINIC").build();
    assertThat(jwt.isAccessToken(access)).isTrue();
    assertThat(jwt.isAccessToken(refresh)).isFalse();
    assertThat(jwt.isTokenValid(access, details)).isTrue();
    jwt.invalidateToken(access);
    assertThat(jwt.isTokenValid(access, details)).isFalse();
    assertThat(jwt.isTokenValid(refresh, details)).isTrue();
  }

  private User clinicAccount() {
    String username = "tx_" + UUID.randomUUID();
    User user = users.save(
        User.builder().username(username).email(username + "@example.invalid").password("unused")
            .homeAddress("test").role(Role.CLINIC).status(AccountStatus.PENDING_PROFILE).build());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(username, "unused", List.of()));
    return user;
  }

  private CompleteClinicProfileRequest profile(String name, List<String> services) {
    return new CompleteClinicProfileRequest(name, "Test address", "!3d21.0!4d105.0", "0912345678",
        "Test description", "http://image.invalid/clinic.jpg", LocalTime.of(8, 0),
        LocalTime.of(20, 0), services);
  }
}
