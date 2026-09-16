package com.hit.comemyway.service;

import com.hit.comemyway.integration.IdentityClient;
import com.hit.comemyway.exception.extended.AppException;
import com.hit.comemyway.repository.ClinicActivationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClinicActivationPublisher {
  private final ClinicActivationRepository events;
  private final IdentityClient identity;

  public void activate(Long userId) {
    try {
      identity.activateClinic(userId);
    } catch (Exception ex) {
      throw new AppException(503, "Identity unavailable");
    }
    try {
      events.deleteById(userId);
    } catch (Exception ex) {
      log.warn("Clinic activation confirmed for user {}, outbox cleanup pending", userId, ex);
    }
  }

  @Scheduled(fixedDelayString = "${clinic.activation.delay-ms:5000}")
  public void publish() {
    for (var event : events.findAllByOrderByAttemptsAsc(PageRequest.of(0, 50))) {
      try {
        identity.activateClinic(event.getUserId());
        events.deleteById(event.getUserId());
      } catch (Exception ex) {
        event.setAttempts(event.getAttempts() + 1);
        events.save(event);
        log.warn("Clinic activation pending for user {}, attempt {}", event.getUserId(),
            event.getAttempts());
      }
    }
  }
}
