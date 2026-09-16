package com.hit.comemyway.entity;

import jakarta.persistence.*;
import lombok.*;

/** Durable outbox entry committed in the same transaction as the clinic profile. */
@Entity
@Table(name = "clinic_activation_outbox")
@Getter
@Setter
@NoArgsConstructor
public class ClinicActivation {
  @Id
  private Long userId;
  private int attempts;

  public ClinicActivation(Long userId) {
    this.userId = userId;
  }
}
