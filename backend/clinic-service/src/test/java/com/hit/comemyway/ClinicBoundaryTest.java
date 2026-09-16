package com.hit.comemyway;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.hit.comemyway.entity.*;
import com.hit.comemyway.integration.*;
import com.hit.comemyway.repository.*;
import com.hit.comemyway.service.*;
import com.hit.comemyway.dto.request.CompleteClinicProfileRequest;
import java.time.LocalTime;
import java.util.*;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@ActiveProfiles("test")
class ClinicBoundaryTest {
  @Autowired
  org.springframework.test.web.servlet.MockMvc mvc;

  @Test
  void swaggerIncludesClinicRoutes() throws Exception {
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .get("/v3/api-docs/clinic"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
            .jsonPath("$.paths['/api/v1/user/appointments']").exists());
  }

  @Autowired
  ClinicService clinics;
  @Autowired
  ClinicRepository repository;
  @Autowired
  ClinicActivationRepository outbox;
  @Autowired
  EntityManagerFactory entities;
  @MockitoBean
  IdentityClient identity;
  @MockitoBean
  MapService maps;

  @BeforeEach
  void prepare() {
    var user = UserRef.builder().id(42L).username("clinic").role(Role.CLINIC)
        .status(AccountStatus.PENDING_PROFILE).build();
    when(identity.findByUsername("clinic")).thenReturn(Optional.of(user));
    when(maps.extractCoordinates(anyString()))
        .thenReturn(Map.of("latitude", 21.0, "longitude", 105.0));
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("clinic", null, List.of()));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  CompleteClinicProfileRequest profile(String service) {
    return new CompleteClinicProfileRequest("Clinic", "Address", "https://maps.google.com/test",
        "0912345678", "description", "image", LocalTime.of(8, 0), LocalTime.of(18, 0),
        List.of(service));
  }

  @Test
  void ownsOnlyClinicData() {
    assertThat(entities.getMetamodel().getEntities()).extracting(e -> e.getName())
        .containsExactlyInAnyOrder("Clinic", "Appointment", "Service", "ClinicActivation");
  }

  @Test
  void profileAndOutboxCommitTogetherAndRetrySurvivesIdentityFailure() {
    clinics.completeClinicProfile(profile("Consultation"));
    assertThat(repository.findByUserId(42L)).isPresent();
    assertThat(outbox.findById(42L)).isPresent();
    verify(identity, never()).activateClinic(anyLong());
    var publisher = new ClinicActivationPublisher(outbox, identity);
    doThrow(new RuntimeException("offline")).doNothing().when(identity).activateClinic(42L);
    publisher.publish();
    assertThat(outbox.findById(42L).orElseThrow().getAttempts()).isEqualTo(1);
    publisher.publish();
    assertThat(outbox.findById(42L)).isEmpty();
  }

  @Test
  void invalidProfileRollsBackOutboxAndClinic() {
    var other = UserRef.builder().id(99L).username("clinic").role(Role.CLINIC)
        .status(AccountStatus.PENDING_PROFILE).build();
    when(identity.findByUsername("clinic")).thenReturn(Optional.of(other));
    assertThatThrownBy(() -> clinics.completeClinicProfile(profile("x".repeat(256))))
        .isInstanceOf(RuntimeException.class);
    assertThat(repository.findByUserId(99L)).isEmpty();
    assertThat(outbox.findById(99L)).isEmpty();
  }
}
